package pe.gob.reniec.dnie.ui.bridge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BridgeScreen(state: BridgeUiState, nfcEnabled: Boolean, onImport: () -> Unit, onScan: () -> Unit,
    onCan: (String) -> Unit, onAuthorize: () -> Unit, onStop: () -> Unit, onBack: () -> Unit,
    onExportTrace: () -> Unit, onClearTrace: () -> Unit, onWindowsPace: (Boolean) -> Unit, onTranslatePace: (Boolean) -> Unit,
    onTailnetHost: (String) -> Unit, onConnectTailnet: () -> Unit,
    onMethod: (pe.gob.reniec.dnie.bridge.BridgeConnectionMethod) -> Unit,
    onSettings: (pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings) -> Unit, development: Boolean = false, onForgetPc: () -> Unit = {},
    onPcHost: (String) -> Unit = {}) {
    if (state.sessionStage.visible) {
        BridgeSessionScreen(state, onStop)
        return
    }
    var settings by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    if (advanced) BridgeAdvancedSettings(state.settings, onSettings) { advanced = false }
    pe.gob.reniec.dnie.ui.design.FeatureScaffold("Puente NFC · PC", "Lector compatible", onBack = onBack, bottomBar = { Column {
        Surface(tonalElevation = 2.dp) { Button(onClick = onAuthorize, modifier = Modifier.fillMaxWidth().padding(16.dp),
            enabled = nfcEnabled && state.peer.isNotEmpty() && (state.windowsPace || state.can.length == 6) && !state.busy && !state.armed) { Text("Iniciar puente") } }
        pe.gob.reniec.dnie.ui.navigation.ActivityNavigation()
    } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            pe.gob.reniec.dnie.ui.design.FeatureSection("Conectar PC") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = settings, onClick = { settings = !settings }, enabled = !state.busy && !state.armed, leadingIcon = { Icon(Icons.Default.Tune, null) }, label = { Text("Conexión") })
                    AssistChip(onClick = { advanced = true }, enabled = !state.busy && !state.armed, leadingIcon = { Icon(Icons.Default.Settings, null) }, label = { Text("Avanzado") })
                }
                if (settings || state.peer.isEmpty()) BridgeConnectionSettings(state.method, !state.busy && !state.armed, onMethod)
                if(settings) Text("Direcciones disponibles: ${state.localWifiAddress.ifEmpty { "Red no disponible" }}")
                Text("La PC encontrará este teléfono al iniciar. La vinculación se conserva al cambiar de red.")
            if (development) pe.gob.reniec.dnie.development.BridgeDevelopmentSettings(state, onWindowsPace, onTranslatePace, onExportTrace, onClearTrace)
            if (state.method.usesPairing) Button(onClick = onScan, enabled = !state.busy && !state.armed) { Text("Escanear QR de la PC") }
            if (state.method.usesPairing) OutlinedButton(onClick = onImport, enabled = !state.busy && !state.armed) { Text("Importar archivo de emparejamiento") }
            if (state.peer.isNotEmpty()) pe.gob.reniec.dnie.ui.components.LinkedPcCard(state.peer, state.fingerprint,
                tls = state.method != pe.gob.reniec.dnie.bridge.BridgeConnectionMethod.USB_NETWORK) {
                if (state.directPcConnection) OutlinedTextField(state.directPcHost, onPcHost,
                    label = { Text("IP actual de la PC") }, singleLine = true, enabled = !state.busy && !state.armed, modifier = Modifier.fillMaxWidth())
                Text("La dirección puede cambiar; se conserva la identidad vinculada.", style = MaterialTheme.typography.bodySmall)
                if (!state.tailscale) TextButton(onClick = onForgetPc, enabled = !state.busy && !state.armed) { Text("Olvidar PC") }
            }
            }
            pe.gob.reniec.dnie.ui.design.FeatureSection("Tu DNI") {
                pe.gob.reniec.dnie.ui.components.ConnectionCanField(state.can, onCan, !state.busy && !state.armed)
                if (!nfcEnabled) Text("Activa NFC para conectar el DNI.", color = MaterialTheme.colorScheme.error)
                Text(state.message)
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            }
        }
    }
}
