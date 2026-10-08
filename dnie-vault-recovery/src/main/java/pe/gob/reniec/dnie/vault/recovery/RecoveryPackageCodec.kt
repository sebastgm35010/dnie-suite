package pe.gob.reniec.dnie.vault.recovery

import java.io.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** R1 experimental sidecar. No names, PINs, private card keys or fast password verifier. */
class RecoveryPackageCodec(private val random: SecureRandom = SecureRandom()) {
    fun seal(entries: List<RecoveryEntry>, password: CharArray, identityContext: ByteArray,
             checkCancelled: () -> Unit = {}): ByteArray {
        require(entries.size in 1..MAX_ENTRIES && entries.map { it.id }.toSet().size == entries.size)
        require(identityContext.size == 32)
        checkCancelled()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val body = ByteArray(4 + entries.size * ENTRY_BYTES)
        var key: ByteArray? = null
        try {
            ByteBuffer.wrap(body).apply {
                putInt(entries.size)
                entries.forEach { entry ->
                    checkCancelled()
                    putLong(entry.id.mostSignificantBits); putLong(entry.id.leastSignificantBits)
                    val recipient = entry.recipient(); val hash = entry.fileHash(); val secret = entry.secret()
                    try { put(recipient); put(hash); put(secret) }
                    finally { recipient.fill(0); hash.fill(0); secret.fill(0) }
                }
            }
            val header = header(salt, nonce, identityContext, body.size + 16)
            key = RecoveryKdf.derive(password, salt, identityContext)
            checkCancelled()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            val result = header + cipher.doFinal(body)
            checkCancelled()
            return result
        } finally { body.fill(0); key?.fill(0) }
    }

    fun open(encoded: ByteArray, password: CharArray, checkCancelled: () -> Unit = {}): OpenRecoveryPackage {
        require(encoded.size in HEADER_BYTES + 20..MAX_BYTES) { "Paquete inválido" }
        val input = DataInputStream(ByteArrayInputStream(encoded))
        require(ByteArray(8).also(input::readFully).contentEquals(MAGIC) && input.readInt() == 1)
        // Validate costs before allocating Argon2's memory or executing a password operation.
        require(input.readInt() == RecoveryKdf.MEMORY_KIB && input.readInt() == RecoveryKdf.PASSES &&
            input.readInt() == RecoveryKdf.LANES) { "Perfil KDF no admitido" }
        val salt = ByteArray(16).also(input::readFully)
        val nonce = ByteArray(12).also(input::readFully)
        val context = ByteArray(32).also(input::readFully)
        val size = input.readInt()
        require(size in 20..MAX_ENTRIES * ENTRY_BYTES + 20 && size == encoded.size - HEADER_BYTES)
        checkCancelled()
        val key = RecoveryKdf.derive(password, salt, context)
        var plain: ByteArray? = null
        val entries = mutableListOf<RecoveryEntry>()
        try {
            checkCancelled()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(encoded.copyOfRange(0, HEADER_BYTES))
            plain = cipher.doFinal(encoded, HEADER_BYTES, size)
            val decoded = DataInputStream(ByteArrayInputStream(plain))
            val count = decoded.readInt()
            require(count in 1..MAX_ENTRIES && plain.size == 4 + count * ENTRY_BYTES)
            val seen = mutableSetOf<UUID>()
            repeat(count) {
                checkCancelled()
                val id = UUID(decoded.readLong(), decoded.readLong())
                require(seen.add(id)) { "Entradas duplicadas" }
                val recipient = ByteArray(32).also(decoded::readFully)
                val hash = ByteArray(32).also(decoded::readFully)
                val secret = ByteArray(32).also(decoded::readFully)
                try { entries += RecoveryEntry(id, recipient, hash, secret) }
                finally { recipient.fill(0); hash.fill(0); secret.fill(0) }
            }
            checkCancelled()
            return OpenRecoveryPackage(entries)
        } catch (failure: Throwable) { entries.forEach { it.close() }; throw failure }
        finally { key.fill(0); plain?.fill(0) }
    }

    private fun header(salt: ByteArray, nonce: ByteArray, context: ByteArray, cipherSize: Int): ByteArray =
        ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).apply {
            write(MAGIC); writeInt(1); writeInt(RecoveryKdf.MEMORY_KIB)
            writeInt(RecoveryKdf.PASSES); writeInt(RecoveryKdf.LANES)
            write(salt); write(nonce); write(context); writeInt(cipherSize)
        } }.toByteArray()

    companion object {
        internal val MAGIC = "DNIERCP1".toByteArray(Charsets.US_ASCII)
        const val MAX_ENTRIES = 1000
        internal const val ENTRY_BYTES = 112
        internal const val HEADER_BYTES = 88
        const val MAX_BYTES = HEADER_BYTES + 20 + MAX_ENTRIES * ENTRY_BYTES
    }
}
