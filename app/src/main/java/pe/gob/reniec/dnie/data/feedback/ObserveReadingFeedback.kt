package pe.gob.reniec.dnie.data.feedback

import android.content.Context
import androidx.lifecycle.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import pe.gob.reniec.dnie.domain.feedback.ReadingCue

fun LifecycleOwner.observeReadingFeedback(context: Context,events: Flow<ReadingCue>) {
    lifecycleScope.launch {repeatOnLifecycle(Lifecycle.State.STARTED) {events.collect {ReadingFeedback.play(context,it)}}}
}
