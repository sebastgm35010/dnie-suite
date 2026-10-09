package pe.gob.reniec.dnie.ui.bridge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
@Composable
fun BridgeScreen(state: BridgeUiState, nfcEnabled: Boolean, onImport: () -> Unit, onScan: () -> Unit,
    onCan: (String) -> Unit, onAuthorize: () -> Unit, onStop: () -> Unit, onBack: () -> Unit,
    onExportTrace: () -> Unit, onClearTrace: () -> Unit, onWindowsPace: (Boolean) -> Unit, onTranslatePace: (Boolean) -> Unit,
    onTailnetHost: (String) -> Unit, onConnectTailnet: () -> Unit,
    onMethod: (pe.gob.reniec.dnie.bridge.BridgeConnectionMethod) -> Unit,
    onSettings: (pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings) -> Unit, development: Boolean = false, onForgetPc: () -> Unit = {}) {
    var settings by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    if (advanced) BridgeAdvancedSettings(state.settings, onSettings) { advanced = false }
    pe.gob.reniec.dnie.ui.design.FeatureScaffold("Puente NFC · PC", "Lector compatible", onBack = onBack, bottomBar = { pe.gob.reniec.dnie.ui.navigation.ActivityNavigation() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            pe.gob.reniec.dnie.ui.design.FeatureSection("1 · Conexión", "Elige el transporte y empareja tu PC.") {
                TextButton(onClick = { settings = !settings }, enabled = !state.busy && !state.armed) { Text("Configuración de conexión") }
                TextButton(onClick = { advanced = true }, enabled = !state.busy && !state.armed) { Text("Configuración avanzada") }
                if (settings || state.peer.isEmpty()) BridgeConnectionSettings(state.method, !state.busy && !state.armed, onMethod)
                if (state.method == pe.gob.reniec.dnie.bridge.BridgeConnectionMethod.TAILSCALE) {
                    OutlinedTextField(value = state.tailnetHost, onValueChange = onTailnetHost,
                        label = { Text("PC en Tailscale") },singleLine = true,modifier = Modifier.fillMaxWidth(), enabled = !state.busy && !state.armed)
                    Button(onClick = onConnectTailnet, enabled = !state.busy && !state.armed) { Text("Conectar por Tailscale") }
            }
            if(state.method == pe.gob.reniec.dnie.bridge.BridgeConnectionMethod.LOCAL_NETWORK)
                Text("IP Wi-Fi del teléfono: ${state.localWifiAddress.ifEmpty { "Conecta a Wi-Fi" }} · puerto 39444")
            Text(if (state.tailscale) "Conexión cifrada por Tailscale." else "Conexión cifrada con la PC mediante TLS.")
            Text(if (state.translatePace) "Traducción PACE · modo recomendado" else if (state.windowsPace) "Modo directo · PACE gestionado por Windows" else "PACE en Android · instrucciones lógicas")
            Text("El teléfono mantiene protegido el acceso NFC al DNI y lo presenta como tarjeta inteligente a la PC.")
            if (development) pe.gob.reniec.dnie.development.BridgeDevelopmentSettings(state, onWindowsPace, onTranslatePace, onExportTrace, onClearTrace)
            Text(if (state.tailscale) "La PC sincroniza la sesión automáticamente al acercar el DNI." else "Empareja la PC y compara su huella antes de autorizar.",
                style = MaterialTheme.typography.bodySmall)
            if (state.method.usesPairing) Button(onClick = onScan, enabled = !state.busy && !state.armed) { Text("Escanear QR de la PC") }
            if (state.method.usesPairing) OutlinedButton(onClick = onImport, enabled = !state.busy && !state.armed) { Text("Importar archivo de emparejamiento") }
            if (state.peer.isNotEmpty()) {
                Text("PC: ${state.peer}", style = MaterialTheme.typography.titleMedium)
                if (!state.tailscale) TextButton(onClick=onForgetPc, enabled=!state.busy && !state.armed) {Text("Olvidar PC") }
                if (!state.tailscale) {
                Text("Huella SHA-256 del servidor")
                Text(state.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("Compara la huella en ambos equipos. Importa el archivo por un canal de confianza; contiene la autorización de conexión.",
                    style = MaterialTheme.typography.bodySmall)
                } else Text("Conexión directa a la PC por Tailscale, con autorización de sesión.")
            }
            }
            pe.gob.reniec.dnie.ui.design.FeatureSection("2 · Autorizar DNI", "El acceso NFC y la conexión con Windows son sesiones distintas.") {
                OutlinedTextField(value = state.can, onValueChange = onCan, label = { Text("CAN del DNI") },
                    modifier = Modifier.fillMaxWidth(), enabled = !state.busy && !state.armed,
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (!nfcEnabled) Text("Activa NFC para conectar el DNI.", color = MaterialTheme.colorScheme.error)
                Text(state.message)
                Button(onClick = onAuthorize, enabled = nfcEnabled && state.peer.isNotEmpty() && (state.windowsPace || state.can.length == 6) && !state.busy && !state.armed) {
                    Text("Autorizar puente en esta PC")
            }
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onStop, enabled = state.busy || state.armed) { Text("Detener sesión") }
            Text("Solo permanece activo en esta pantalla. Al salir o bloquear el teléfono se detiene. Retirar el DNI cierra la sesión de tarjeta y permite iniciar otra al acercarlo.",
                style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
