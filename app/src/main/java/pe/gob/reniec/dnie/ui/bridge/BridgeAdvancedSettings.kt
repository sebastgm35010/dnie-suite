package pe.gob.reniec.dnie.ui.bridge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings
@Composable fun BridgeAdvancedSettings(value: BridgeRuntimeSettings, change: (BridgeRuntimeSettings) -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("Configuración avanzada") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Espera al conectar con la PC")
            Row { listOf(5, 15, 30).forEach { seconds -> FilterChip(value.connectTimeoutMs == seconds * 1000,
                { change(value.copy(connectTimeoutMs = seconds * 1000)) }, label = { Text("${seconds}s") }) } }
            Text("Espera de respuesta NFC")
            Row { listOf(5, 10, 20, 30).forEach { seconds -> FilterChip(value.nfcTimeoutMs == seconds * 1000,
                { change(value.copy(nfcTimeoutMs = seconds * 1000)) }, label = { Text("${seconds}s") }) } }
            Text("No hay cierre por inactividad. Estos límites solo afectan a la conexión y a cada respuesta NFC.")
            TextButton(onClick = { change(BridgeRuntimeSettings()) }) { Text("Restablecer valores") }
        }
    }, confirmButton = { TextButton(close) { Text("Listo") } })
}
