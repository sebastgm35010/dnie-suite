package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.crypto.PaceProtocol
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** PICC-side SM endpoint. Keys and SSC belong solely to the PC-facing session. */
internal class TerminalSecureMessaging(enc: ByteArray, mac: ByteArray, private val contextId: String = "synthetic") : AutoCloseable {
    private val enc = enc.copyOf()
    private val mac = mac.copyOf()
    private val ssc = ByteArray(16)
    private var valid = true
    private var pending = false
    private var berTlvResponse = false
    private fun next() { for (i in ssc.indices.reversed()) { ssc[i]++; if (ssc[i] != 0.toByte()) break } }
    private fun crypt(data: ByteArray, operation: Int): ByteArray {
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(enc, "AES"))
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(operation, SecretKeySpec(enc, "AES"), IvParameterSpec(ecb.doFinal(ssc)))
        return cipher.doFinal(data)
    }
    fun decode(bytes: ByteArray): ByteArray {
        check(valid && !pending)
        try {
            val c = CommandApdu.fromBytes(bytes)
            require((c.cla.toInt() and 255) in setOf(0x0c, 0x1c))
            val fields = SmTlv.parse(c.data)
            require(!(fields.containsKey(0x85) && fields.containsKey(0x87)))
            require(fields.keys.toList() == listOfNotNull(if (fields.containsKey(0x85)) 0x85 else if (fields.containsKey(0x87)) 0x87 else null,
                if (fields.containsKey(0x97)) 0x97 else null, 0x8e))
            require(fields[0x8e]?.size == 8)
            next()
            val covered = SmTlv.pad(bytes.copyOfRange(0, 4)) +
                fields.filterKeys { it != 0x8e }.flatMap { (tag, value) -> SmTlv.encode(tag, value).toList() }.toByteArray()
            val expected = PaceProtocol.computeCmac(mac, SmTlv.pad(ssc + covered))
            if (!MessageDigest.isEqual(expected, fields[0x8e])) {
                // Diagnostics only: do not adopt a candidate, decrypt or forward the command.
                val n = java.math.BigInteger(1,ssc).takeIf { it.bitLength() <= 62 }?.toLong()
                val candidates = (listOf(0L,1L,2L) + (n?.let { value -> listOf(-4L,-2L,-1L,1L,2L,4L).map { value+it } } ?: emptyList()))
                    .filter { it >= 0 && it != n }.distinct()
                val matches = candidates.filter { value ->
                    val probe = ByteArray(16)
                    var remaining = value
                    for (i in 15 downTo 8) { probe[i]=remaining.toByte(); remaining=remaining ushr 8 }
                    val token = PaceProtocol.computeCmac(mac,SmTlv.pad(probe+covered))
                    try { MessageDigest.isEqual(token,fields[0x8e]) } finally { token.fill(0);probe.fill(0) }
                }
                throw TerminalSmIntegrityException(contextId,n,matches)
            }
            val data = fields[0x87]?.let {
                require(it.size >= 17 && it[0] == 1.toByte() && (it.size - 1) % 16 == 0)
                SmTlv.unpad(crypt(it.copyOfRange(1, it.size), Cipher.DECRYPT_MODE))
            } ?: fields[0x85]?.let {
                // BER-TLV plaintext is encrypted without the DO87 padding-indicator byte.
                require(it.isNotEmpty() && it.size % 16 == 0)
                SmTlv.unpad(crypt(it, Cipher.DECRYPT_MODE))
            } ?: byteArrayOf()
            val le = fields[0x97]?.let {
                require(it.size in 1..2)
                val n = it.fold(0) { a, b -> (a shl 8) or (b.toInt() and 255) }
                if (n == 0) if (it.size == 1) 256 else 65536 else n
            }
            pending = true
            berTlvResponse = (c.ins.toInt() and 1) != 0
            return CommandApdu((c.cla.toInt() and 0xf3).toByte(), c.ins, c.p1, c.p2, data, le,
                extended = fields[0x97]?.size == 2 || data.size > 255).toByteArray().also { data.fill(0) }
        } catch (failure: Exception) { close(); throw failure }
    }
    fun encode(response: ByteArray): ByteArray {
        check(valid && pending)
        try {
            require(response.size in 2..16386)
            next()
            val body = response.copyOfRange(0, response.size - 2)
            val objects = (if (body.isEmpty()) byteArrayOf() else SmTlv.encode(if (berTlvResponse) 0x85 else 0x87,
                (if (berTlvResponse) byteArrayOf() else byteArrayOf(1)) + crypt(SmTlv.pad(body), Cipher.ENCRYPT_MODE))) +
                SmTlv.encode(0x99, response.copyOfRange(response.size - 2, response.size))
            body.fill(0)
            val token = PaceProtocol.computeCmac(mac, SmTlv.pad(ssc + objects))
            pending = false
            return objects + SmTlv.encode(0x8e, token) + byteArrayOf(0x90.toByte(), 0)
        } catch (failure: Exception) { close(); throw failure }
    }
    override fun close() { valid = false; pending = false; enc.fill(0); mac.fill(0); ssc.fill(0) }
}

/** Strict bounded BER objects; reject duplicates, indefinite and nonminimal lengths. */
internal object SmTlv {
    fun parse(bytes: ByteArray): LinkedHashMap<Int, ByteArray> {
        require(bytes.size <= 16384)
        val result = linkedMapOf<Int, ByteArray>()
        var offset = 0
        while (offset < bytes.size) {
            val tag = bytes[offset++].toInt() and 255
            require(offset < bytes.size && !result.containsKey(tag))
            var length = bytes[offset++].toInt() and 255
            if (length >= 128) {
                val count = length and 127
                require(count in 1..2 && offset + count <= bytes.size && bytes[offset] != 0.toByte())
                length = 0
                repeat(count) { length = (length shl 8) or (bytes[offset++].toInt() and 255) }
                require(length >= 128 && (count == 1 || length >= 256))
            }
            require(length <= bytes.size - offset)
            result[tag] = bytes.copyOfRange(offset, offset + length); offset += length
        }
        return result
    }
    fun encode(tag: Int, value: ByteArray): ByteArray {
        require(value.size <= 16384)
        val length = when { value.size < 128 -> byteArrayOf(value.size.toByte())
            value.size < 256 -> byteArrayOf(0x81.toByte(), value.size.toByte())
            else -> byteArrayOf(0x82.toByte(), (value.size shr 8).toByte(), value.size.toByte()) }
        return byteArrayOf(tag.toByte()) + length + value
    }
    fun pad(data: ByteArray) = data.copyOf(data.size + 16 - data.size % 16).also { it[data.size] = 0x80.toByte() }
    fun unpad(data: ByteArray): ByteArray {
        var i = data.size - 1
        while (i >= 0 && data[i] == 0.toByte()) i--
        require(i >= 0 && data[i] == 0x80.toByte())
        return data.copyOf(i)
    }
}
