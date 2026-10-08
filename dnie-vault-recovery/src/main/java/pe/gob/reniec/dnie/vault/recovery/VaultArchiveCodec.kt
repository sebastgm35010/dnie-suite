package pe.gob.reniec.dnie.vault.recovery

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import pe.gob.reniec.dnie.vault.VaultContainer
import java.io.*
import java.security.SecureRandom

/** Self-contained password archive, independent of certificate renewal. Fixed bounded KDF. */
class VaultArchiveCodec {
    fun seal(input: InputStream, output: OutputStream, password: CharArray, check: () -> Unit = {}) {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val context = ByteArray(32).also(random::nextBytes)
        val header = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).apply {
            write(MAGIC); writeInt(2); writeInt(RecoveryKdf.MEMORY_KIB)
            writeInt(RecoveryKdf.PASSES); writeInt(RecoveryKdf.LANES); write(salt); write(context)
        } }.toByteArray()
        check()
        val key = RecoveryKdf.derive(password, salt, context, "dnie:archive:v2")
        try {
            output.write(header); check()
            AesGcmHkdfStreaming(key, "HmacSha256", 32, 1048576, 0)
                .newEncryptingStream(NonClosingOutput(output), header).use {
                    VaultContainer.copyBounded(input, it, check, MAX_BYTES)
                }
            check()
        } finally { key.fill(0) }
    }

    /** Output must be private staging; no publication until EOF authenticates. */
    fun open(input: InputStream, output: OutputStream, password: CharArray, check: () -> Unit = {}) {
        val header = ByteArray(72).also { DataInputStream(input).readFully(it) }
        val data = DataInputStream(ByteArrayInputStream(header))
        require(ByteArray(8).also(data::readFully).contentEquals(MAGIC) && data.readInt() == 2) {
            "No es un respaldo completo de bóveda compatible."
        }
        require(data.readInt() == RecoveryKdf.MEMORY_KIB && data.readInt() == RecoveryKdf.PASSES &&
            data.readInt() == RecoveryKdf.LANES) { "Perfil de contraseña no compatible." }
        val salt = ByteArray(16).also(data::readFully)
        val context = ByteArray(32).also(data::readFully)
        check()
        val key = RecoveryKdf.derive(password, salt, context, "dnie:archive:v2")
        try {
            check()
            AesGcmHkdfStreaming(key, "HmacSha256", 32, 1048576, 0).newDecryptingStream(input, header).use {
                VaultContainer.copyBounded(it, output, check, MAX_BYTES)
            }
            check()
        } finally { key.fill(0) }
    }

    private class NonClosingOutput(output: OutputStream) : FilterOutputStream(output) {
        override fun write(bytes: ByteArray, offset: Int, length: Int) { out.write(bytes, offset, length) }
        override fun close() { flush() }
    }
    companion object {
        private val MAGIC = "DNIEBKP2".toByteArray(Charsets.US_ASCII)
        const val MAX_BYTES = 16L * 1024 * 1024 * 1024
    }
}
