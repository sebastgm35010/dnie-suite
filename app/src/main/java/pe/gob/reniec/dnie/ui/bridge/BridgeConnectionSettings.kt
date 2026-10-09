package pe.gob.reniec.dnie.ui.bridge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pe.gob.reniec.dnie.bridge.BridgeConnectionMethod
@Composable fun BridgeConnectionSettings(method: BridgeConnectionMethod, enabled: Boolean, select: (BridgeConnectionMethod) -> Unit) {
    Column {
        BridgeConnectionMethod.entries.forEach { value ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(method == value, { select(value) }, enabled = enabled); Text(value.title, Modifier.weight(1f))
            }
        }
        Text(when (method) {
            BridgeConnectionMethod.TAILSCALE -> "Activa Tailscale en el teléfono y la PC."
            BridgeConnectionMethod.LOCAL_NETWORK -> "Conecta ambos equipos a la misma Wi-Fi y escanea el QR de la PC."
            BridgeConnectionMethod.USB_NETWORK -> "Conecta el cable e inicia USB directo en la PC. Acepta el accesorio en Android."
        }, style = MaterialTheme.typography.bodySmall)
    }
}
