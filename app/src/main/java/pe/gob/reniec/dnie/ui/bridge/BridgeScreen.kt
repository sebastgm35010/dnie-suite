package pe.gob.reniec.dnie.ui.bridge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
@Composable
fun BridgeScreen(state: BridgeUiState, nfcEnabled: Boolean, onImport: () -> Unit, onScan: () -> Unit,
    onCan: (String) -> Unit, onAuthorize: () -> Unit, onStop: () -> Unit, onBack: () -> Unit,
    onExportTrace: () -> Unit, onClearTrace: () -> Unit, onWindowsPace: (Boolean) -> Unit, onTranslatePace: (Boolean) -> Unit,
    onTailnetHost: (String) -> Unit, onConnectTailnet: () -> Unit) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Puente NFC · PC", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = state.tailnetHost, onValueChange = onTailnetHost,
                label = { Text("PC en Tailscale") },singleLine = true,modifier = Modifier.fillMaxWidth(), enabled = !state.busy && !state.armed)
            Button(onClick = onConnectTailnet, enabled = !state.busy && !state.armed) { Text("Conectar por Tailscale · sin JSON") }
            Text(if (state.tailscale) "Tailscale directo · pruebas. Cifrado de la red Tailscale, sin TLS adicional." else "Elige quién gestiona PACE. La conexión con la PC conserva el cifrado TLS.")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = state.windowsPace, onCheckedChange = onWindowsPace, enabled = !state.busy && !state.armed)
                Text("PACE en Windows · comparación")
            }
            Text(if (state.windowsPace) "Android retransmite las APDU físicas. El middleware de Windows puede solicitar el CAN." else "Android abre PACE y recibe APDU lógicas de la PC.")
            if (!state.windowsPace) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = state.translatePace, onCheckedChange = onTranslatePace, enabled = !state.busy && !state.armed)
                    Text("Traducir PACE del middleware · experimental")
                }
                if (state.translatePace) Text("Android mantiene dos sesiones seguras independientes. Perfil IM AES-256; compatibilidad en prueba.")
            }
            Text("Registro APDU activo: incluye comandos y respuestas lógicos completos. Los datos de PIN no se guardan. Exporta el registro al terminar.", style = MaterialTheme.typography.bodySmall)
            Text(if (state.tailscale) "La PC sincroniza la sesión automáticamente al acercar el DNI. Compatibilidad RENIEC en diagnóstico." else "Compatibilidad con RENIEC en diagnóstico. Empareja la PC y compara su huella antes de autorizar.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = onScan, enabled = !state.busy && !state.armed) { Text("Escanear QR de la PC") }
            OutlinedButton(onClick = onImport, enabled = !state.busy && !state.armed) { Text("Importar archivo de emparejamiento") }
            if (state.peer.isNotEmpty()) {
                Text("PC: ${state.peer}", style = MaterialTheme.typography.titleMedium)
                if (!state.tailscale) {
                Text("Huella SHA-256 del servidor")
                Text(state.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("Compara la huella en ambos equipos. Importa el archivo por un canal de confianza; contiene la autorización de conexión.",
                    style = MaterialTheme.typography.bodySmall)
                } else Text("Conexión directa a la PC por Tailscale, con autorización de sesión.")
            }
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
            if (state.traceAvailable) {
                OutlinedButton(onClick = onExportTrace, enabled = !state.busy && !state.armed) { Text("Exportar registro APDU") }
                TextButton(onClick = onClearTrace, enabled = !state.busy && !state.armed) { Text("Borrar registro APDU") }
            }
            Text("Solo permanece activo en esta pantalla. Al salir, bloquear el teléfono o perder la tarjeta se cierra la sesión.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onBack) { Text("Volver") }
        }
    }
}
