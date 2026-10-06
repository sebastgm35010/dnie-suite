package pe.gob.reniec.dnie.ui.diagnostics

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import pe.gob.reniec.dnie.ui.theme.DnieTheme

class DiagnosticsActivity : ComponentActivity() {
    private val model: DiagnosticsViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { val state by model.state.collectAsStateWithLifecycle()
            DnieTheme { DiagnosticsScreen(state, model::filter, model::older, model::newest, model::clear, model::export) { finish() } }
        }
    }
    override fun onResume() { super.onResume(); model.start() }
    override fun onPause() { model.stop(); super.onPause() }
}
