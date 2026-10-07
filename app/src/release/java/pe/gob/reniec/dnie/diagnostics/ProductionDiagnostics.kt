package pe.gob.reniec.dnie.diagnostics
import android.app.Application
import android.view.Window
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow
object AppDiagnostics { fun initialize(application: Application) { DiagnosticLog.install(null) } }
object AppFlowDiagnostics { fun <T> observe(owner: LifecycleOwner, category: String, state: StateFlow<T>, project: (T) -> String) {} }
class UiFrameDiagnostics(window: Window) : AutoCloseable { fun onRoute(value: String?) {}; override fun close() {} }
