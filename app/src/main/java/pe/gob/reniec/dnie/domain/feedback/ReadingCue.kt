package pe.gob.reniec.dnie.domain.feedback

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class ReadingCue { START, PROGRESS, SUCCESS, ERROR }
/** Event stream, not replayed on recomposition or activity recreation; never blocks NFC. */
class ReadingFeedbackEvents {
    private val events=MutableSharedFlow<ReadingCue>(extraBufferCapacity=8,onBufferOverflow=BufferOverflow.DROP_OLDEST)
    val flow=events.asSharedFlow()
    fun report(cue: ReadingCue) {events.tryEmit(cue)}
}
object ReadingOutcome {
    fun batch(signed: Int,expected: Int): ReadingCue = if(expected>0 && signed==expected) ReadingCue.SUCCESS else ReadingCue.ERROR
}
