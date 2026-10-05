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

    private var secureChannel: PaceSecureChannel? = null
    private var isV3Pki = false

    private fun openV3Pki(): Boolean {
        if (isV3Pki) return true
        // A secure ICAO session cannot be reused for IAS. Never send plain APDUs
        // while that session is active; a fresh tag connection is required.
        if (secureChannel != null) return false
        val opened = V3PkiSession.open(can, ::transceiveWithLogging) { profile ->
            logger.logInfo("V3: PACE en IAS/PKI con perfil NFC IM AES-256, curva 13; sin seleccionar ICAO ni MF.")
            secureChannel = PaceHandler(logger).performPace(channel, can!!, profileSecurityInfo = profile)
        }
        if (opened) {
            isV3Pki = true
            activeAppletName = "DNIe v3 IAS/PKI"
            logger.logInfo("V3: aplicación PKI seleccionada con PACE IAS autenticado.")
        }
        return opened
    }

    private fun establishPace(preloadedCardAccess: ByteArray? = null): PaceSecureChannel {
        return secureChannel ?: PaceHandler(logger).performPace(
            channel, can ?: throw PaceException("Ingrese el CAN para abrir el canal seguro"), preloadedCardAccess
        ).also { secureChannel = it }
    }

    private fun requirePersonalPki() {
        if (isIcaoMode) throw CardProtocolException(
            "Canal ICAO detectado. No se encontró PKI personal entre los AIDs probados. " +
                "EF.SOD contiene el certificado del emisor del documento, no el certificado de firma del titular. " +
                "Use el diagnóstico de consola para explorar EF.DIR y los directorios después de PACE."
        )
    }

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
            (secureChannel ?: channel).transceive(command.toByteArray())
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
            if (openV3Pki()) return@runCatching
            var selectedName: String? = null
            var lastResp: ResponseApdu? = null

            if (!can.isNullOrBlank()) {
                logger.logInfo("Sesión DNIe inicializada con CAN provisto (${can?.length} dígitos).")
            }

            // Intentar lista priorizada de AIDs candidatos para DNIe v3 (ICAO, IDEMIA) y v2 (PGeneric)
            // Prefer personal PKI; ICAO is a separate application, not a signing applet.
            val candidates = ApduConstants.CANDIDATE_AIDS.sortedBy { it.second.contentEquals(ApduConstants.AID_ICAO_MRTD) }
            for ((name, aid) in candidates) {
                val cmd = CommandApdu.selectDfByName(aid)
                val resp = transceiveWithLogging(cmd, "SELECT Applet: $name")
                lastResp = resp

                if (resp.isSuccess || resp.sw1 == 0x61 || resp.sw1 == 0x90) {
                    selectedName = name
                    activeAppletName = name
                    logger.logInfo("Aplicación detectada: $name (SW: ${resp.statusWordHex})")
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

            if (isIcaoMode && !can.isNullOrBlank()) {
                establishPace()
                // Some chips expose additional applications only after PACE.
                for ((name, aid) in candidates.filterNot { it.second.contentEquals(ApduConstants.AID_ICAO_MRTD) }) {
                    val resp = transceiveWithLogging(CommandApdu.selectDfByName(aid), "SM: SELECT PKI $name")
                    if (resp.isSuccess) {
                        activeAppletName = name
                        break
                    }
                }
                if (isIcaoMode) {
                    val resp = transceiveWithLogging(CommandApdu.selectDfByName(ApduConstants.AID_ICAO_MRTD), "SM: SELECT ICAO")
                    if (!resp.isSuccess) throw CardProtocolException("Fallo al seleccionar ICAO con canal seguro", resp.sw)
                    logger.logInfo("PACE correcto. Aplicación ICAO disponible; PKI personal no encontrada por los AIDs probados.")
                }
            }
        }
    }

    /**
     * Realiza un sondeo diagnóstico exhaustivo de todos los AIDs conocidos en la tarjeta.
     */
    suspend fun probeAllAids(): List<Pair<String, ResponseApdu>> = withContext(ioDispatcher) {
        val results = mutableListOf<Pair<String, ResponseApdu>>()
        logger.logInfo("=== INICIANDO SONDEO DIAGNÓSTICO DE AIDs (DNIe v2 / v3) ===")
        if (can.isNullOrBlank() && secureChannel == null) {
            V3PkiProbe(::transceiveWithLogging, logger).explore("SIN CAN, SIN PACE")
            logger.logInfo("Diagnóstico sin CAN terminado. Para probar los certificados con PACE, " +
                "ingrese el CAN y vuelva a aproximar la tarjeta.")
            return@withContext results
        }
        try {
            if (openV3Pki()) {
                V3PkiProbe(::transceiveWithLogging, logger).exploreSelectedPki("PACE IAS/PKI (SM)")
                logger.logInfo("=== FIN DE SONDEO V3 IAS/PKI; sin verificar PIN ni firmar ===")
                return@withContext results
            }
        } catch (e: Exception) {
            logger.logInfo("Diagnóstico V3 IAS/PKI detenido: ${e.message}. " +
                "Retire y vuelva a aproximar la tarjeta antes de otra sesión.")
            return@withContext results
        }
        // With CAN, perform PACE before entering IAS. A prior plain IAS selection
        // blocks MF/CardAccess on this card and must not poison PACE negotiation.
        // IAS rejects SELECT by name with P2=00 (6A86 on the tested v3 card).
        // Restore ICAO explicitly before reading CardAccess; MF alone cannot exit IAS.
        val restoreIcao = transceiveWithLogging(
            CommandApdu(0, 0xA4.toByte(), 4, 0x0C, ApduConstants.AID_ICAO_MRTD),
            "SONDEO: SELECT ICAO antes de PACE (P2=0C)"
        )
        results.add(Pair("Restaurar ICAO después de IAS", restoreIcao))
        if (!restoreIcao.isSuccess) {
            logger.logInfo("Diagnóstico detenido: no se pudo restaurar ICAO después de IAS. " +
                "Retire y vuelva a aproximar la tarjeta; no se intentará PACE con parámetros supuestos.")
            return@withContext results
        }
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
            if (secureChannel == null && readCardAccessBytes == null) {
                logger.logInfo("Diagnóstico PACE detenido: EF.CardAccess no pudo leerse. " +
                    "No se enviará MSE con una suite supuesta; retire y vuelva a aproximar la tarjeta.")
                return@withContext results
            }
            logger.logInfo("CAN disponible (${can?.length} dígitos): ejecutando prueba diagnóstica de canal seguro PACE...")
            try {
                val secureChannel = establishPace(readCardAccessBytes)
                logger.logInfo("¡Prueba diagnóstica de canal seguro PACE completada exitosamente! Canal SM activo.")

                V3PkiProbe(::transceiveWithLogging, logger).explore("DESPUÉS DE PACE (SM)")

                // Every discovery APDU uses the retained authenticated session.
                CardDirectoryProbe(::transceiveWithLogging, logger).explore()

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
                            val readSod = CommandApdu.readBinary(0, le = 223)
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
                type.fileId, // 00 1D (DNIe v2)
                ApduConstants.EF_COSMO_SIGNATURE, // 20 01 (IDEMIA Cosmo DF 5011 / PKCS#15)
                ApduConstants.EF_IAS_ECC_341F,    // 34 1F (IAS-ECC v3)
                byteArrayOf(0x34.toByte(), 0x02.toByte()), // 34 02 (PKI DF v3)
                byteArrayOf(0xC0.toByte(), 0x01.toByte()), // C0 01 (IAS-ECC v3)
                byteArrayOf(0x00.toByte(), 0x02.toByte())
            )
            CertificateType.AUTHENTICATION -> listOf(
                type.fileId, // 00 1C (DNIe v2)
                ApduConstants.EF_COSMO_AUTHENTICATION, // 20 00 (IDEMIA Cosmo DF 5011 / PKCS#15)
                ApduConstants.EF_IAS_ECC_3401,         // 34 01 (IAS-ECC v3)
                byteArrayOf(0xC0.toByte(), 0x02.toByte()), // C0 02 (IAS-ECC v3)
                byteArrayOf(0x00.toByte(), 0x01.toByte())
            )
            CertificateType.ENCRYPTION -> listOf(
                type.fileId, // 00 1B (DNIe v2)
                byteArrayOf(0x20.toByte(), 0x02.toByte()), // 20 02 (IDEMIA Cosmo)
                byteArrayOf(0x34.toByte(), 0x03.toByte()),
                byteArrayOf(0xC0.toByte(), 0x03.toByte())
            )
        }
    }

    override suspend fun readCertificate(type: CertificateType): Result<ByteArray> = withContext(ioDispatcher) {
        runCatching {
            requirePersonalPki()
            if (isV3Pki) {
                val file = when (type) {
                    CertificateType.SIGNATURE -> "CE82"
                    CertificateType.AUTHENTICATION -> "CE81"
                    CertificateType.ENCRYPTION -> "CE83"
                }
                return@runCatching V3PkiProbe(::transceiveWithLogging, logger)
                    .readFile(file, type.displayName, "V3 SM")
                    ?: throw CertificateReadException("No se pudo leer el X.509 completo de $file (${type.displayName})")
            }
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

            // Retry the same personal PKI application over the retained secure channel.
            if (!efSelected && secureChannel == null && !can.isNullOrBlank() &&
                lastSelectResp?.sw == StatusWord.SW_SECURITY_STATUS_NOT_SATISFIED) {
                val applet = ApduConstants.CANDIDATE_AIDS.firstOrNull { it.first == activeAppletName }
                    ?: throw CertificateReadException("No se conoce el AID de la aplicación PKI activa")
                establishPace()
                val selected = transceiveWithLogging(CommandApdu.selectDfByName(applet.second), "SM: SELECT PKI personal")
                if (!selected.isSuccess) throw CertificateReadException("Aplicación PKI no accesible tras PACE", selected.sw)
                return@runCatching readCertificate(type).getOrThrow()
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
            // Reserve room for padding and response DO87/DO99/DO8E within short Le=256.
            val stepSize = if (secureChannel != null) 223 else ApduConstants.DEFAULT_BLOCK_SIZE
            var useStandardReadBinary = secureChannel != null

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

                currentOffset += blockPayload.size
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
            requirePersonalPki()
            // Validación sintáctica previa antes de impactar el chip físico
            if (!pin.all { it.isDigit() } || pin.length !in 4..8) {
                throw IllegalArgumentException("El PIN debe tener entre 4 y 8 dígitos numéricos")
            }

            val typeDesc = if (pinType == ApduConstants.PIN_TYPE_SIGNATURE) "Firma" else "Autenticación"
            val cmd = if (isV3Pki) V3PkiSession.verifyPin(pin) else CommandApdu.verifyPin(pin, pinType)
            val resp = transceiveWithLogging(cmd, "VERIFY PIN $typeDesc (0x%02X)".format(cmd.p2))

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
            requirePersonalPki()
            require(hash32Bytes.size == ApduConstants.SHA256_HASH_SIZE) {
                "El hash SHA-256 debe tener exactamente 32 bytes (recibido ${hash32Bytes.size})"
            }

            // Etapa 1: Manage Security Environment (MSE: SET DST)
            val mseCmd = if (isV3Pki) V3PkiSession.signatureEnvironment() else CommandApdu.mseSetDst()
            val mseResp = transceiveWithLogging(mseCmd, "MSE: SET DST (RSA-2048 SHA-256)")

            if (!mseResp.isSuccess) {
                throw CryptoOperationException(
                    "Fallo al configurar entorno de seguridad MSE: ${mseResp.statusWord.description}",
                    mseResp.sw
                )
            }

            // Etapa 2: Perform Security Operation - Compute Digital Signature (PSO: CDS)
            val psoCmd = if (isV3Pki) V3PkiSession.sign(hash32Bytes) else CommandApdu.psoComputeDigitalSignature(hash32Bytes)
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
