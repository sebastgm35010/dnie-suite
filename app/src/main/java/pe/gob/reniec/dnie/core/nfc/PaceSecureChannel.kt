package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.crypto.PaceProtocol
import java.io.ByteArrayOutputStream
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
    private val kMac: ByteArray
) : DnieCardChannel {

    private val ssc = ByteArray(16) // Inicializado a 16 ceros en PACE

    override fun transceive(command: ByteArray): ByteArray {
        val cmd = CommandApdu.fromBytes(command)
        val wrappedApdu = wrapCommand(cmd)
        val rawResponse = baseChannel.transceive(wrappedApdu)
        return unwrapResponse(rawResponse)
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
        return data
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
            val content = byteArrayOf(0x01.toByte()) + ciphertext
            do87 = buildTlv(0x87, content)
        }

        var do97: ByteArray? = null
        if (cmd.le != null) {
            val leByte = if (cmd.le == 256) 0x00.toByte() else (cmd.le and 0xFF).toByte()
            do97 = byteArrayOf(0x97.toByte(), 0x01.toByte(), leByte)
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

        // Armar trama APDU de salida: CLA INS P1 P2 Lc Data 00
        val out = ByteArray(4 + 1 + smData.size + 1)
        out[0] = maskedCla
        out[1] = cmd.ins
        out[2] = cmd.p1
        out[3] = cmd.p2
        out[4] = smData.size.toByte()
        System.arraycopy(smData, 0, out, 5, smData.size)
        out[out.size - 1] = 0x00.toByte() // Le = 0x00

        return out
    }

    /**
     * Desencapsula y valida una respuesta APDU protegida con Mensajería Segura (SM).
     */
    fun unwrapResponse(rawSmResponse: ByteArray): ByteArray {
        if (rawSmResponse.size < 2) return rawSmResponse

        // Si la respuesta solo tiene 2 bytes (SW de error puro, ej. 69 82, 6A 82, etc.)
        if (rawSmResponse.size == 2) {
            return rawSmResponse
        }

        incrementSsc()

        val smPayload = rawSmResponse.copyOfRange(0, rawSmResponse.size - 2)
        val tlvMap = parseTlvObjects(smPayload)

        val do87 = tlvMap[0x87]
        val do99 = tlvMap[0x99]
        val do8e = tlvMap[0x8E]

        // Validar integridad MAC si DO8E y DO99 están presentes
        if (do8e != null && do99 != null) {
            val macStream = ByteArrayOutputStream()
            macStream.write(ssc)
            if (do87 != null) {
                macStream.write(buildTlv(0x87, do87))
            }
            macStream.write(buildTlv(0x99, do99))

            val expectedMac = PaceProtocol.computeCmac(kMac, padIso7816(macStream.toByteArray()))
            if (!expectedMac.contentEquals(do8e)) {
                throw CardProtocolException("Fallo de integridad criptográfica: MAC de Secure Messaging no coincide")
            }
        }

        // Descifrar datos si DO87 está presente
        var plainData = byteArrayOf()
        if (do87 != null && do87.isNotEmpty()) {
            val ciphertext = if (do87[0] == 0x01.toByte()) {
                do87.copyOfRange(1, do87.size)
            } else {
                do87
            }
            val iv = computeIv()
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kEnc, "AES"), IvParameterSpec(iv))
            val decryptedPadded = cipher.doFinal(ciphertext)
            plainData = unpadIso7816(decryptedPadded)
        }

        // El Status Word final proviene de DO99 (o de los últimos 2 bytes de la APDU)
        val sw1 = if (do99 != null && do99.size >= 2) do99[0] else rawSmResponse[rawSmResponse.size - 2]
        val sw2 = if (do99 != null && do99.size >= 2) do99[1] else rawSmResponse[rawSmResponse.size - 1]

        return plainData + byteArrayOf(sw1, sw2)
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
            if (offset >= data.size) break

            val firstLen = data[offset++].toInt() and 0xFF
            val length = when {
                firstLen == 0x82 -> {
                    if (offset + 1 >= data.size) break
                    val l = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
                    offset += 2
                    l
                }
                firstLen == 0x81 -> {
                    if (offset >= data.size) break
                    val l = data[offset].toInt() and 0xFF
                    offset += 1
                    l
                }
                else -> firstLen
            }

            val end = minOf(data.size, offset + length)
            map[tag] = data.copyOfRange(offset, end)
            offset = end
        }
        return map
    }
}
