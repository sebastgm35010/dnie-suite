package pe.gob.reniec.dnie.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import pe.gob.reniec.dnie.icao.*
import pe.gob.reniec.dnie.ui.identity.IdentityViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class IdentityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var vm: IdentityViewModel
    @Before fun setup() { Dispatchers.setMain(dispatcher); vm = IdentityViewModel() }
    @After fun teardown() { vm.cancelReading(); Dispatchers.resetMain() }
    private fun awaitCard() { vm.requestReading(); vm.confirmCan("123456") }

    @Test fun onlyAcceptsSixAsciiDigitsAndDoesNotExposeCanAfterCancel() {
        vm.requestReading()
        vm.confirmCan("１２３４５６")
        assertFalse(vm.uiState.value.awaitingCard)
        vm.confirmCan("123456")
        assertEquals("123456", vm.requestedCan)
        vm.cancelReading()
        assertNull(vm.requestedCan)
    }
    @Test fun ignoresUnexpectedTagsAndClosesUnusedReader() {
        val reader = FakeReader()
        vm.onCardDetected(reader)
        assertTrue(reader.closed)
        assertFalse(vm.uiState.value.reading)
    }
    @Test fun preservesPartialResultAndDisplaysInterruption() = runTest(dispatcher) {
        awaitCard()
        val partial = IcaoDocument(blocks = mapOf(IcaoFile.DG1 to IcaoBlock(IcaoFile.DG1, BlockStatus.AVAILABLE)),
            completed = true, sessionMessage = "NFC perdido")
        val reader = FakeReader(partial)
        vm.onCardDetected(reader)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(partial, vm.uiState.value.document)
        assertEquals("NFC perdido", vm.uiState.value.message)
        assertTrue(reader.closed)
        assertFalse(vm.uiState.value.reading)
        assertNull(vm.requestedCan)
    }
    @Test fun cancelRetainsCompletedBlocksAndIgnoresStaleCallbacks() = runTest(dispatcher) {
        awaitCard()
        val gate = CompletableDeferred<IcaoDocument>()
        var callback: ((IcaoDocument) -> Unit)? = null
        var closed = false
        val reader = object : IcaoReader {
            override suspend fun read(onProgress: (IcaoDocument) -> Unit): IcaoDocument {
                callback = onProgress
                onProgress(IcaoDocument().copy(blocks = IcaoDocument().blocks +
                    (IcaoFile.DG1 to IcaoBlock(IcaoFile.DG1, BlockStatus.AVAILABLE))))
                return gate.await()
            }
            override fun close() { closed = true }
        }
        vm.onCardDetected(reader)
        dispatcher.scheduler.runCurrent()
        vm.cancelReading()
        callback?.invoke(IcaoDocument())
        assertTrue(closed)
        assertEquals(BlockStatus.AVAILABLE, vm.uiState.value.document?.blocks?.get(IcaoFile.DG1)?.status)
        assertEquals(BlockStatus.INTERRUPTED, vm.uiState.value.document?.blocks?.get(IcaoFile.DG2)?.status)
        assertFalse(vm.uiState.value.reading)
    }
    @Test fun completedReadCanBeClearedFromMemory() = runTest(dispatcher) {
        awaitCard()
        vm.onCardDetected(FakeReader(IcaoDocument(completed = true)))
        dispatcher.scheduler.advanceUntilIdle()
        vm.clear()
        assertNull(vm.uiState.value.document)
    }
    @Test fun repeatedTagDoesNotStartAnotherSession() = runTest(dispatcher) {
        awaitCard()
        val gate = CompletableDeferred<IcaoDocument>()
        val first = object : IcaoReader {
            override suspend fun read(onProgress: (IcaoDocument) -> Unit) = gate.await()
        }
        vm.onCardDetected(first)
        val second = FakeReader()
        vm.onCardDetected(second)
        assertTrue(second.closed)
        gate.complete(IcaoDocument(completed = true))
        dispatcher.scheduler.advanceUntilIdle()
    }
    private class FakeReader(private val result: IcaoDocument = IcaoDocument()) : IcaoReader {
        var closed = false
        override suspend fun read(onProgress: (IcaoDocument) -> Unit): IcaoDocument {
            onProgress(result)
            return result
        }
        override fun close() { closed = true }
    }
}
