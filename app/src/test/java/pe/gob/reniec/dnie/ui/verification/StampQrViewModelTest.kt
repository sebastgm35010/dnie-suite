package pe.gob.reniec.dnie.ui.verification

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.data.qr.StampQrRepository
import pe.gob.reniec.dnie.domain.model.CertificateInfo
import pe.gob.reniec.dnie.pdf.*

@OptIn(ExperimentalCoroutinesApi::class)
class StampQrViewModelTest {
    private val metadata=StampQrMetadata("AB".repeat(32),"1","CD".repeat(32),32)
    private fun payload(preview: Boolean=false)=StampQrCodec.encode(StampQrRecord(metadata.copy(preview=preview),"ANA",null,null,"B"))
    private fun repository(original: suspend () -> StampFileDigest)=object: StampQrRepository {
        override suspend fun original(uri: String)=original()
        override suspend fun certificate(uri: String): CertificateInfo=error("not used")
    }
    @Test fun invalidOrPreviewQrCannotStartFileIo()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var reads=0
            val model=StampQrViewModel(repository { reads++;StampFileDigest(metadata.originalSha256,32) })
            model.scan("https://attacker.invalid");model.original("unused");runCurrent()
            assertNull(model.state.value.record);assertNotNull(model.state.value.message)
            model.scan(payload(true));model.original("unused");runCurrent()
            assertEquals(0,reads);assertNull(model.state.value.original)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun changingQrDiscardsEvidenceEvenIfOldIoIgnoresCancellation()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val waiting=CompletableDeferred<StampFileDigest>()
            val model=StampQrViewModel(repository { withContext(NonCancellable) { waiting.await() } })
            model.scan(payload());model.original("file");runCurrent();assertTrue(model.state.value.busy)
            model.scan(payload(true));waiting.complete(StampFileDigest(metadata.originalSha256,32));runCurrent()
            assertTrue(model.state.value.record!!.metadata.preview);assertNull(model.state.value.original);assertFalse(model.state.value.busy)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun mismatchAndFailureNeverBecomeSuccessAndClearRemovesEverything()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model=StampQrViewModel(repository { StampFileDigest("00".repeat(32),32) })
            model.scan(payload());model.original("file");runCurrent()
            assertEquals(StampQrMatch.DIFFERENT,model.state.value.original)
            model.clear();assertEquals(StampQrUiState(),model.state.value)
            val broken=StampQrViewModel(repository { error("failure") })
            broken.scan(payload());broken.original("file");runCurrent()
            assertNotNull(broken.state.value.message);assertNull(broken.state.value.original);assertFalse(broken.state.value.busy)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun unreadableReplacementFileClearsPriorPositiveMatch()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var fail=false
            val model=StampQrViewModel(repository { if(fail) error("no access") else StampFileDigest(metadata.originalSha256,32) })
            model.scan(payload());model.original("good");runCurrent()
            assertEquals(StampQrMatch.MATCH,model.state.value.original)
            fail=true;model.original("unreadable")
            assertNull(model.state.value.original)
            runCurrent();assertNull(model.state.value.original);assertNotNull(model.state.value.message)
        } finally { Dispatchers.resetMain() }
    }
}
