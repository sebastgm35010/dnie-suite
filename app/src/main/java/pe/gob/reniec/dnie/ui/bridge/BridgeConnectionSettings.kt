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
            BridgeConnectionMethod.TAILSCALE -> "Requiere Tailscale en ambos equipos y permiso entre ellos en la tailnet. Sin JSON ni TLS adicional."
            BridgeConnectionMethod.LOCAL_NETWORK -> "Conecta ambos equipos a la misma Wi-Fi. Autoriza en Android y escribe la IP del teléfono mostrada aquí en el gestor de Windows. TLS comprueba la identidad permanente de la PC."
            BridgeConnectionMethod.USB_NETWORK -> "Conecta un cable de datos e inicia USB directo en Windows. Autoriza el accesorio Android. No necesita compartir Internet ni depuración USB; TLS viaja por el cable."
        }, style = MaterialTheme.typography.bodySmall)
        Text("Avanzado: el teléfono gestiona PACE y traduce el protocolo del PC. No hay límite de inactividad. Retirar y acercar el DNI abre una sesión nueva, sin reenviar operaciones anteriores.", style = MaterialTheme.typography.bodySmall)
    }
}
