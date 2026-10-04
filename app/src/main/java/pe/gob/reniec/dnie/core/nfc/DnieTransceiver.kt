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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    var can: String? = null,
    var pin: String? = null
) : DnieTransceiverInterface {

    var activeAppletName: String? = null
        private set

    val isIcaoMode: Boolean
        get() = activeAppletName?.contains("ICAO", ignoreCase = true) == true

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
            var selectedName: String? = null
            var lastResp: ResponseApdu? = null

            if (!can.isNullOrBlank()) {
                logger.logInfo("Sesión DNIe inicializada con CAN provisto (${can?.length} dígitos).")
            }

            // Intentar lista priorizada de AIDs candidatos para DNIe v3 (ICAO, IDEMIA) y v2 (PGeneric)
            for ((name, aid) in ApduConstants.CANDIDATE_AIDS) {
                val cmd = CommandApdu.selectDfByName(aid)
                val resp = transceiveWithLogging(cmd, "SELECT Applet: $name")
                lastResp = resp

                if (resp.isSuccess || resp.sw1 == 0x61 || resp.sw1 == 0x90) {
                    selectedName = name
                    activeAppletName = name
                    logger.logInfo("¡Applet compatible detectado exitosamente: $name (SW: ${resp.statusWordHex})!")
                    break
                }
            }

            // Si ningún AID por nombre respondió OK, intentar Master File (MF 3F 00)
            if (selectedName == null) {
                val mfCmd = CommandApdu.selectMasterFile()
                val mfResp = transceiveWithLogging(mfCmd, "SELECT Master File (3F 00)")
                lastResp = mfResp
                if (mfResp.isSuccess || mfResp.sw1 == 0x61 || mfResp.sw1 == 0x90) {
                    selectedName = "Master File (3F 00)"
                    activeAppletName = selectedName
                    logger.logInfo("Master File (3F 00) seleccionado exitosamente.")
                }
            }

            if (selectedName == null) {
                val sw = lastResp?.sw ?: StatusWord.SW_FILE_NOT_FOUND
                throw AppletNotFoundException(
                    "No se encontró un Applet PKI reconocido en la tarjeta (SW: %04X). Para DNIe 3.0, el chip puede requerir canal seguro PACE/CAN.".format(sw),
                    sw
                )
            }
        }
    }

    /**
     * Realiza un sondeo diagnóstico exhaustivo de todos los AIDs conocidos en la tarjeta.
     */
    suspend fun probeAllAids(): List<Pair<String, ResponseApdu>> = withContext(ioDispatcher) {
        val results = mutableListOf<Pair<String, ResponseApdu>>()
        logger.logInfo("=== INICIANDO SONDEO DIAGNÓSTICO DE AIDs (DNIe v2 / v3) ===")
        for ((name, aid) in ApduConstants.CANDIDATE_AIDS) {
            val cmd = CommandApdu.selectDfByName(aid)
            val resp = transceiveWithLogging(cmd, "SONDEO: SELECT $name")
            results.add(Pair(name, resp))
        }
        val mfCmd = CommandApdu.selectMasterFile()
        val mfResp = transceiveWithLogging(mfCmd, "SONDEO: SELECT Master File (3F 00)")
        results.add(Pair("Master File (3F 00)", mfResp))
        logger.logInfo("=== FIN DE SONDEO DIAGNÓSTICO ===")
        results
    }

    private fun getAlternativeEfIds(type: CertificateType): List<ByteArray> {
        return when (type) {
            CertificateType.SIGNATURE -> listOf(
                type.fileId, // 00 1D (v2 o EF.SOD en ICAO)
                byteArrayOf(0x01.toByte(), 0x1C.toByte()), // 01 1C (EF.CardAccess en ICAO)
                byteArrayOf(0x01.toByte(), 0x1D.toByte()), // 01 1D (EF.SOD alternativo)
                byteArrayOf(0xC0.toByte(), 0x01.toByte()), // IAS-ECC (v3)
                byteArrayOf(0x34.toByte(), 0x02.toByte()), // PKI DF (v3)
                byteArrayOf(0x00.toByte(), 0x02.toByte())
            )
            CertificateType.AUTHENTICATION -> listOf(
                type.fileId, // 00 1C (v2)
                byteArrayOf(0x01.toByte(), 0x1C.toByte()), // 01 1C (EF.CardAccess)
                byteArrayOf(0xC0.toByte(), 0x02.toByte()), // IAS-ECC (v3)
                byteArrayOf(0x34.toByte(), 0x01.toByte()), // PKI DF (v3)
                byteArrayOf(0x00.toByte(), 0x01.toByte())
            )
            CertificateType.ENCRYPTION -> listOf(
                type.fileId, // 00 1B (v2)
                byteArrayOf(0xC0.toByte(), 0x03.toByte()),
                byteArrayOf(0x34.toByte(), 0x03.toByte())
            )
        }
    }

    override suspend fun readCertificate(type: CertificateType): Result<ByteArray> = withContext(ioDispatcher) {
        runCatching {
            // Paso 1: Seleccionar el Archivo Elemental (EF) probando identificadores estándar y alternativos
            val candidateEfs = getAlternativeEfIds(type)
            var efSelected = false
            var lastSelectResp: ResponseApdu? = null

            for (efId in candidateEfs) {
                val efHex = efId.joinToString("") { "%02X".format(it) }
                // 1. Probar SELECT EF bajo DF actual (P1=0x02, P2=0x0C)
                val selectCmd = CommandApdu.selectEf(efId)
                var selectResp = transceiveWithLogging(
                    selectCmd,
                    "SELECT EF ${type.displayName} (0x$efHex)"
                )
                lastSelectResp = selectResp

                // 2. Si falló con P1=02, probar SELECT EF directo (P1=0x00, P2=0x0C)
                if (!selectResp.isSuccess) {
                    val selectDirectCmd = CommandApdu.selectEfDirect(efId)
                    val directResp = transceiveWithLogging(
                        selectDirectCmd,
                        "SELECT EF direct ${type.displayName} (0x$efHex)"
                    )
                    if (directResp.isSuccess) {
                        selectResp = directResp
                        lastSelectResp = directResp
                    }
                }

                if (selectResp.isSuccess) {
                    efSelected = true
                    logger.logInfo("EF 0x$efHex (${type.displayName}) seleccionado exitosamente.")
                    break
                }
            }

            // Fallback para ICAO: Si no se seleccionó bajo ICAO, intentar seleccionando Master File (3F 00)
            if (!efSelected && isIcaoMode) {
                logger.logInfo("EF no seleccionado bajo ICAO eMRTD; intentando bajo Master File (3F 00)...")
                val mfCmd = CommandApdu.selectMasterFile()
                val mfResp = transceiveWithLogging(mfCmd, "SELECT Master File (3F 00)")
                if (mfResp.isSuccess) {
                    for (efId in candidateEfs) {
                        val efHex = efId.joinToString("") { "%02X".format(it) }
                        val selectCmd = CommandApdu.selectEf(efId)
                        val selectResp = transceiveWithLogging(
                            selectCmd,
                            "SELECT EF bajo MF ${type.displayName} (0x$efHex)"
                        )
                        lastSelectResp = selectResp
                        if (selectResp.isSuccess) {
                            efSelected = true
                            break
                        }
                        val selectDirectCmd = CommandApdu.selectEfDirect(efId)
                        val directResp = transceiveWithLogging(
                            selectDirectCmd,
                            "SELECT EF direct bajo MF ${type.displayName} (0x$efHex)"
                        )
                        if (directResp.isSuccess) {
                            efSelected = true
                            lastSelectResp = directResp
                            break
                        }
                    }
                }
            }

            if (!efSelected) {
                val sw = lastSelectResp?.sw ?: StatusWord.SW_FILE_NOT_FOUND
                if (sw == StatusWord.SW_FILE_NOT_FOUND && !type.isMandatory) {
                    throw CertificateReadException(
                        "Certificado opcional ${type.displayName} no está personalizado en este chip",
                        sw
                    )
                }
                throw CertificateReadException(
                    "Error al seleccionar EF para ${type.displayName}: ${lastSelectResp?.statusWord?.description ?: "No encontrado"}",
                    sw
                )
            }

            // Paso 2: Lectura secuencial de bloques
            val certAccumulator = ByteArrayOutputStream()
            var currentOffset = 0
            var expectedDerLength: Int? = null
            val stepSize = ApduConstants.DEFAULT_BLOCK_SIZE // 0x00E4 (228 bytes)
            var useStandardReadBinary = false

            while (true) {
                val readCmd = if (useStandardReadBinary) {
                    CommandApdu.readBinary(currentOffset, le = stepSize)
                } else {
                    CommandApdu.readBinaryWithOffset(currentOffset, le = 0xFF)
                }

                var readResp = transceiveWithLogging(
                    readCmd,
                    "READ BINARY ${type.displayName} [Offset 0x%04X (%d)]".format(currentOffset, currentOffset)
                )

                // Fallback: Si INS 0xB1 falla en offset 0 (ej. en modo ICAO o 6D00), conmutar a 00 B0
                if (!useStandardReadBinary && currentOffset == 0 && !readResp.isSuccess) {
                    logger.logInfo("READ BINARY DO (00 B1) no soportado o falló (SW: ${readResp.statusWordHex}), conmutando a READ BINARY estándar ISO 7816-4 (00 B0)...")
                    useStandardReadBinary = true
                    val fallbackCmd = CommandApdu.readBinary(currentOffset, le = stepSize)
                    readResp = transceiveWithLogging(
                        fallbackCmd,
                        "READ BINARY estándar ${type.displayName} [Offset 0x0000]"
                    )
                }

                // Condición 1: Offset fuera de límites del EF (6B 00) -> Fin de archivo
                if (readResp.sw == StatusWord.SW_WRONG_OFFSET) {
                    break
                }

                // Condición 2: Fin de archivo alcanzado (62 82)
                if (readResp.sw == StatusWord.SW_EOF_REACHED) {
                    val remainingPayload = if (useStandardReadBinary) readResp.data else extractPayloadFromTlv(readResp.data)
                    if (remainingPayload.isNotEmpty()) {
                        certAccumulator.write(remainingPayload)
                    }
                    break
                }

                // Condición 3: Longitud Le incorrecta (SW 6C xx) -> Reintentar con Le exacto
                if (readResp.sw1 == 0x6C) {
                    val correctLe = if (readResp.sw2 == 0) 256 else readResp.sw2
                    val retryCmd = if (useStandardReadBinary) {
                        CommandApdu.readBinary(currentOffset, le = correctLe)
                    } else {
                        CommandApdu.readBinaryWithOffset(currentOffset, le = correctLe)
                    }
                    readResp = transceiveWithLogging(
                        retryCmd,
                        "READ BINARY reintento con Le=0x%02X".format(correctLe)
                    )
                }

                // Si ocurrió cualquier otro error
                if (!readResp.isSuccess) {
                    throw CertificateReadException(
                        "Fallo de lectura en offset 0x%04X: %s".format(currentOffset, readResp.statusWord.description),
                        readResp.sw
                    )
                }

                // Extraer carga útil del contenedor TLV (Tag 0x53) o datos planos
                val blockPayload = if (useStandardReadBinary) readResp.data else extractPayloadFromTlv(readResp.data)
                if (blockPayload.isEmpty()) {
                    break
                }

                certAccumulator.write(blockPayload)

                // En el bloque 0, detectar la longitud exacta ASN.1 DER del certificado o estructura TLV
                if (currentOffset == 0 && expectedDerLength == null) {
                    val accumulated = certAccumulator.toByteArray()
                    expectedDerLength = parseDerSequenceLength(accumulated)
                }

                // Verificar si ya se leyó la totalidad de bytes del certificado DER
                if (expectedDerLength != null && certAccumulator.size() >= expectedDerLength) {
                    break
                }

                currentOffset += if (useStandardReadBinary) blockPayload.size else stepSize
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
     * Determina la longitud exacta en bytes de la secuencia ASN.1 DER (0x30 ...) o estructura ICAO (Tag 0x77).
     */
    private fun parseDerSequenceLength(derBytes: ByteArray): Int? {
        if (derBytes.size < 4) return null
        val first = derBytes[0].toInt() and 0xFF
        if (first != 0x30 && first != 0x77) return null

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
