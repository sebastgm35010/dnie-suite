package pe.gob.reniec.dnie.development
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import pe.gob.reniec.dnie.ui.bridge.BridgeUiState
@Composable fun BridgeDevelopmentSettings(state: BridgeUiState, windows: (Boolean) -> Unit, translate: (Boolean) -> Unit, export: () -> Unit, clear: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bridge-development", android.content.Context.MODE_PRIVATE) }
    var trace by remember { mutableStateOf(prefs.getBoolean("trace", false)) }
    val editable = !state.busy && !state.armed
    Column {
        Text("Desarrollo · perfiles y captura APDU")
        Row { Switch(state.windowsPace, windows, enabled = editable); Text("PACE en Windows") }
        if (!state.windowsPace) Row { Switch(state.translatePace, translate, enabled = editable); Text("Traducir PACE del middleware") }
        Row { Switch(trace, { trace = it; prefs.edit().putBoolean("trace", it).apply() }, enabled = editable); Text("Capturar APDU en próximas sesiones") }
        Text("La captura puede contener datos personales y certificados. PIN oculto. Al desactivarla no se borra el historial.")
        if (state.traceAvailable) { OutlinedButton(export, enabled = editable) { Text("Exportar APDU") }; TextButton(clear, enabled = editable) { Text("Borrar APDU") } }
    }
}
