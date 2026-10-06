package pe.gob.reniec.dnie.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult
import pe.gob.reniec.dnie.ui.certificates.CertificatesViewModel
import pe.gob.reniec.dnie.ui.certificates.CertificatesUiState

@OptIn(ExperimentalCoroutinesApi::class)
class CertificatesSessionOwnershipTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private class Reader : DnieTransceiverInterface {
        val selection = CompletableDeferred<Result<Unit>>()
        var closes = 0
        var reads = 0
        override suspend fun selectPkiApplet(): Result<Unit> = selection.await()
        override suspend fun readCertificate(type: CertificateType): Result<ByteArray> {
            reads++; return Result.failure(IllegalStateException("Synthetic failure"))
        }
        override suspend fun verifyPin(pin: String): Result<PinVerificationResult> = error("Forbidden")
        override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> = error("Forbidden")
        override fun close() { closes++ }
    }

    @Test fun cancellationBeforeQueuedWorkReleasesOwnerAndDoesNotRead() = runTest(dispatcher) {
        val model = CertificatesViewModel(dispatcher)
        val reader = Reader()
        model.onCardDetected(reader)
        assertTrue(model.busy)
        model.cancelReading()
        advanceUntilIdle()
        assertFalse(model.busy)
        assertTrue(reader.closes >= 1)
        assertEquals(0, reader.reads)
        assertEquals(CertificatesUiState.Idle, model.uiState.value)
    }

    @Test fun secondTagIsClosedAndNeverStartsAnotherSelection() = runTest(dispatcher) {
        val model = CertificatesViewModel(dispatcher)
        val first = Reader()
        val second = Reader()
        model.onCardDetected(first)
        model.onCardDetected(second)
        advanceUntilIdle()
        assertEquals(1, second.closes)
        assertTrue(model.busy)
        model.cancelReading()
        advanceUntilIdle()
        assertFalse(model.busy)
    }
}
