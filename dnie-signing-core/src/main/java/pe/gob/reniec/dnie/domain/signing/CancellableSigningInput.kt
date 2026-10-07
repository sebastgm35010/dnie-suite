package pe.gob.reniec.dnie.domain.signing

import kotlinx.coroutines.ensureActive
import java.io.FilterInputStream
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

/** Synchronous PDF/CMS libraries still honor the caller's cancellation during IO. */
internal class CancellableSigningInput(input: InputStream, private val context: CoroutineContext) : FilterInputStream(input) {
    override fun read(): Int {
        context.ensureActive()
        return super.read()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        context.ensureActive()
        return super.read(buffer, offset, length)
    }
}
