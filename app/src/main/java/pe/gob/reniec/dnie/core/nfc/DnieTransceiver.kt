package pe.gob.reniec.dnie.core.nfc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.apdu.StatusWord
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.core.crypto.PaceProtocol
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

        // Sondeo de Directorios Dedicados (DF) bajo Master File
        for ((dfName, dfId) in ApduConstants.CANDIDATE_DFS) {
            val dfCmd = CommandApdu.selectDf(dfId)
            var dfResp = transceiveWithLogging(dfCmd, "SONDEO: SELECT DF $dfName (P1=01)")
            if (!dfResp.isSuccess) {
                val directCmd = CommandApdu.selectDfDirect(dfId)
                dfResp = transceiveWithLogging(directCmd, "SONDEO: SELECT DF $dfName directo (P1=00)")
            }
            results.add(Pair("DF $dfName", dfResp))

            if (dfResp.isSuccess) {
                val testEfs = listOf(
                    byteArrayOf(0x00, 0x1D),
                    byteArrayOf(0x00, 0x1C),
                    byteArrayOf(0xC0.toByte(), 0x01.toByte()),
                    byteArrayOf(0x34.toByte(), 0x02.toByte()),
                    byteArrayOf(0x34.toByte(), 0x01.toByte()),
                    byteArrayOf(0x50.toByte(), 0x31.toByte()),
                    byteArrayOf(0x50.toByte(), 0x32.toByte())
                )
                for (efId in testEfs) {
                    val efHex = efId.joinToString("") { "%02X".format(it) }
                    val efCmd = CommandApdu.selectEf(efId)
                    val efResp = transceiveWithLogging(efCmd, "SONDEO: SELECT EF 0x$efHex bajo $dfName")
                    results.add(Pair("EF 0x$efHex ($dfName)", efResp))
                }
            }
        }

        // Sondeo de EF.CardAccess (01 1C) bajo Master File
        val selMf = CommandApdu.selectMasterFile()
        transceiveWithLogging(selMf, "SONDEO: SELECT Master File (3F 00)")
        val cardAccessCmd = CommandApdu.selectEfDirect(ApduConstants.EF_CARD_ACCESS)
        val cardAccessResp = transceiveWithLogging(cardAccessCmd, "SONDEO: SELECT EF.CardAccess (01 1C)")
        results.add(Pair("EF.CardAccess (01 1C)", cardAccessResp))

        var readCardAccessBytes: ByteArray? = null
        if (cardAccessResp.isSuccess) {
            val readCmd = CommandApdu.readBinaryWithOffset(0, le = 0xFF)
            var readResp = transceiveWithLogging(readCmd, "SONDEO: READ BINARY EF.CardAccess")
            if (!readResp.isSuccess) {
                val readStdCmd = CommandApdu.readBinary(0, le = 0x00)
                readResp = transceiveWithLogging(readStdCmd, "SONDEO: READ BINARY estándar EF.CardAccess")
            }
            results.add(Pair("READ EF.CardAccess", readResp))
            if (readResp.isSuccess && readResp.data.isNotEmpty()) {
                readCardAccessBytes = extractPayloadFromTlv(readResp.data)
                val secInfo = PaceProtocol.parseCardAccess(readCardAccessBytes)
                if (secInfo != null) {
                    logger.logInfo("PACE detectado en DNIe: OID=${secInfo.oid}, CurveID=${secInfo.parameterId} (brainpoolP256r1), Versión=${secInfo.version}")
                }
            }
        }

        // Si se dispone de CAN, probar handshake de PACE en modo diagnóstico
        if (!can.isNullOrBlank()) {
            logger.logInfo("CAN disponible (${can?.length} dígitos): ejecutando prueba diagnóstica de canal seguro PACE...")
            try {
                val paceHandler = PaceHandler(logger)
                val secureChannel = paceHandler.performPace(channel, can!!, readCardAccessBytes)
                logger.logInfo("¡Prueba diagnóstica de canal seguro PACE completada exitosamente! Canal SM activo.")

                // Prueba de lectura bajo Secure Messaging: SELECT Applet ICAO LDS1 -> SELECT EF.SOD (01 1D)
                try {
                    val selIcao = CommandApdu.selectDfByName(ApduConstants.AID_ICAO_MRTD)
                    logger.logTx(selIcao, "SM DIAG: SELECT Applet ICAO LDS1 eMRTD")
                    val icaoRaw = secureChannel.transceive(selIcao.toByteArray())
                    val icaoResp = ResponseApdu.fromBytes(icaoRaw)
                    logger.logRx(icaoResp, 0, "SM DIAG: SELECT Applet ICAO LDS1 eMRTD")

                    if (icaoResp.isSuccess) {
                        val selSod = CommandApdu.selectEfDirect(ApduConstants.EF_SOD)
                        logger.logTx(selSod, "SM DIAG: SELECT EF.SOD (01 1D)")
                        val sodRaw = secureChannel.transceive(selSod.toByteArray())
                        val sodResp = ResponseApdu.fromBytes(sodRaw)
                        logger.logRx(sodResp, 0, "SM DIAG: SELECT EF.SOD (01 1D)")

                        if (sodResp.isSuccess) {
                            val readSod = CommandApdu.readBinary(0, le = 256)
                            logger.logTx(readSod, "SM DIAG: READ BINARY EF.SOD [Offset 0x0000]")
                            val readRaw = secureChannel.transceive(readSod.toByteArray())
                            val readResp = ResponseApdu.fromBytes(readRaw)
                            logger.logRx(readResp, 0, "SM DIAG: READ BINARY EF.SOD [Offset 0x0000]")
                            if (readResp.isSuccess && readResp.data.isNotEmpty()) {
                                val payload = extractPayloadFromTlv(readResp.data)
                                logger.logInfo("SM DIAG: EF.SOD leído exitosamente bajo Secure Messaging (${payload.size} bytes útiles).")
                            }
                        }
                    }
                } catch (diagEx: Exception) {
                    logger.logInfo("SM DIAG: Lectura post-PACE diagnóstica no completada: ${diagEx.message}")
                }
            } catch (e: Exception) {
                logger.logInfo("Prueba diagnóstica de PACE no completada: ${e.message}")
            }
        }

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

            // Fallback: Si no se seleccionó en el applet activo, explorar bajo Master File (3F 00) y DFs dedicados
            if (!efSelected) {
                logger.logInfo("EF no seleccionado directamente; explorando bajo Master File (3F 00) y DFs...")
                val mfCmd = CommandApdu.selectMasterFile()
                val mfResp = transceiveWithLogging(mfCmd, "SELECT Master File (3F 00)")
                if (mfResp.isSuccess) {
                    // 1. Probar EFs directamente bajo MF
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

                    // 2. Probar bajo Dedicated Files (DF 70 01 y DF 50 15)
                    if (!efSelected) {
                        for ((dfName, dfId) in ApduConstants.CANDIDATE_DFS) {
                            val dfCmd = CommandApdu.selectDf(dfId)
                            var dfResp = transceiveWithLogging(dfCmd, "SELECT DF $dfName (P1=01)")
                            if (!dfResp.isSuccess) {
                                val directDfCmd = CommandApdu.selectDfDirect(dfId)
                                dfResp = transceiveWithLogging(directDfCmd, "SELECT DF $dfName directo (P1=00)")
                            }

                            if (dfResp.isSuccess) {
                                for (efId in candidateEfs) {
                                    val efHex = efId.joinToString("") { "%02X".format(it) }
                                    val selCmd = CommandApdu.selectEf(efId)
                                    val selResp = transceiveWithLogging(
                                        selCmd,
                                        "SELECT EF bajo $dfName ${type.displayName} (0x$efHex)"
                                    )
                                    lastSelectResp = selResp
                                    if (selResp.isSuccess) {
                                        efSelected = true
                                        break
                                    }
                                }
                                if (efSelected) break
                            }
                        }
                    }
                }
            }

            // Fallback con PACE: Si el acceso está protegido (SW 6982 o modo ICAO) y se cuenta con CAN
            if (!efSelected && !can.isNullOrBlank() && (isIcaoMode || lastSelectResp?.sw == StatusWord.SW_SECURITY_STATUS_NOT_SATISFIED)) {
                logger.logInfo("Acceso protegido detectado (SW: ${lastSelectResp?.statusWordHex ?: "6982"}). Iniciando establecimiento de canal seguro PACE con CAN...")
                val paceHandler = PaceHandler(logger)
                val secureChannel = paceHandler.performPace(channel, can!!)

                // Con canal seguro PACE establecido, seleccionar applet ICAO LDS1
                val icaoAppletCmd = CommandApdu.selectDfByName(ApduConstants.AID_ICAO_MRTD)
                logger.logTx(icaoAppletCmd, "SM: SELECT Applet ICAO LDS1 eMRTD")
                val appletRespRaw = secureChannel.transceive(icaoAppletCmd.toByteArray())
                val appletResp = ResponseApdu.fromBytes(appletRespRaw)
                logger.logRx(appletResp, 0, "SM: SELECT Applet ICAO LDS1 eMRTD")

                val targetEf = when (type) {
                    CertificateType.SIGNATURE -> ApduConstants.EF_SOD
                    CertificateType.AUTHENTICATION -> ApduConstants.EF_SOD
                    CertificateType.ENCRYPTION -> ApduConstants.EF_SOD
                }

                val selSodCmd = CommandApdu.selectEfDirect(targetEf)
                logger.logTx(selSodCmd, "SM: SELECT EF.SOD (01 1D)")
                val selSodRaw = secureChannel.transceive(selSodCmd.toByteArray())
                val selSodResp = ResponseApdu.fromBytes(selSodRaw)
                logger.logRx(selSodResp, 0, "SM: SELECT EF.SOD (01 1D)")

                if (selSodResp.isSuccess) {
                    val sodAccumulator = ByteArrayOutputStream()
                    var offset = 0
                    var expectedLen: Int? = null
                    val blockSize = ApduConstants.DEFAULT_BLOCK_SIZE

                    while (true) {
                        val readCmd = CommandApdu.readBinary(offset, le = blockSize)
                        logger.logTx(readCmd, "SM: READ BINARY EF.SOD [Offset 0x%04X]".format(offset))
                        val readRaw = secureChannel.transceive(readCmd.toByteArray())
                        val readResp = ResponseApdu.fromBytes(readRaw)
                        logger.logRx(readResp, 0, "SM: READ BINARY EF.SOD [Offset 0x%04X]".format(offset))

                        if (readResp.sw == StatusWord.SW_WRONG_OFFSET ||
                            readResp.sw == StatusWord.SW_INCORRECT_PARAMETERS_IN_DATA_FIELD ||
                            readResp.sw == StatusWord.SW_EOF_REACHED) {
                            if (sodAccumulator.size() > 0) {
                                break
                            }
                        }

                        if (!readResp.isSuccess) {
                            if (sodAccumulator.size() > 0) break
                            throw CertificateReadException("Fallo al leer EF.SOD bajo Secure Messaging: ${readResp.statusWord.description}", readResp.sw)
                        }

                        val payload = extractPayloadFromTlv(readResp.data)
                        if (payload.isEmpty()) break
                        sodAccumulator.write(payload)

                        if (offset == 0 && expectedLen == null) {
                            expectedLen = parseDerSequenceLength(sodAccumulator.toByteArray())
                        }

                        if (expectedLen != null && sodAccumulator.size() >= expectedLen) {
                            break
                        }

                        if (payload.size < blockSize) {
                            break
                        }

                        offset += payload.size
                    }

                    val rawSod = sodAccumulator.toByteArray()
                    if (rawSod.isNotEmpty()) {
                        logger.logInfo("EF.SOD leído exitosamente con Secure Messaging (${rawSod.size} bytes). Extrayendo certificado X.509 DSC...")
                        val cert = pe.gob.reniec.dnie.crypto.X509CertificateParser.toX509Certificate(rawSod)
                        return@runCatching cert.encoded
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

                // Condición 1: Offset fuera de límites del EF (6B 00) o parámetros incorrectos en datos (6A 80) -> Fin de archivo
                if (readResp.sw == StatusWord.SW_WRONG_OFFSET ||
                    readResp.sw == StatusWord.SW_INCORRECT_PARAMETERS_IN_DATA_FIELD) {
                    if (certAccumulator.size() > 0) {
                        logger.logInfo("Fin de archivo detectado (SW: ${readResp.statusWordHex}, bytes acumulados: ${certAccumulator.size()})")
                        break
                    }
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
                    if (certAccumulator.size() > 0) {
                        logger.logInfo("Lectura finalizada en offset 0x%04X por respuesta de tarjeta (SW: ${readResp.statusWordHex})".format(currentOffset))
                        break
                    }
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

                // Si el bloque devuelto es menor que el paso solicitado, se completó el archivo
                if (blockPayload.size < stepSize) {
                    logger.logInfo("Fin de lectura: bloque de tamaño parcial (${blockPayload.size} < $stepSize bytes).")
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
            if (data.size <= 2) return byteArrayOf()
            val firstLenByte = data[1].toInt() and 0xFF
            val (payloadStart, payloadLength) = when {
                firstLenByte == 0x82 -> {
                    if (data.size < 4) return byteArrayOf()
                    val len = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
                    Pair(4, len)
                }
                firstLenByte == 0x81 -> {
                    if (data.size < 3) return byteArrayOf()
                    val len = data[2].toInt() and 0xFF
                    Pair(3, len)
                }
                firstLenByte in 0x01..0x7F -> {
                    Pair(2, firstLenByte)
                }
                firstLenByte == 0x00 && data.size >= 3 -> {
                    val len = data[2].toInt() and 0xFF
                    Pair(3, len)
                }
                else -> {
                    val len = if (data.size >= 3) {
                        ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
                    } else data.size - 2
                    Pair(3, len)
                }
            }
            val end = minOf(data.size, payloadStart + payloadLength)
            return if (data.size >= payloadStart) {
                data.copyOfRange(payloadStart, end)
            } else {
                byteArrayOf()
            }
        }

        // Si no inicia con 0x53, se asume que los datos son planos
        return data
    }

    /**
     * Determina la longitud exacta en bytes de la secuencia ASN.1 DER (0x30 ...),
     * SET (0x31 ...), estructura ICAO (Tag 0x77) o Data Group (Tag 0x61).
     */
    private fun parseDerSequenceLength(derBytes: ByteArray): Int? {
        if (derBytes.size < 3) return null
        val first = derBytes[0].toInt() and 0xFF
        if (first != 0x30 && first != 0x31 && first != 0x77 && first != 0x61) return null

        val secondByte = derBytes[1].toInt() and 0xFF
        return when {
            secondByte == 0x82 -> {
                if (derBytes.size < 4) return null
                val len = ((derBytes[2].toInt() and 0xFF) shl 8) or (derBytes[3].toInt() and 0xFF)
                len + 4 // Tag 1b + Indicador 1b + 2b longitud + len
            }
            secondByte == 0x81 -> {
                if (derBytes.size < 3) return null
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
