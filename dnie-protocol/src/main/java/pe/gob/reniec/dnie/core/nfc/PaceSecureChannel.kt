package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.crypto.PaceProtocol
import pe.gob.reniec.dnie.core.logging.ApduLogger
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Canal de comunicación seguro para DNIe v3.0 / ICAO eMRTD.
 *
 * Envuelve cualquier canal [DnieCardChannel] físico aplicando el protocolo de
 * Mensajería Segura (Secure Messaging) conforme a ISO/IEC 7816-4 e ICAO Doc 9303 Parte 11:
 * - Cifrado de datos con AES-128-CBC e IV derivado dinámicamente de SSC mediante AES-ECB.
 * - Integridad y autenticación de tramas mediante AES-CMAC truncado a 8 bytes (DO 8E).
 * - Mantenimiento de contador de secuencia (Send Sequence Counter - SSC) de 16 bytes.
 */
class PaceSecureChannel(
    private val baseChannel: DnieCardChannel,
    private val kEnc: ByteArray,
    private val kMac: ByteArray,
    private val logger: ApduLogger = ApduLogger.default
) : DnieCardChannel {

    private val ssc = ByteArray(16) // Inicializado a 16 ceros en PACE
    private var valid = true
    override val extendedLengthSupported: Boolean get() = baseChannel.extendedLengthSupported
    override val maxCommandLength: Int get() = baseChannel.maxCommandLength

    @Synchronized
    override fun transceive(command: ByteArray): ByteArray {
        check(valid) { "Sesión segura invalidada; retire y vuelva a aproximar el DNIe" }
        try {
            val cmd = CommandApdu.fromBytes(command)
            val wrappedApdu = wrapCommand(cmd)
            logger.logTx(CommandApdu.fromBytes(wrappedApdu), "SM WIRE: comando protegido")
            val rawResponse = collectPhysicalResponse(wrappedApdu)
            return unwrapResponse(rawResponse)
        } catch (e: Exception) {
            valid = false
            throw e
        }
    }

    /** A short physical response can split a single SM envelope (RSA-2048: 291 bytes).
     * Retrieve transport fragments before parsing or authenticating DO87/DO99/DO8E.
     * GET RESPONSE is plain transport, as observed in the v3 NFC implementation;
     * it does not wrap another command or advance the secure session counter.
     */
    private fun collectPhysicalResponse(command: ByteArray): ByteArray {
        fun exchange(bytes: ByteArray): ResponseApdu {
            val start = System.currentTimeMillis()
            val raw = baseChannel.transceive(bytes)
            if (raw.size < 2) throw CardProtocolException("Fragmento de respuesta física truncado")
            return ResponseApdu.fromBytes(raw).also {
                logger.logRx(it, System.currentTimeMillis() - start,
                    "SM WIRE: respuesta protegida (estado externo)")
            }
        }
        var response = exchange(command)
        if (response.sw1 != 0x61) return response.toByteArray()
        val output = ByteArrayOutputStream()
        var continuations = 0
        while (response.sw1 == 0x61) {
            output.write(response.data)
            if (output.size() > 16384 || ++continuations > 64)
                throw CardProtocolException("Continuación de respuesta segura excedió el límite")
            val getResponse = CommandApdu(0, 0xC0.toByte(), 0, 0,
                le = if (response.sw2 == 0) 256 else response.sw2)
            logger.logTx(getResponse, "SM WIRE: GET RESPONSE físico (sin incrementar SSC)")
            response = exchange(getResponse.toByteArray())
        }
        output.write(response.data)
        if (output.size() > 16384) throw CardProtocolException("Respuesta segura excedió el límite")
        logger.logInfo("SM: respuesta reunida en ${continuations + 1} fragmentos; validación MAC pendiente.")
        return output.toByteArray() + byteArrayOf(response.sw1.toByte(), response.sw2.toByte())
    }

    override fun isConnected(): Boolean = baseChannel.isConnected()

    override fun setTimeout(timeoutMs: Int) = baseChannel.setTimeout(timeoutMs)

    override fun close() = baseChannel.close()

    /**
     * Incrementa el Send Sequence Counter (SSC) como un entero de 128 bits en Big-Endian.
     */
    private fun incrementSsc() {
        for (i in ssc.size - 1 downTo 0) {
            val v = (ssc[i].toInt() and 0xFF) + 1
            ssc[i] = v.toByte()
            if (v <= 0xFF) break
        }
    }

    /**
     * Calcula el Vector de Inicialización (IV) para cifrado AES cifrando el SSC con K_Enc:
     * IV = AES-128-ECB(K_Enc, SSC)
     */
    private fun computeIv(): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kEnc, "AES"))
        return cipher.doFinal(ssc)
    }

    /**
     * Aplica relleno ISO/IEC 7816-4 Método 2: agrega 0x80 y completa con 0x00 hasta múltiplo de 16.
     */
    private fun padIso7816(data: ByteArray): ByteArray {
        val padLen = 16 - (data.size % 16)
        val padded = ByteArray(data.size + padLen)
        System.arraycopy(data, 0, padded, 0, data.size)
        padded[data.size] = 0x80.toByte()
        return padded
    }

    /**
     * Remueve el relleno ISO/IEC 7816-4 Método 2.
     */
    private fun unpadIso7816(data: ByteArray): ByteArray {
        for (i in data.size - 1 downTo 0) {
            if (data[i] == 0x80.toByte()) {
                return data.copyOfRange(0, i)
            } else if (data[i] != 0x00.toByte()) {
                break
            }
        }
        throw CardProtocolException("Padding inválido en respuesta segura")
    }

    /**
     * Encapsula un [CommandApdu] estándar en una trama con Mensajería Segura (SM).
     */
    fun wrapCommand(cmd: CommandApdu): ByteArray {
        incrementSsc()

        val maskedCla = (cmd.cla.toInt() or 0x0C).toByte()
        val header = byteArrayOf(maskedCla, cmd.ins, cmd.p1, cmd.p2)
        val paddedHeader = padIso7816(header)

        var do87: ByteArray? = null
        if (cmd.data.isNotEmpty()) {
            val iv = computeIv()
            val paddedData = padIso7816(cmd.data)
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kEnc, "AES"), IvParameterSpec(iv))
            val ciphertext = cipher.doFinal(paddedData)

            // Tag 87: 0x87 <len+1> 0x01 <ciphertext> (0x01 = padding content indicator)
            val oddInstruction = (cmd.ins.toInt() and 1) != 0
            val content = if (oddInstruction) ciphertext else byteArrayOf(0x01.toByte()) + ciphertext
            do87 = buildTlv(if (oddInstruction) 0x85 else 0x87, content)
        }

        var do97: ByteArray? = null
        val responseLength = cmd.le
        if (responseLength != null) {
            do97 = if (cmd.isExtended) byteArrayOf(0x97.toByte(), 2,
                (responseLength shr 8).toByte(), responseLength.toByte())
            else byteArrayOf(0x97.toByte(), 1, responseLength.toByte())
        }

        // Cómputo del MAC: M = SSC || paddedHeader || DO87? || DO97?
        val macStream = ByteArrayOutputStream()
        macStream.write(ssc)
        macStream.write(paddedHeader)
        if (do87 != null) macStream.write(do87)
        if (do97 != null) macStream.write(do97)

        val mac = PaceProtocol.computeCmac(kMac, padIso7816(macStream.toByteArray()))
        val do8e = byteArrayOf(0x8E.toByte(), 0x08.toByte()) + mac

        // Combinar datos seguros
        val smDataStream = ByteArrayOutputStream()
        if (do87 != null) smDataStream.write(do87)
        if (do97 != null) smDataStream.write(do97)
        smDataStream.write(do8e)

        val smData = smDataStream.toByteArray()
        val extendedWire = cmd.isExtended || smData.size > 255
        return CommandApdu(maskedCla, cmd.ins, cmd.p1, cmd.p2, smData,
            le = if (extendedWire) 65536 else 256, extended = extendedWire).toByteArray()
    }

    /**
     * Desencapsula y valida una respuesta APDU protegida con Mensajería Segura (SM).
     */
    fun unwrapResponse(rawSmResponse: ByteArray): ByteArray {
        if (rawSmResponse.size < 2) throw CardProtocolException("Respuesta segura truncada")

        // Si la respuesta solo tiene 2 bytes (SW de error puro, ej. 69 82, 6A 82, etc.)
        if (rawSmResponse.size == 2) {
            throw CardProtocolException("Respuesta sin protección en canal seguro",
                ((rawSmResponse[0].toInt() and 255) shl 8) or (rawSmResponse[1].toInt() and 255))
        }

        incrementSsc()

        val smPayload = rawSmResponse.copyOfRange(0, rawSmResponse.size - 2)
        val tlvMap = parseTlvObjects(smPayload)

        val do87 = tlvMap[0x87]
        val do85 = tlvMap[0x85]
        val encryptedObject = do85 ?: do87
        val encryptedTag = if (do85 != null) 0x85 else 0x87
        val do99 = tlvMap[0x99]
        val do8e = tlvMap[0x8E]
        if (do99 == null || do99.size != 2 || do8e == null || do8e.size != 8) {
            throw CardProtocolException("Respuesta segura sin DO99/DO8E válidos")
        }
        if (tlvMap.keys.any { it !in setOf(0x85, 0x87, 0x99, 0x8E) } || (do85 != null && do87 != null) || tlvMap.keys.toList() !=
            (if (encryptedObject != null) listOf(encryptedTag, 0x99, 0x8E) else listOf(0x99, 0x8E))) {
            throw CardProtocolException("Objetos de respuesta segura inesperados o fuera de orden")
        }

        // Every protected response must authenticate its status and ciphertext.
        run {
            val macStream = ByteArrayOutputStream()
            macStream.write(ssc)
            if (encryptedObject != null) {
                macStream.write(buildTlv(encryptedTag, encryptedObject))
            }
            macStream.write(buildTlv(0x99, do99))

            val expectedMac = PaceProtocol.computeCmac(kMac, padIso7816(macStream.toByteArray()))
            if (!MessageDigest.isEqual(expectedMac, do8e)) {
                throw CardProtocolException("Fallo de integridad criptográfica: MAC de Secure Messaging no coincide")
            }
        }

        // Some cards repeat an authenticated application error outside DO99 instead of 9000.
        // Accept this only AFTER MAC verification and only when both statuses agree.
        val outerSw = ((rawSmResponse[rawSmResponse.size - 2].toInt() and 255) shl 8) or
            (rawSmResponse.last().toInt() and 255)
        val authenticatedSw = ((do99[0].toInt() and 255) shl 8) or (do99[1].toInt() and 255)
        if (outerSw != 0x9000 && outerSw != authenticatedSw) {
            throw CardProtocolException(
                "Estados de respuesta segura inconsistentes: externo=%04X, DO99 autenticado=%04X".format(outerSw, authenticatedSw), outerSw)
        }
        if (outerSw != 0x9000) {
            logger.logInfo("SM: estado externo %04X coincide con DO99; MAC verificado.".format(outerSw))
        }

        // Descifrar datos si DO87 está presente
        var plainData = byteArrayOf()
        if (encryptedObject != null) {
            val offset = if (do85 != null) 0 else 1
            if (encryptedObject.size < 16 + offset || (offset == 1 && encryptedObject[0] != 0x01.toByte()) || (encryptedObject.size - offset) % 16 != 0) {
                throw CardProtocolException("Objeto cifrado de respuesta inválido")
            }
            val ciphertext = encryptedObject.copyOfRange(offset, encryptedObject.size)
            val iv = computeIv()
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kEnc, "AES"), IvParameterSpec(iv))
            val decryptedPadded = cipher.doFinal(ciphertext)
            plainData = unpadIso7816(decryptedPadded)
        }

        return plainData + do99
    }

    private fun buildTlv(tag: Int, value: ByteArray): ByteArray {
        val lenBytes = if (value.size < 0x80) {
            byteArrayOf(value.size.toByte())
        } else if (value.size <= 0xFF) {
            byteArrayOf(0x81.toByte(), value.size.toByte())
        } else {
            byteArrayOf(0x82.toByte(), ((value.size shr 8) and 0xFF).toByte(), (value.size and 0xFF).toByte())
        }
        return byteArrayOf(tag.toByte()) + lenBytes + value
    }

    private fun parseTlvObjects(data: ByteArray): Map<Int, ByteArray> {
        val map = mutableMapOf<Int, ByteArray>()
        var offset = 0
        while (offset < data.size) {
            val tag = data[offset++].toInt() and 0xFF
            if (offset >= data.size) throw CardProtocolException("TLV seguro truncado")

            val firstLen = data[offset++].toInt() and 0xFF
            val length = when {
                firstLen == 0x82 -> {
                    if (offset + 1 >= data.size) throw CardProtocolException("Longitud TLV truncada")
                    val l = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
                    offset += 2
                    l
                }
                firstLen == 0x81 -> {
                    if (offset >= data.size) throw CardProtocolException("Longitud TLV truncada")
                    val l = data[offset].toInt() and 0xFF
                    offset += 1
                    l
                }
                firstLen in 0..0x7F -> firstLen
                else -> throw CardProtocolException("Longitud TLV no soportada")
            }

            if (length > data.size - offset || map.containsKey(tag)) throw CardProtocolException("TLV truncado o duplicado")
            val end = offset + length
            map[tag] = data.copyOfRange(offset, end)
            offset = end
        }
        return map
    }
}
