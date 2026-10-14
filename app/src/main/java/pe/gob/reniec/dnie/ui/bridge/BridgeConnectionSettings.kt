package pe.gob.reniec.dnie.ui.bridge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pe.gob.reniec.dnie.bridge.BridgeConnectionMethod
@Composable fun BridgeConnectionSettings(method: BridgeConnectionMethod, enabled: Boolean, select: (BridgeConnectionMethod) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            FilterChip(selected = method != BridgeConnectionMethod.USB_NETWORK,
                onClick = { select(BridgeConnectionMethod.LOCAL_NETWORK) },
                enabled = enabled, label = { Text("Red · Wi-Fi / Tailscale") })
            FilterChip(selected = method == BridgeConnectionMethod.USB_NETWORK,
                onClick = { select(BridgeConnectionMethod.USB_NETWORK) }, enabled = enabled, label = { Text("Cable USB") })
        }
        Text(when (method) {
            BridgeConnectionMethod.TAILSCALE -> "Activa Tailscale en el teléfono y la PC."
            BridgeConnectionMethod.LOCAL_NETWORK -> "Vincula la PC una vez. Después, ambos equipos se buscan por Wi-Fi o Tailscale."
            BridgeConnectionMethod.USB_NETWORK -> "Conecta el cable e inicia USB directo en la PC. Acepta el accesorio en Android."
        }, style = MaterialTheme.typography.bodySmall)
    }
}
