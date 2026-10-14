package pe.gob.reniec.dnie.domain.signing

import kotlinx.coroutines.ensureActive
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Synchronous PDF/CMS libraries still honor the caller's cancellation and original-file integrity during IO. */
internal class CancellableSigningInput(
    input: InputStream,
    private val context: CoroutineContext = EmptyCoroutineContext,
    private val expectedSize: Long = -1L,
    private val expectedSha256: ByteArray? = null
) : FilterInputStream(input) {
    init {
        require(expectedSha256 == null || expectedSha256.size == 32) { "El resumen esperado debe tener 32 bytes" }
    }
    private val digest = if (expectedSha256 != null) MessageDigest.getInstance("SHA-256") else null
    private var bytesRead = 0L
    private var computedHash: ByteArray? = null
    private var verified = false

    override fun read(): Int {
        context.ensureActive()
        val value = super.read()
        if (value >= 0) {
            bytesRead++
            if (expectedSize >= 0L && bytesRead > expectedSize)
                throw IOException("El archivo original cambió durante el proceso de firma")
            digest?.update(value.toByte())
        } else {
            verifyComplete()
        }
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        context.ensureActive()
        val count = super.read(buffer, offset, length)
        if (count > 0) {
            bytesRead += count
            if (expectedSize >= 0L && bytesRead > expectedSize)
                throw IOException("El archivo original cambió durante el proceso de firma")
            digest?.update(buffer, offset, count)
        } else if (count == -1) {
            verifyComplete()
        }
        return count
    }

    fun verifyComplete() {
        if (verified) return
        if (expectedSize >= 0L && bytesRead != expectedSize)
            throw IOException("El archivo original cambió durante el proceso de firma")
        if (digest != null && expectedSha256 != null) {
            val actual = computedHash ?: digest.digest().also { computedHash = it }
            if (!MessageDigest.isEqual(actual, expectedSha256))
                throw IOException("El archivo original cambió durante el proceso de firma")
        }
        verified = true
    }
}
