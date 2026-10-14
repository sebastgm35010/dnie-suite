package pe.gob.reniec.dnie.domain.feedback

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingFeedbackEventsTest {
    @Test fun eventsDoNotReplayAfterRecreationAndPartialBatchesAreErrors()=runTest {
        val events=ReadingFeedbackEvents();events.report(ReadingCue.SUCCESS)
        val received=mutableListOf<ReadingCue>()
        val collector=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {events.flow.collect {received+=it}}
        assertTrue(received.isEmpty())
        events.report(ReadingCue.PROGRESS);events.report(ReadingOutcome.batch(2,3));runCurrent()
        assertEquals(listOf(ReadingCue.PROGRESS,ReadingCue.ERROR),received)
        assertEquals(ReadingCue.SUCCESS,ReadingOutcome.batch(3,3));assertEquals(ReadingCue.ERROR,ReadingOutcome.batch(0,0));collector.cancel()
    }
}
