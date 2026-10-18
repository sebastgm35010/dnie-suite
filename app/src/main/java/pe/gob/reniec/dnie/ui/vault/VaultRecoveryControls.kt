package pe.gob.reniec.dnie.ui.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.ui.design.*

data class VaultRecoveryActions(
    val exportVault: (Uri, CharArray) -> Unit,
    val importVault: (Uri, CharArray) -> Unit
)

/** SAF first, credentials afterwards. Never auto-authorize from a picker callback. */
@Composable
internal fun VaultRecoveryControls(state: VaultUiState, actions: VaultRecoveryActions,
    onCanChange: (String) -> Unit, onPinChange: (String) -> Unit, onRememberCan: (Boolean) -> Unit,
    importing: Boolean = false) {
    val busy = state.running || state.awaitingCard
    val focus = LocalFocusManager.current
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        if (it != null) selectedUri = it.toString()
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) selectedUri = it.toString()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (importing) "Importar bóveda" else "Exportar bóveda", style = MaterialTheme.typography.titleLarge)
        if (busy) {
            Text(if (state.awaitingCard) {
                if (importing) "Acerca el DNI de destino" else "Acerca el DNI para exportar"
            } else "Procesando bóveda", style = MaterialTheme.typography.titleMedium)
            Text(if (state.awaitingCard) "Mantén la tarjeta junto al sensor NFC. Elegir el archivo no completa la operación; ahora se autorizará con el DNI."
                else "Espera a que la app confirme que terminó. Puedes cancelar desde el estado de la operación.")
        } else {
            Text(if (importing) "Tus archivos se cifrarán para el DNI que acerques. Los originales coincidentes se actualizarán al finalizar; los demás se conservan."
                else "Se exportarán ${state.allItems} archivos, incluida la papelera, en un único respaldo comprimido y cifrado con contraseña.")
            Text("1. ${if (importing) "Elige el respaldo" else "Elige dónde guardarlo"}", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = {
                password = ""; confirmation = ""; focus.clearFocus()
                if (importing) open.launch(arrayOf("*/*")) else save.launch("mi-boveda.dniebackup")
            }, enabled = state.riskAcknowledged && (importing || state.allItems > 0), modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedUri != null) "Cambiar archivo elegido" else if (importing) "Elegir respaldo" else "Elegir destino")
            }
            if (selectedUri != null) {
                Text(if (importing) "Respaldo elegido. Falta autorizar la importación." else "Destino elegido. El respaldo aún no se ha escrito.")
                Text("2. ${if (importing) "DNI de destino" else "DNI que abre tus archivos"}", style = MaterialTheme.typography.titleMedium)
                VaultCredentialFields(state, onCanChange, onPinChange, onRememberCan)
                OutlinedTextField(password, { if (it.length <= 1024) password = it },
                    label = { Text("Contraseña del respaldo (mínimo 16 caracteres)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                if (!importing) OutlinedTextField(confirmation, { if (it.length <= 1024) confirmation = it },
                    label = { Text("Repetir contraseña") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val uri = selectedUri?.let(Uri::parse)
                    if (uri != null) {
                        focus.clearFocus()
                        val secret = password.toCharArray()
                        if (importing) actions.importVault(uri, secret) else actions.exportVault(uri, secret)
                        password = ""; confirmation = ""
                    }
                }, enabled = state.riskAcknowledged && state.can.length == 6 && state.pin.length in 4..8 &&
                    password.length in 16..1024 && (importing || password == confirmation), modifier = Modifier.fillMaxWidth()) {
                    Text(if (importing) "Autorizar importación con DNI" else "Autorizar exportación con DNI")
                }
                Text("3. Acerca y mantén el DNI cuando se indique.")
            }
            Text(if (importing) "Se comprobará el respaldo completo antes de incorporar archivos."
                else "Conserva la contraseña. Junto con el respaldo permite restaurar los archivos aunque renueves el DNI. Cambiarla no invalida copias anteriores.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
