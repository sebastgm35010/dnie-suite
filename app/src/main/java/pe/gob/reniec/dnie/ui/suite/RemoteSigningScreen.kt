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
    onAuthorize: (String) -> Unit, onConnect: () -> Unit, onForget: () -> Unit, onStop: () -> Unit, onBack: () -> Unit) {
    if (state.sessionVisible) {
        SuiteSessionScreen(state, onStop)
        return
    }
    var pin by remember { mutableStateOf("") } // Not saveable, never stored in ViewModel UI state.
    LaunchedEffect(state.pc) { if (state.pc.isEmpty()) pin = "" }
    DisposableEffect(Unit) { onDispose { pin = "" } }
    pe.gob.reniec.dnie.ui.design.FeatureScaffold("Suite · firma remota", "Firma desde PC", onBack = onBack, bottomBar = { pe.gob.reniec.dnie.ui.navigation.ActivityNavigation() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            pe.gob.reniec.dnie.ui.design.FeatureSection("1 · Vincular PC") {
                Text("Los archivos permanecen en tu PC. Tu DNI firma por NFC y la respuesta viaja cifrada. No se publica un lector de tarjeta para otras aplicaciones.")
                Text("IP del teléfono: $addresses")
                Button(onClick = onScan, enabled = !state.busy && !state.armed) { Text("Escanear solicitud de la PC") }
                OutlinedButton(onClick = onImport, enabled = !state.busy && !state.armed) { Text("Importar solicitud TXT") }
                if (state.pc.isNotEmpty()) {
                    Text(if (state.continuous) "PC guardada · conexión continua" else "Solicitud para hasta ${state.count} documento(s)")
                    Text("Dirección al vincular: ${state.pc}. Puede cambiar; la PC se reconoce por su huella.", style = MaterialTheme.typography.bodySmall)
                    Text("Compara esta huella con la que muestra tu PC:")
                    Text(state.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text("Autoriza solo solicitudes que hayas iniciado. El teléfono autoriza el lote y no muestra los documentos guardados en PC.")
            }
            }
            pe.gob.reniec.dnie.ui.design.FeatureSection("2 · Autorizar conexión") {
                OutlinedTextField(state.can, onCan, label = { Text("CAN del DNI") }, singleLine = true,
                    enabled = !state.busy && !state.armed, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (!state.continuous) OutlinedTextField(pin, { pin = it.filter { c -> c in '0'..'9' }.take(8) }, label = { Text("PIN de firma") }, singleLine = true,
                    enabled = !state.busy && !state.armed, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (state.continuous) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (state.connected) "PC ↔ Android: conectados" else "PC ↔ Android: sin conexión")
                            Text(when (state.cardState) {
                                pe.gob.reniec.dnie.signing.link.SuiteCardState.ABSENT -> "DNI: sin conexión NFC. Acerca tu tarjeta."
                                pe.gob.reniec.dnie.signing.link.SuiteCardState.DETECTED -> "DNI: NFC conectado; PACE se abrirá al iniciar el trabajo."
                                pe.gob.reniec.dnie.signing.link.SuiteCardState.PREPARING -> "DNI: abriendo canal PACE. Mantén la tarjeta quieta."
                                pe.gob.reniec.dnie.signing.link.SuiteCardState.READY -> "DNI: canal PACE listo para otro trabajo."
                                pe.gob.reniec.dnie.signing.link.SuiteCardState.IN_USE -> "DNI: procesando solicitud. No retires la tarjeta."
                                pe.gob.reniec.dnie.signing.link.SuiteCardState.REMOVED -> "DNI: retirado o desconectado. Puedes acercarlo de nuevo."
                                pe.gob.reniec.dnie.signing.link.SuiteCardState.FAILED -> "DNI: sesión NFC fallida. Retira y vuelve a acercar la tarjeta."
                                pe.gob.reniec.dnie.signing.link.SuiteCardState.CLOSED -> "DNI: sesión NFC cerrada."
                            })
                        }
                    }
            }
            Text(state.message)
            if (!nfcEnabled) Text("Activa NFC para firmar.")
            if (!state.continuous) Button(onClick = { onAuthorize(pin); pin = "" }, enabled = nfcEnabled && state.pc.isNotEmpty() &&
                state.can.length == 6 && pin.length in 4..8 && !state.busy && !state.armed) { Text("Autorizar este lote") }
            if (state.continuous) {
                Text("El PIN se introduce en Windows y viaja cifrado. Autorizar esta conexión permite a la PC enviar lotes mientras Suite está abierta; no guarda el PIN.")
                Button(onClick = onConnect, enabled = nfcEnabled && state.can.length == 6 && !state.busy && !state.armed) {
                    Text(if (state.remembered) "Conectar PC guardada" else "Recordar PC y conectar")
                }
                OutlinedButton(onClick = onForget, enabled = !state.busy && !state.armed) { Text("Olvidar PC") }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { pin = ""; onStop() }, enabled = state.busy || state.armed) { Text(if (state.continuous) "Cerrar conexión Suite" else "Cancelar") }
            Text("Salir o bloquear el teléfono termina la autorización. Nunca se reenvía una firma fallida.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
