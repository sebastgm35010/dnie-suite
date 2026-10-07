package pe.gob.reniec.dnie.ui.certificates

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.data.certificates.CertificateValidationRepository
import pe.gob.reniec.dnie.validation.*
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class CertificateValidationViewModelTest {
    private fun report() = CertificateValidationReport(CertificateAssessment(true,emptyList()),
        RevocationAssessment(RevocationStatus.UNKNOWN,"Offline"),TrustStatus.UNKNOWN,Date())
    @Test fun changingCertificateCancelsOldQueryAndPublishesOnlyNewFingerprint() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val first = CompletableDeferred<CertificateValidationReport>()
            val model = CertificateValidationViewModel(CertificateValidationRepository { der, _ ->
                if(der[0].toInt()==1) first.await() else report()
            })
            model.check(byteArrayOf(1),"first",true); runCurrent()
            model.check(byteArrayOf(2),"second",true); runCurrent()
            first.complete(report()); runCurrent()
            assertEquals("second",model.state.value.fingerprint)
            assertFalse(model.state.value.checking)
            assertEquals(RevocationStatus.UNKNOWN,model.state.value.report!!.revocation.status)
            model.clear()
        } finally { Dispatchers.resetMain() }
    }
    @Test fun clearDoesNotKeepStaleRevocationResult() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val pending = CompletableDeferred<CertificateValidationReport>()
            val model = CertificateValidationViewModel(CertificateValidationRepository { _, _ -> pending.await() })
            model.check(byteArrayOf(1),"first",true); runCurrent(); model.clear()
            pending.complete(report()); runCurrent()
            assertEquals(CertificateValidationUiState(),model.state.value)
        } finally { Dispatchers.resetMain() }
    }
}
