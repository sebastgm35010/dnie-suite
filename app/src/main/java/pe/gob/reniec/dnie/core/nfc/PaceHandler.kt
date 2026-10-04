package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.crypto.MappedGroup
import pe.gob.reniec.dnie.core.crypto.PaceProtocol
import pe.gob.reniec.dnie.core.crypto.PaceSecurityInfo
import pe.gob.reniec.dnie.core.logging.ApduLogger
import java.io.ByteArrayOutputStream

/**
 * Excepción lanzada cuando ocurre un error en la ejecución del protocolo PACE.
 */
class PaceException(message: String, statusWord: Int? = null) : CardProtocolException(message, statusWord)

/**
 * Coordinador de alto nivel del protocolo PACE (Password Authenticated Connection Establishment)
 * con código CAN para DNIe 3.0 conforme a ICAO Doc 9303 Parte 11 y BSI TR-03110.
 */
class PaceHandler(
    private val logger: ApduLogger = ApduLogger.default
) {

    private fun transceive(channel: DnieCardChannel, cmd: CommandApdu, desc: String): ResponseApdu {
        logger.logTx(cmd, desc)
        val start = System.currentTimeMillis()
        val raw = try {
            channel.transceive(cmd.toByteArray())
        } catch (e: Exception) {
            val dur = System.currentTimeMillis() - start
            logger.logInfo("Error de transporte en PACE ($desc, ${dur}ms): ${e.message}")
            throw e
        }
        val dur = System.currentTimeMillis() - start
        val resp = ResponseApdu.fromBytes(raw)
        logger.logRx(resp, dur, desc)
        return resp
    }

    /**
     * Ejecuta el apretón de manos completo de PACE con el CAN provisto.
     * Retorna una instancia de [PaceSecureChannel] lista para transmitir APDUs cifradas.
     */
    fun performPace(
        channel: DnieCardChannel,
        can: String,
        preloadedCardAccess: ByteArray? = null
    ): PaceSecureChannel {
        require(can.isNotBlank() && can.all { it.isDigit() }) {
            "El código CAN debe contener dígitos numéricos (recibido: $can)"
        }

        logger.logInfo("=== INICIANDO PROTOCOLO PACE (CAN: ${can.take(2)}****) ===")

        // Paso 0: Obtener y parsear EF.CardAccess (01 1C)
        val cardAccessBytes = preloadedCardAccess ?: readCardAccess(channel)
        val securityInfo = if (cardAccessBytes != null) {
            PaceProtocol.parseCardAccess(cardAccessBytes)
        } else null

        val paramId = securityInfo?.parameterId ?: 13 // 13 = BrainpoolP256r1
        val version = securityInfo?.version ?: 2

        // Prioridad 1: Proponer Generic Mapping (GM) con BrainpoolP256r1 (soporte universal ICAO / IDEMIA Cosmo)
        val gmSecurityInfo = PaceSecurityInfo(
            oid = "0.4.0.127.0.7.2.2.4.2.2",
            oidValueBytes = ApduConstants.OID_PACE_ECDH_GM_AES128,
            version = version,
            parameterId = paramId
        )

        // Paso 1: Manage Security Environment (MSE: SET AT)
        // Intentar primero con Generic Mapping (GM AES-128)
        var selectedSecurityInfo = gmSecurityInfo
        val mseGmCmd = CommandApdu.mseSetAtPace(
            oidValueBytes = gmSecurityInfo.oidValueBytes,
            parameterId = gmSecurityInfo.parameterId,
            passwordType = ApduConstants.PACE_PWD_CAN
        )
        var mseResp = transceive(channel, mseGmCmd, "PACE Paso 1: MSE: SET AT (CAN - Generic Mapping GM)")

        if (!mseResp.isSuccess && securityInfo != null && securityInfo.oid != gmSecurityInfo.oid) {
            // Si la tarjeta rechaza GM, intentar con el OID nativo leído de EF.CardAccess (ej. IM)
            logger.logInfo("GM no aceptado (${mseResp.statusWord.description}); intentando con OID nativo ${securityInfo.oid}...")
            selectedSecurityInfo = securityInfo
            val mseNativeCmd = CommandApdu.mseSetAtPace(
                oidValueBytes = securityInfo.oidValueBytes,
                parameterId = securityInfo.parameterId,
                passwordType = ApduConstants.PACE_PWD_CAN
            )
            mseResp = transceive(channel, mseNativeCmd, "PACE Paso 1: MSE: SET AT (CAN - OID Nativo ${securityInfo.oid})")
        }

        if (!mseResp.isSuccess) {
            throw PaceException("Fallo en MSE: SET AT: ${mseResp.statusWord.description}", mseResp.sw)
        }

        logger.logInfo("Parámetros PACE acordados: OID=${selectedSecurityInfo.oid}, CurveID=${selectedSecurityInfo.parameterId}, Versión=${selectedSecurityInfo.version}, GM=${selectedSecurityInfo.isGenericMapping}, AES256=${selectedSecurityInfo.isAes256}")

        // Paso 2: GENERAL AUTHENTICATE - Obtención del Nonce cifrado
        // Comando: 10 86 00 00 02 7C 00 00
        val getNonceCmd = CommandApdu.generalAuthenticate(
            data = byteArrayOf(0x7C.toByte(), 0x00.toByte()),
            isChained = true,
            le = 256
        )
        val nonceResp = transceive(channel, getNonceCmd, "PACE Paso 2: Obtener Nonce cifrado")
        if (!nonceResp.isSuccess) {
            throw PaceException("Fallo al solicitar Nonce: ${nonceResp.statusWord.description}", nonceResp.sw)
        }

        val encryptedNonce = extractTagDataFrom7c(nonceResp.data, 0x80)
            ?: throw PaceException("Respuesta de Nonce no contiene Tag 0x80")

        val decryptedNonce = try {
            PaceProtocol.decryptNonce(can, encryptedNonce, isAes256 = selectedSecurityInfo.isAes256)
        } catch (e: Exception) {
            throw PaceException("Fallo al descifrar Nonce con CAN: ${e.message}")
        }
        logger.logInfo("Nonce descifrado exitosamente con CAN (${decryptedNonce.size} bytes).")

        // Paso 3: Mapeo del generador
        val mappedGroup: MappedGroup
        if (selectedSecurityInfo.isGenericMapping) {
            // Generic Mapping (PACE-GM): intercambio de Tag 0x81 (terminal) y Tag 0x82 (tarjeta)
            val mappingKey = PaceProtocol.generateMappingKey(selectedSecurityInfo.parameterId)
            val mapCmdData = buildDo7c(0x81, mappingKey.publicPointBytes)
            val mapCmd = CommandApdu.generalAuthenticate(
                data = mapCmdData,
                isChained = true,
                le = 256
            )
            val mapResp = transceive(channel, mapCmd, "PACE Paso 3: Mapeo de generador GM (Tag 0x81 -> 0x82)")
            if (!mapResp.isSuccess) {
                throw PaceException("Fallo en mapeo de generador GM: ${mapResp.statusWord.description}", mapResp.sw)
            }

            val cardMappingPub = extractTagDataFrom7c(mapResp.data, 0x82)
                ?: throw PaceException("Respuesta de mapeo no contiene Tag 0x82")

            mappedGroup = PaceProtocol.computeMappedGenerator(
                mappingPair = mappingKey,
                cardMappingPubBytes = cardMappingPub,
                nonceBytes = decryptedNonce,
                parameterId = selectedSecurityInfo.parameterId
            )
            logger.logInfo("Generador elíptico G' calculado exitosamente (Generic Mapping GM).")
        } else {
            // Integrated Mapping (PACE-IM): cálculo directo sin intercambio de Tag 0x81/0x82
            logger.logInfo("Modo Integrated Mapping (IM): derivando generador elíptico G' directamente desde Nonce...")
            mappedGroup = PaceProtocol.computeIntegratedMappedGenerator(
                nonceBytes = decryptedNonce,
                parameterId = selectedSecurityInfo.parameterId
            )
            logger.logInfo("Generador elíptico G' calculado exitosamente (Integrated Mapping IM).")
        }

        // Paso 4: GENERAL AUTHENTICATE - Acuerdo de claves efímeras
        // En Generic Mapping (GM) se utiliza Tag 0x83 (terminal) -> Tag 0x84 (tarjeta)
        // En Integrated Mapping (IM), al no haber intercambio previo de mapeo, se utiliza Tag 0x81 (terminal) -> Tag 0x82 (tarjeta)
        val sendTag = if (selectedSecurityInfo.isGenericMapping) 0x83 else 0x81
        val recvTag = if (selectedSecurityInfo.isGenericMapping) 0x84 else 0x82

        val ephemeralKey = PaceProtocol.generateEphemeralKey(mappedGroup)
        val agreeCmdData = buildDo7c(sendTag, ephemeralKey.publicPointBytes)
        val agreeCmd = CommandApdu.generalAuthenticate(
            data = agreeCmdData,
            isChained = true,
            le = 256
        )
        val agreeResp = transceive(
            channel,
            agreeCmd,
            "PACE Paso 4: Acuerdo de claves (Tag 0x%02X -> 0x%02X)".format(sendTag, recvTag)
        )
        if (!agreeResp.isSuccess) {
            throw PaceException("Fallo en acuerdo de claves: ${agreeResp.statusWord.description}", agreeResp.sw)
        }

        val cardEphemeralPub = extractTagDataFrom7c(agreeResp.data, recvTag)
            ?: throw PaceException("Respuesta de acuerdo de claves no contiene Tag 0x%02X".format(recvTag))

        val sharedSecretX = PaceProtocol.computeSharedSecretX(
            ephemeralPair = ephemeralKey,
            cardEphemeralPubBytes = cardEphemeralPub,
            mappedGroup = mappedGroup
        )
        val sessionKeys = PaceProtocol.deriveSessionKeys(
            sharedSecretX = sharedSecretX,
            isAes256 = selectedSecurityInfo.isAes256
        )
        logger.logInfo("Claves de sesión K_Enc y K_Mac derivadas exitosamente (${if (selectedSecurityInfo.isAes256) "AES-256" else "AES-128"}).")

        // Paso 5: GENERAL AUTHENTICATE - Autenticación mutua (Token MAC)
        val terminalToken = PaceProtocol.computeAuthenticationToken(
            kMac = sessionKeys.kMac,
            oidValueBytes = selectedSecurityInfo.oidValueBytes,
            pubKeyBytes = cardEphemeralPub
        )

        val authCmdData = buildDo7c(0x85, terminalToken)
        val authCmd = CommandApdu.generalAuthenticate(
            data = authCmdData,
            isChained = false, // Último comando del protocolo
            le = 256
        )
        val authResp = transceive(channel, authCmd, "PACE Paso 5: Autenticación mutua (Tag 0x85 -> 0x86)")
        if (!authResp.isSuccess) {
            throw PaceException("Fallo en verificación de token de autenticación: ${authResp.statusWord.description}", authResp.sw)
        }

        val cardToken = extractTagDataFrom7c(authResp.data, 0x86)
        if (cardToken != null) {
            val expectedCardToken = PaceProtocol.computeAuthenticationToken(
                kMac = sessionKeys.kMac,
                oidValueBytes = selectedSecurityInfo.oidValueBytes,
                pubKeyBytes = ephemeralKey.publicPointBytes
            )

            if (!expectedCardToken.contentEquals(cardToken)) {
                throw PaceException("Fallo de autenticación mutua: El token de la tarjeta (T_PICC) no coincide.")
            }
            logger.logInfo("Token de la tarjeta (T_PICC) validado correctamente contra Tag 0x86.")
        } else {
            logger.logInfo("Tarjeta autenticada con éxito (SW 9000 recibido en autenticación mutua).")
        }

        logger.logInfo("¡Autenticación mutua PACE exitosa! Canal Seguro ${if (selectedSecurityInfo.isAes256) "AES-256" else "AES-128"} CBC/CMAC establecido.")

        return PaceSecureChannel(
            baseChannel = channel,
            kEnc = sessionKeys.kEnc,
            kMac = sessionKeys.kMac
        )
    }

    /**
     * Intenta leer EF.CardAccess (01 1C) bajo Master File (3F 00).
     */
    private fun readCardAccess(channel: DnieCardChannel): ByteArray? {
        try {
            // Seleccionar Master File (3F 00)
            val mfCmd = CommandApdu.selectMasterFile()
            val mfResp = transceive(channel, mfCmd, "PACE: SELECT Master File (3F 00)")
            if (!mfResp.isSuccess) return null

            // Seleccionar EF 01 1C
            val selCmd = CommandApdu.selectEfDirect(ApduConstants.EF_CARD_ACCESS)
            val selResp = transceive(channel, selCmd, "PACE: SELECT EF.CardAccess (01 1C)")
            if (!selResp.isSuccess) return null

            // Leer con READ BINARY con Data Object de Offset (00 B1) o estándar (00 B0)
            val readCmd = CommandApdu.readBinaryWithOffset(0, le = 0xFF)
            val readResp = transceive(channel, readCmd, "PACE: READ BINARY EF.CardAccess")
            if (readResp.isSuccess && readResp.data.isNotEmpty()) {
                return readResp.data
            }

            val readStdCmd = CommandApdu.readBinary(0, le = 0x00)
            val readStdResp = transceive(channel, readStdCmd, "PACE: READ BINARY estándar EF.CardAccess")
            if (readStdResp.isSuccess && readStdResp.data.isNotEmpty()) {
                return readStdResp.data
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Empaqueta un campo de datos en una plantilla Dynamic Authentication Data (Tag 0x7C):
     * 7C <len> <innerTag> <innerLen> <data>
     */
    private fun buildDo7c(innerTag: Int, data: ByteArray): ByteArray {
        val innerStream = ByteArrayOutputStream()
        innerStream.write(innerTag)
        if (data.size < 0x80) {
            innerStream.write(data.size)
        } else if (data.size <= 0xFF) {
            innerStream.write(0x81)
            innerStream.write(data.size)
        } else {
            innerStream.write(0x82)
            innerStream.write((data.size shr 8) and 0xFF)
            innerStream.write(data.size and 0xFF)
        }
        innerStream.write(data)
        val inner = innerStream.toByteArray()

        val out = ByteArrayOutputStream()
        out.write(0x7C)
        if (inner.size < 0x80) {
            out.write(inner.size)
        } else if (inner.size <= 0xFF) {
            out.write(0x81)
            out.write(inner.size)
        } else {
            out.write(0x82)
            out.write((inner.size shr 8) and 0xFF)
            out.write(inner.size and 0xFF)
        }
        out.write(inner)
        return out.toByteArray()
    }

    /**
     * Extrae el contenido de un tag interno dentro de la plantilla Dynamic Authentication Data (Tag 0x7C).
     */
    private fun extractTagDataFrom7c(responseBytes: ByteArray, targetTag: Int): ByteArray? {
        if (responseBytes.size < 3) return null

        var offset = 0
        if (responseBytes[0] == 0x7C.toByte()) {
            offset = 1
            val lenByte = responseBytes[offset++].toInt() and 0xFF
            if (lenByte == 0x81) offset += 1
            else if (lenByte == 0x82) offset += 2
        }

        while (offset < responseBytes.size) {
            val tag = responseBytes[offset++].toInt() and 0xFF
            if (offset >= responseBytes.size) break

            val firstLen = responseBytes[offset++].toInt() and 0xFF
            val len = when {
                firstLen == 0x82 -> {
                    if (offset + 1 >= responseBytes.size) break
                    val l = ((responseBytes[offset].toInt() and 0xFF) shl 8) or (responseBytes[offset + 1].toInt() and 0xFF)
                    offset += 2
                    l
                }
                firstLen == 0x81 -> {
                    if (offset >= responseBytes.size) break
                    val l = responseBytes[offset].toInt() and 0xFF
                    offset += 1
                    l
                }
                else -> firstLen
            }

            val end = minOf(responseBytes.size, offset + len)
            if (tag == targetTag) {
                return responseBytes.copyOfRange(offset, end)
            }
            offset = end
        }
        return null
    }
}
