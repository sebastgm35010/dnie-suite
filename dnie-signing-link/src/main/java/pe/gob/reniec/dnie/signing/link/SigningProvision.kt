package pe.gob.reniec.dnie.signing.link

import java.util.UUID

/** One job, transferred by a user-controlled QR/file. Never log or stringify credentials. */
class SigningProvision(val sessionId: UUID, val pcAddress: String, val expiresAt: Long,
                       val count: Int, fingerprint: ByteArray, token: ByteArray) : AutoCloseable {
    val fingerprint = fingerprint.copyOf()
    val token = token.copyOf()
    init {
        require(privateAddress(pcAddress) && count in 1..50 && expiresAt > 0)
        require(fingerprint.size == 32 && token.size == 32)
    }
    fun encode(): String = listOf("DNISUITE1", sessionId, pcAddress, expiresAt, count,
        hex(fingerprint), hex(token)).joinToString("|")
    fun fingerprintText(): String = hex(fingerprint).chunked(2).joinToString(":")
    override fun close() { fingerprint.fill(0); token.fill(0) }
    override fun toString() = "SigningProvision(count=$count)"
    companion object {
        const val PHONE_PORT = 39446
        fun parse(text: String, now: Long = System.currentTimeMillis()): SigningProvision {
            require(text.length <= 1024)
            val parts = text.trim().split('|')
            require(parts.size == 7 && parts[0] == "DNISUITE1")
            val expiry = parts[3].toLong()
            require(expiry > now && expiry - now <= 600_000)
            fun bytes(value: String): ByteArray {
                require(value.matches(Regex("[0-9A-Fa-f]{64}")))
                return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
            return SigningProvision(UUID.fromString(parts[1]), parts[2], expiry, parts[4].toInt(), bytes(parts[5]), bytes(parts[6]))
        }
        fun privateAddress(value: String): Boolean {
            if (!value.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))) return false
            val a = value.split('.').map(String::toInt)
            if (a.any { it !in 0..255 }) return false
            return a[0] == 10 || (a[0] == 192 && a[1] == 168) ||
                (a[0] == 172 && a[1] in 16..31) || (a[0] == 100 && a[1] in 64..127)
        }
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02X".format(it) }
    }
}
