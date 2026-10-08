package pe.gob.reniec.dnie.ui.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

data class VaultRecoveryActions(
    val exportVault: (Uri, CharArray) -> Unit,
    val importVault: (Uri, CharArray) -> Unit
)

/** One archive, two directions. Presentation never decrypts or persists credentials. */
@Composable
internal fun VaultRecoveryControls(state: VaultUiState, actions: VaultRecoveryActions,
    onCanChange: (String) -> Unit, onPinChange: (String) -> Unit, onRememberCan: (Boolean) -> Unit,
    importing: Boolean = false) {
    val busy = state.running || state.awaitingCard
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var readyUri by remember { mutableStateOf<Uri?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { readyUri = it }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { readyUri = it }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (importing) "Importar bóveda" else "Exportar bóveda", style = MaterialTheme.typography.titleLarge)
        Text(if (importing) "Elige tu respaldo e introduce su contraseña. Todos sus archivos se cifrarán para el DNI que acerques, sea el mismo o uno nuevo. Los originales coincidentes se actualizarán al finalizar; los demás archivos se conservan."
            else "Se exportarán ${state.allItems} archivos, incluida la papelera, en un único respaldo comprimido y cifrado con contraseña. Podrás importarlo aunque renueves el DNI.")
        Text(if (importing) "DNI de destino" else "DNI que abre tus archivos", style = MaterialTheme.typography.titleMedium)
        VaultCredentialFields(state, onCanChange, onPinChange, onRememberCan)
        OutlinedTextField(password, { if (it.length <= 1024) password = it },
            label = { Text("Contraseña del respaldo (mínimo 16 caracteres)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !busy)
        if (!importing) OutlinedTextField(confirmation, { if (it.length <= 1024) confirmation = it },
            label = { Text("Repetir contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(), enabled = !busy)
        Button(onClick = { if (importing) open.launch(arrayOf("*/*")) else save.launch("mi-boveda.dniebackup") },
            enabled = !busy && state.riskAcknowledged && (importing || state.allItems > 0) && state.can.length == 6 &&
                state.pin.length in 4..8 && password.length in 16..1024 && (importing || password == confirmation),
            modifier = Modifier.fillMaxWidth()) { Text(if (importing) "Elegir respaldo" else "Guardar respaldo") }
        Text(if (importing) "La app comprobará el respaldo completo antes de incorporar archivos. Acerca el DNI cuando se indique; después podrás retirarlo."
            else "Necesitas el DNI para autorizar la exportación. Guarda la contraseña: junto con este respaldo permite restaurar todos los archivos. Cambiarla no invalida copias anteriores.", style = MaterialTheme.typography.bodySmall)
    }
    LaunchedEffect(readyUri) {
        readyUri?.let { uri ->
            if (password.length in 16..1024) {
                val secret = password.toCharArray()
                if (importing) actions.importVault(uri, secret) else actions.exportVault(uri, secret)
            }
            password = ""; confirmation = ""; readyUri = null
        }
    }
}
