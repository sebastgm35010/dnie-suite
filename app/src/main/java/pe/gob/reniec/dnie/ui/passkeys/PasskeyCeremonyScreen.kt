package pe.gob.reniec.dnie.ui.passkeys

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Presentation only. No card, filesystem, key or platform-provider operations. */
@Composable
fun PasskeyCeremonyScreen(state: PasskeyUiState, onCan: (String) -> Unit, onPin: (String) -> Unit,
    onAuthorize: () -> Unit, onDelete: (String) -> Unit, onCancel: () -> Unit, nfcEnabled: Boolean, onBiometric: () -> Unit = {}) {
    var deleting by remember { mutableStateOf<String?>(null) }
    // Validation/continuation does not need to compose editable fields, keyboard or account cards.
    if (!state.ready && state.error == null && !state.managing) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(state.title, style = MaterialTheme.typography.titleLarge)
            Text(state.description)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onCancel) { Text("Cerrar") }
        }
        return
    }
    val busy = state.busy || state.awaiting
    val valid = state.can.length == 6 && state.pin.length in 4..8
    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Passkeys con DNIe", style = MaterialTheme.typography.headlineSmall) }
            item { Text(state.title, style = MaterialTheme.typography.titleLarge); Text(state.description) }
            if (state.biometricAvailable) item {
                Button(onBiometric, Modifier.fillMaxWidth(), enabled = !busy) { Text("Autorizar con biometría") }
                Text("Este teléfono fue autorizado previamente con tu DNI.")
            }
            if (!state.independent) item {
                OutlinedTextField(state.can, onCan, Modifier.fillMaxWidth(), label = { Text("CAN") }, enabled = !busy,
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(state.pin, onPin, Modifier.fillMaxWidth(), label = { Text("PIN del DNI") }, enabled = !busy,
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            }
            item {
                if (!state.independent && !nfcEnabled) Text("Activa NFC en los ajustes del teléfono.", color = MaterialTheme.colorScheme.error)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.awaiting) Text(if (state.deleteId == null) "Acerca el DNI y mantenlo hasta terminar." else "Acerca el DNI para eliminar la passkey confirmada.")
                if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(if (state.independent) "Autorizando operación…" else "Autorizando con DNI…") }
                if (!state.independent) Button(onAuthorize, Modifier.fillMaxWidth(), enabled = state.ready && valid && !busy && nfcEnabled) {
                    Text(if (state.managing) "Ver cuentas con DNI" else "Autorizar con DNI")
                }
                TextButton(onCancel, Modifier.fillMaxWidth()) { Text("Cerrar") }
            }
            items(state.accounts, key = { it.id }) { account ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(account.site, style = MaterialTheme.typography.titleMedium); Text(account.username)
                        TextButton(onClick = { deleting = account.id }, enabled = !busy) { Text("Eliminar passkey") }
                    }
                }
            }
            if (state.managing && state.accounts.isNotEmpty()) item { Text(if (state.independent) "Cada eliminación requiere una nueva autorización biométrica. Conserva otro método de acceso al sitio." else "Para eliminar, escribe de nuevo el PIN, confirma la cuenta y acerca el DNI. Eliminarla puede impedir el acceso al sitio; conserva otro método de recuperación.") }
        }
    }
    deleting?.let { id -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("¿Eliminar esta passkey?") },
        text = { Text("La eliminación es permanente y requiere una nueva autorización. Conserva otro método de acceso a la cuenta.") },
        confirmButton = { TextButton(onClick = { deleting = null; onDelete(id) }, enabled = valid || state.independent) { Text("Autorizar eliminación") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }) }
}
