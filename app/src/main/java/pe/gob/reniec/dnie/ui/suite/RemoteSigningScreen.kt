package pe.gob.reniec.dnie.ui.suite

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
fun RemoteSigningScreen(state: RemoteSigningState, nfcEnabled: Boolean, addresses: String,
    onImport: () -> Unit, onScan: () -> Unit, onCan: (String) -> Unit,
    onAuthorize: (String) -> Unit, onStop: () -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") } // Not saveable, never stored in ViewModel UI state.
    LaunchedEffect(state.pc) { if (state.pc.isEmpty()) pin = "" }
    DisposableEffect(Unit) { onDispose { pin = "" } }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Suite · firma remota", style = MaterialTheme.typography.headlineSmall)
            Text("Los archivos permanecen en tu PC. Tu DNI firma por NFC y la respuesta viaja cifrada. No se publica un lector de tarjeta para otras aplicaciones.")
            Text("IP del teléfono: $addresses")
            Button(onClick = onScan, enabled = !state.busy && !state.armed) { Text("Escanear solicitud de la PC") }
            OutlinedButton(onClick = onImport, enabled = !state.busy && !state.armed) { Text("Importar solicitud TXT") }
            if (state.pc.isNotEmpty()) {
                Text("PC: ${state.pc} · hasta ${state.count} documento(s)")
                Text("Compara esta huella con la que muestra tu PC:")
                Text(state.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("Autoriza solo solicitudes que hayas iniciado. El teléfono autoriza el lote y no muestra los documentos guardados en PC.")
            }
            OutlinedTextField(state.can, onCan, label = { Text("CAN del DNI") }, singleLine = true,
                enabled = !state.busy && !state.armed, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            OutlinedTextField(pin, { pin = it.filter { c -> c in '0'..'9' }.take(8) }, label = { Text("PIN de firma") }, singleLine = true,
                enabled = !state.busy && !state.armed, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            Text(state.message)
            if (!nfcEnabled) Text("Activa NFC para firmar.")
            Button(onClick = { onAuthorize(pin); pin = "" }, enabled = nfcEnabled && state.pc.isNotEmpty() &&
                state.can.length == 6 && pin.length in 4..8 && !state.busy && !state.armed) { Text("Autorizar este lote") }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { pin = ""; onStop() }, enabled = state.busy || state.armed) { Text("Cancelar") }
            Text("Salir o bloquear el teléfono termina la autorización. Nunca se reenvía una firma fallida.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onBack) { Text("Volver") }
        }
    }
}
