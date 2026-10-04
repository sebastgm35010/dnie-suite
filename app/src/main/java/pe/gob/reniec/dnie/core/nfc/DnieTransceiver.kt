package pe.gob.reniec.dnie.core.nfc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.apdu.StatusWord
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult
import java.io.ByteArrayOutputStream

/**
 * Transceptor APDU principal para tarjetas DNIe del Perú (v2 y v3).
 *
 * Implementa la especificación ISO/IEC 7816-4 para:
 * 1. Selección del Applet PKI de RENIEC.
 * 2. Extracción secuencial de certificados X.509 en bloques de 228 bytes con parsing TLV y recorte DER.
 * 3. Verificación de PIN en hardware con decodificación de reintentos y detección de bloqueo.
 * 4. Cómputo de firmas digitales RSA-2048 con SHA-256 en tarjeta (MSE + PSO CDS).
 *
 * Todo el tráfico de comando y respuesta es emitido reactivamente a través de [ApduLogger].
 */
class DnieTransceiver(
    private val channel: DnieCardChannel,
    private val logger: ApduLogger = ApduLogger.default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DnieTransceiverInterface {

    /**
     * Envía un comando APDU por el canal físico, midiendo el tiempo de respuesta y registrando en el logger.
     */
    private fun transceiveWithLogging(command: CommandApdu, description: String): ResponseApdu {
        logger.logTx(command, description)
        val startTime = System.currentTimeMillis()
        val rawResponse = try {
            channel.transceive(command.toByteArray())
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.logInfo("Error de transporte en $description (${duration}ms): ${e.message}")
            throw e
        }
        val duration = System.currentTimeMillis() - startTime
        val response = ResponseApdu.fromBytes(rawResponse)
        logger.logRx(response, duration, description)
        return response
    }

    override suspend fun selectPkiApplet(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val cmd = CommandApdu.selectReniecPkiApplet()
            val resp = transceiveWithLogging(cmd, "SELECT PKI Applet (RENIEC)")

            when {
                resp.isSuccess -> Unit
                resp.sw == StatusWord.SW_FILE_NOT_FOUND -> {
                    throw AppletNotFoundException(
                        "Applet PKI de RENIEC no encontrado. Verifique que la tarjeta sea un DNIe peruano válido.",
                        resp.sw
                    )
                }
                else -> {
                    throw CardProtocolException(
                        "Fallo al seleccionar Applet PKI: ${resp.statusWord.description}",
                        resp.sw
                    )
                }
            }
        }
    }

    override suspend fun readCertificate(type: CertificateType): Result<ByteArray> = withContext(ioDispatcher) {
        runCatching {
            // Paso 1: Seleccionar el Archivo Elemental (EF) correspondiente
            val selectCmd = CommandApdu.selectEf(type.fileId)
            val selectResp = transceiveWithLogging(
                selectCmd,
                "SELECT EF ${type.displayName} (0x${type.fileIdHex})"
            )

            if (!selectResp.isSuccess) {
                if (selectResp.sw == StatusWord.SW_FILE_NOT_FOUND && !type.isMandatory) {
                    throw CertificateReadException(
                        "Certificado opcional ${type.displayName} no está personalizado en este chip",
                        selectResp.sw
                    )
                }
                throw CertificateReadException(
                    "Error al seleccionar EF para ${type.displayName}: ${selectResp.statusWord.description}",
                    selectResp.sw
                )
            }

            // Paso 2: Lectura secuencial de bloques con Data Object de Offset (Tag 0x54)
            val certAccumulator = ByteArrayOutputStream()
            var currentOffset = 0
            var expectedDerLength: Int? = null
            val stepSize = ApduConstants.DEFAULT_BLOCK_SIZE // 0x00E4 (228 bytes)

            while (true) {
                val readCmd = CommandApdu.readBinaryWithOffset(currentOffset, le = 0xFF)
                val readResp = transceiveWithLogging(
                    readCmd,
                    "READ BINARY ${type.displayName} [Offset 0x%04X (%d)]".format(currentOffset, currentOffset)
                )

                // Condición 1: Offset fuera de límites del EF (6B 00) -> Fin de archivo
                if (readResp.sw == StatusWord.SW_WRONG_OFFSET) {
                    break
                }

                // Condición 2: Fin de archivo alcanzado (62 82)
                if (readResp.sw == StatusWord.SW_EOF_REACHED) {
                    val remainingPayload = extractPayloadFromTlv(readResp.data)
                    if (remainingPayload.isNotEmpty()) {
                        certAccumulator.write(remainingPayload)
                    }
                    break
                }

                // Si ocurrió cualquier otro error
                if (!readResp.isSuccess) {
                    throw CertificateReadException(
                        "Fallo de lectura en offset 0x%04X: %s".format(currentOffset, readResp.statusWord.description),
                        readResp.sw
                    )
                }

                // Extraer carga útil del contenedor TLV (Tag 0x53)
                val blockPayload = extractPayloadFromTlv(readResp.data)
                if (blockPayload.isEmpty()) {
                    break
                }

                certAccumulator.write(blockPayload)

                // En el bloque 0, detectar la longitud exacta ASN.1 DER del certificado
                if (currentOffset == 0 && expectedDerLength == null) {
                    val accumulated = certAccumulator.toByteArray()
                    expectedDerLength = parseDerSequenceLength(accumulated)
                }

                // Verificar si ya se leyó la totalidad de bytes del certificado DER
                if (expectedDerLength != null && certAccumulator.size() >= expectedDerLength) {
                    break
                }

                currentOffset += stepSize
            }

            val rawAccumulated = certAccumulator.toByteArray()
            if (rawAccumulated.isEmpty()) {
                throw CertificateReadException("El archivo EF de ${type.displayName} está vacío")
            }

            // Recorte preventivo de relleno (padding) de la tarjeta según la cabecera DER
            val cleanCertBytes = if (expectedDerLength != null && rawAccumulated.size > expectedDerLength) {
                rawAccumulated.copyOfRange(0, expectedDerLength)
            } else {
                rawAccumulated
            }

            cleanCertBytes
        }
    }

    /**
     * Lee todos los certificados disponibles en el DNIe (Firma, Autenticación y opcionalmente Cifrado).
     */
    suspend fun readAllCertificates(): Result<Map<CertificateType, ByteArray>> = withContext(ioDispatcher) {
        runCatching {
            val results = mutableMapOf<CertificateType, ByteArray>()
            for (type in CertificateType.entries) {
                val certResult = readCertificate(type)
                certResult.onSuccess { bytes ->
                    results[type] = bytes
                }.onFailure { ex ->
                    if (type.isMandatory) {
                        throw ex
                    }
                    // Si el certificado de cifrado no está presente, se omite limpiamente
                }
            }
            results
        }
    }

    override suspend fun verifyPin(pin: String): Result<PinVerificationResult> =
        verifyPin(pin, ApduConstants.PIN_TYPE_SIGNATURE)

    /**
     * Verifica el PIN para el tipo de clave especificado (Firma 0x81 o Autenticación 0x83).
     */
    suspend fun verifyPin(
        pin: String,
        pinType: Byte = ApduConstants.PIN_TYPE_SIGNATURE
    ): Result<PinVerificationResult> = withContext(ioDispatcher) {
        runCatching {
            // Validación sintáctica previa antes de impactar el chip físico
            if (!pin.all { it.isDigit() } || pin.length !in 4..8) {
                throw IllegalArgumentException("El PIN debe tener entre 4 y 8 dígitos numéricos")
            }

            val typeDesc = if (pinType == ApduConstants.PIN_TYPE_SIGNATURE) "Firma" else "Autenticación"
            val cmd = CommandApdu.verifyPin(pin, pinType)
            val resp = transceiveWithLogging(cmd, "VERIFY PIN $typeDesc (0x%02X)".format(pinType))

            when {
                resp.isSuccess -> {
                    PinVerificationResult.Success
                }
                (resp.sw and 0xFFF0) == StatusWord.SW_PIN_RETRY_BASE -> {
                    val remainingAttempts = resp.sw and 0x0F
                    if (remainingAttempts == 0) {
                        PinVerificationResult.CardBlocked
                    } else {
                        PinVerificationResult.InvalidPin(remainingAttempts)
                    }
                }
                resp.sw == StatusWord.SW_AUTHENTICATION_BLOCKED -> {
                    PinVerificationResult.CardBlocked
                }
                resp.sw == StatusWord.SW_SECURITY_STATUS_NOT_SATISFIED -> {
                    throw PinVerificationException(
                        message = "Estado de seguridad no satisfecho al verificar PIN",
                        statusWord = resp.sw
                    )
                }
                else -> {
                    throw PinVerificationException(
                        message = "Fallo en verificación de PIN: ${resp.statusWord.description}",
                        statusWord = resp.sw
                    )
                }
            }
        }
    }

    override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> = withContext(ioDispatcher) {
        runCatching {
            require(hash32Bytes.size == ApduConstants.SHA256_HASH_SIZE) {
                "El hash SHA-256 debe tener exactamente 32 bytes (recibido ${hash32Bytes.size})"
            }

            // Etapa 1: Manage Security Environment (MSE: SET DST)
            val mseCmd = CommandApdu.mseSetDst()
            val mseResp = transceiveWithLogging(mseCmd, "MSE: SET DST (RSA-2048 SHA-256)")

            if (!mseResp.isSuccess) {
                throw CryptoOperationException(
                    "Fallo al configurar entorno de seguridad MSE: ${mseResp.statusWord.description}",
                    mseResp.sw
                )
            }

            // Etapa 2: Perform Security Operation - Compute Digital Signature (PSO: CDS)
            val psoCmd = CommandApdu.psoComputeDigitalSignature(hash32Bytes)
            val psoResp = transceiveWithLogging(psoCmd, "PSO: Compute Digital Signature (CDS)")

            if (!psoResp.isSuccess) {
                throw CryptoOperationException(
                    "Fallo al computar firma digital en tarjeta: ${psoResp.statusWord.description}",
                    psoResp.sw
                )
            }

            val signature = psoResp.data
            if (signature.size != ApduConstants.RSA_2048_SIGNATURE_SIZE) {
                throw CryptoOperationException(
                    "Longitud de firma RSA inválida. Esperada: 256 bytes, Recibida: ${signature.size} bytes",
                    psoResp.sw
                )
            }

            signature
        }
    }

    /**
     * Extrae los bytes de carga útil del contenedor TLV propietario (Tag 0x53).
     */
    private fun extractPayloadFromTlv(data: ByteArray): ByteArray {
        if (data.isEmpty()) return byteArrayOf()

        // Si la trama está encapsulada con el tag BER-TLV 0x53
        if (data[0] == ApduConstants.TAG_BER_TLV_DATA) {
            if (data.size <= 3) return byteArrayOf()
            val firstLenByte = data[1].toInt() and 0xFF
            val payloadStart = when (firstLenByte) {
                0x82 -> 4 // Formato BER-TLV extendido: Tag 0x53, 0x82, Len_HI, Len_LO, Payload...
                else -> 3 // Formato estándar DNIe: Tag 0x53, 2 bytes de longitud (Len_HI, Len_LO), Payload...
            }
            return if (data.size > payloadStart) {
                data.copyOfRange(payloadStart, data.size)
            } else {
                byteArrayOf()
            }
        }

        // Si no inicia con 0x53, se asume que los datos son planos
        return data
    }

    /**
     * Determina la longitud exacta en bytes de la secuencia ASN.1 DER (0x30 ...).
     */
    private fun parseDerSequenceLength(derBytes: ByteArray): Int? {
        if (derBytes.size < 4 || derBytes[0] != 0x30.toByte()) return null

        val secondByte = derBytes[1].toInt() and 0xFF
        return when {
            secondByte == 0x82 -> {
                val len = ((derBytes[2].toInt() and 0xFF) shl 8) or (derBytes[3].toInt() and 0xFF)
                len + 4 // Tag 1b + Indicador 1b + 2b longitud + len
            }
            secondByte == 0x81 -> {
                val len = derBytes[2].toInt() and 0xFF
                len + 3 // Tag 1b + Indicador 1b + 1b longitud + len
            }
            secondByte < 0x80 -> {
                secondByte + 2 // Tag 1b + 1b longitud + len
            }
            else -> null
        }
    }
}
