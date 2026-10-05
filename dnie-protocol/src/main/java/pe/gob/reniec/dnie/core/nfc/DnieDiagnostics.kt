package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.crypto.PaceProtocol
import pe.gob.reniec.dnie.core.logging.ApduLogger

/** Read-only diagnostic workflow, separate from normal certificate/PIN/signing operations. */
internal class DnieDiagnostics(
    private val can: () -> String?,
    private val logger: ApduLogger,
    private val send: (CommandApdu, String) -> ResponseApdu,
    private val currentSecureChannel: () -> PaceSecureChannel?,
    private val openV3Pki: () -> Boolean,
    private val establishPace: (ByteArray?) -> PaceSecureChannel,
    private val extractPayload: (ByteArray) -> ByteArray
) {
    fun probe(): List<Pair<String, ResponseApdu>> {
        val results = mutableListOf<Pair<String, ResponseApdu>>()
        logger.logInfo("=== INICIANDO SONDEO DIAGNÓSTICO DE AIDs (DNIe v2 / v3) ===")
        if (can().isNullOrBlank() && currentSecureChannel() == null) {
            V3PkiProbe(send, logger).explore("SIN CAN, SIN PACE")
            logger.logInfo("Diagnóstico sin CAN terminado. Para probar los certificados con PACE, " +
                "ingrese el CAN y vuelva a aproximar la tarjeta.")
            return results
        }
        try {
            if (openV3Pki()) {
                V3PkiProbe(send, logger).exploreSelectedPki("PACE IAS/PKI (SM)")
                logger.logInfo("=== FIN DE SONDEO V3 IAS/PKI; sin verificar PIN ni firmar ===")
                return results
            }
        } catch (e: Exception) {
            logger.logInfo("Diagnóstico V3 IAS/PKI detenido: ${e.message}. " +
                "Retire y vuelva a aproximar la tarjeta antes de otra sesión.")
            return results
        }
        // With CAN, perform PACE before entering IAS. A prior plain IAS selection
        // blocks MF/CardAccess on this card and must not poison PACE negotiation.
        // IAS rejects SELECT by name with P2=00 (6A86 on the tested v3 card).
        // Restore ICAO explicitly before reading CardAccess; MF alone cannot exit IAS.
        val restoreIcao = send(
            CommandApdu(0, 0xA4.toByte(), 4, 0x0C, ApduConstants.AID_ICAO_MRTD),
            "SONDEO: SELECT ICAO antes de PACE (P2=0C)"
        )
        results.add(Pair("Restaurar ICAO después de IAS", restoreIcao))
        if (!restoreIcao.isSuccess) {
            logger.logInfo("Diagnóstico detenido: no se pudo restaurar ICAO después de IAS. " +
                "Retire y vuelva a aproximar la tarjeta; no se intentará PACE con parámetros supuestos.")
            return results
        }
        for ((name, aid) in ApduConstants.CANDIDATE_AIDS) {
            val cmd = CommandApdu.selectDfByName(aid)
            val resp = send(cmd, "SONDEO: SELECT $name")
            results.add(Pair(name, resp))
        }
        val mfCmd = CommandApdu.selectMasterFile()
        val mfResp = send(mfCmd, "SONDEO: SELECT Master File (3F 00)")
        results.add(Pair("Master File (3F 00)", mfResp))

        // Sondeo de Directorios Dedicados (DF) bajo Master File
        for ((dfName, dfId) in ApduConstants.CANDIDATE_DFS) {
            val dfCmd = CommandApdu.selectDf(dfId)
            var dfResp = send(dfCmd, "SONDEO: SELECT DF $dfName (P1=01)")
            if (!dfResp.isSuccess) {
                val directCmd = CommandApdu.selectDfDirect(dfId)
                dfResp = send(directCmd, "SONDEO: SELECT DF $dfName directo (P1=00)")
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
                    val efResp = send(efCmd, "SONDEO: SELECT EF 0x$efHex bajo $dfName")
                    results.add(Pair("EF 0x$efHex ($dfName)", efResp))
                }
            }
        }

        // Sondeo de EF.CardAccess (01 1C) bajo Master File
        val selMf = CommandApdu.selectMasterFile()
        send(selMf, "SONDEO: SELECT Master File (3F 00)")
        val cardAccessCmd = CommandApdu.selectEfDirect(ApduConstants.EF_CARD_ACCESS)
        val cardAccessResp = send(cardAccessCmd, "SONDEO: SELECT EF.CardAccess (01 1C)")
        results.add(Pair("EF.CardAccess (01 1C)", cardAccessResp))

        var readCardAccessBytes: ByteArray? = null
        if (cardAccessResp.isSuccess) {
            val readCmd = CommandApdu.readBinaryWithOffset(0, le = 0xFF)
            var readResp = send(readCmd, "SONDEO: READ BINARY EF.CardAccess")
            if (!readResp.isSuccess) {
                val readStdCmd = CommandApdu.readBinary(0, le = 0x00)
                readResp = send(readStdCmd, "SONDEO: READ BINARY estándar EF.CardAccess")
            }
            results.add(Pair("READ EF.CardAccess", readResp))
            if (readResp.isSuccess && readResp.data.isNotEmpty()) {
                readCardAccessBytes = extractPayload(readResp.data)
                val secInfo = PaceProtocol.parseCardAccess(readCardAccessBytes)
                if (secInfo != null) {
                    logger.logInfo("PACE detectado en DNIe: OID=${secInfo.oid}, CurveID=${secInfo.parameterId} (brainpoolP256r1), Versión=${secInfo.version}")
                }
            }
        }

        // Si se dispone de CAN, probar handshake de PACE en modo diagnóstico
        if (!can().isNullOrBlank()) {
            if (currentSecureChannel() == null && readCardAccessBytes == null) {
                logger.logInfo("Diagnóstico PACE detenido: EF.CardAccess no pudo leerse. " +
                    "No se enviará MSE con una suite supuesta; retire y vuelva a aproximar la tarjeta.")
                return results
            }
            logger.logInfo("CAN disponible (${can()?.length} dígitos): ejecutando prueba diagnóstica de canal seguro PACE...")
            try {
                val secureChannel = establishPace(readCardAccessBytes)
                logger.logInfo("¡Prueba diagnóstica de canal seguro PACE completada exitosamente! Canal SM activo.")

                V3PkiProbe(send, logger).explore("DESPUÉS DE PACE (SM)")

                // Every discovery APDU uses the retained authenticated session.
                CardDirectoryProbe(send, logger).explore()

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
                                val payload = extractPayload(readResp.data)
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
        return results
    }
}
