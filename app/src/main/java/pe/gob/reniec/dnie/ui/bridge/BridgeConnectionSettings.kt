package pe.gob.reniec.dnie.ui.bridge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pe.gob.reniec.dnie.bridge.BridgeConnectionMethod
@Composable fun BridgeConnectionSettings(method: BridgeConnectionMethod, enabled: Boolean, select: (BridgeConnectionMethod) -> Unit) {
    Column {
        BridgeConnectionMethod.entries.forEach { value ->
            Row { RadioButton(method == value, { select(value) }, enabled = enabled); Text(value.title) }
        }
        Text(when (method) {
            BridgeConnectionMethod.TAILSCALE -> "Requiere Tailscale en ambos equipos y permiso entre ellos en la tailnet. Sin JSON ni TLS adicional."
            BridgeConnectionMethod.LOCAL_NETWORK -> "Conecta ambos equipos a la misma red. En Windows elige su IP local. El QR vincula la sesión con TLS y huella del servidor."
            BridgeConnectionMethod.USB_NETWORK -> "Conecta el cable y activa Compartir Internet por USB en Android. En Windows elige la IP de esa interfaz. Usa TLS y QR; no requiere depuración USB ni ADB."
        }, style = MaterialTheme.typography.bodySmall)
        Text("Avanzado: el teléfono gestiona PACE y traduce el protocolo del PC. Al salir o perder NFC se cierra la sesión; no hay reconexión ni reenvío de operaciones automático.", style = MaterialTheme.typography.bodySmall)
    }
}
