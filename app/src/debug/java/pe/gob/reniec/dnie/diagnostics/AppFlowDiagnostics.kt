package pe.gob.reniec.dnie.diagnostics

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Projects private UI states to coarse operation stages before any logging occurs. */
object AppFlowDiagnostics {
    fun <T> observe(owner: LifecycleOwner, category: String, state: StateFlow<T>, project: (T) -> String) {
        owner.lifecycleScope.launch { state.map(project).distinctUntilChanged().collect {
            DiagnosticLog.event(category, "state_changed", "state" to it, "layer" to "coordination")
        } }
    }
}
