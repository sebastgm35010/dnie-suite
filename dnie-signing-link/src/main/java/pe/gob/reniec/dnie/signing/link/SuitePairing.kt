package pe.gob.reniec.dnie.signing.link

import java.util.UUID

/** Persistent trust, not a signature authorization. Store encrypted and never log encode(). */
class SuitePairing(val id: UUID, val pc: String, fingerprint: ByteArray, token: ByteArray) : AutoCloseable {
    val fingerprint = fingerprint.copyOf()
    val token = token.copyOf()
    init { require(SigningProvision.privateAddress(pc) && fingerprint.size == 32 && token.size == 32) }
    fun encode() = "DNISUITE2|$id|$pc|${hex(fingerprint)}|${hex(token)}"
    fun provision() = SigningProvision(id, pc, System.currentTimeMillis() + 300_000, 1, fingerprint, token)
    fun fingerprintText() = fingerprint.joinToString(":") { "%02X".format(it.toInt() and 255) }
    override fun close() { fingerprint.fill(0); token.fill(0) }
    override fun toString() = "SuitePairing($pc)"
    companion object {
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        fun parse(text: String): SuitePairing {
            require(text.length <= 1024)
            val parts = text.trim().split('|')
            require(parts.size == 5 && parts[0] == "DNISUITE2")
            fun decode(s: String): ByteArray {
                require(s.length == 64 && s.all { it in "0123456789abcdefABCDEF" })
                return s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
            return SuitePairing(UUID.fromString(parts[1]), parts[2], decode(parts[3]), decode(parts[4]))
        }
    }
}
