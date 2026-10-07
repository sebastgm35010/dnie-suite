package pe.gob.reniec.dnie.ui.bridge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings
@Composable fun BridgeAdvancedSettings(value: BridgeRuntimeSettings, change: (BridgeRuntimeSettings) -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("Configuración avanzada") }, text = {
        Column {
            Text("Espera al conectar con la PC")
            Row { listOf(5, 15, 30).forEach { seconds -> FilterChip(value.connectTimeoutMs == seconds * 1000,
                { change(value.copy(connectTimeoutMs = seconds * 1000)) }, label = { Text("${seconds}s") }) } }
            Text("Inactividad del canal con la PC")
            Row { listOf(60, 120, 300).forEach { seconds -> FilterChip(value.readTimeoutMs == seconds * 1000,
                { change(value.copy(readTimeoutMs = seconds * 1000)) }, label = { Text("${seconds}s") }) } }
            Text("Espera de respuesta NFC")
            Row { listOf(5, 10, 20, 30).forEach { seconds -> FilterChip(value.nfcTimeoutMs == seconds * 1000,
                { change(value.copy(nfcTimeoutMs = seconds * 1000)) }, label = { Text("${seconds}s") }) } }
            Text("Se aplican a la próxima sesión. El host también puede cerrar por su límite de inactividad. Recomendado: 15 / 120 / 10 segundos.")
            TextButton(onClick = { change(BridgeRuntimeSettings()) }) { Text("Restablecer valores") }
        }
    }, confirmButton = { TextButton(close) { Text("Listo") } })
}
