package pe.gob.reniec.dnie.domain.signing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class CancellableSigningInputTest {
    @Test fun cancelledBatchStopsReadingBeforeConsumingMoreDocumentBytes() {
        val job = Job()
        val original = ByteArrayInputStream(ByteArray(128))
        val input = CancellableSigningInput(original, job)
        assertEquals(16, input.read(ByteArray(16)))
        job.cancel()
        try {
            input.read(ByteArray(16))
            fail("Debe cancelar la lectura")
        } catch (_: CancellationException) {}
        assertEquals(112, original.available())
    }
}
