package pe.gob.reniec.dnie.ui.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

data class VaultRecoveryActions(
    val prepare: (String, Uri, CharArray) -> Unit,
    val restore: (String, Uri, CharArray, Uri) -> Unit,
    val prepareAll: (Uri, CharArray) -> Unit,
    val restoreAll: (Uri, CharArray, Uri) -> Unit
)

/** Guided whole-vault tasks. Password drafts are never saved across recreation. */
@Composable
internal fun VaultRecoveryControls(state: VaultUiState, actions: VaultRecoveryActions,
    onCanChange: (String) -> Unit, onPinChange: (String) -> Unit, onRememberCan: (Boolean) -> Unit) {
    val busy = state.running || state.awaitingCard
    var task by remember { mutableStateOf("") }
    var packageUri by remember { mutableStateOf<Uri?>(null) }
    var outputUri by remember { mutableStateOf<Uri?>(null) }
    var passwordDialog by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        outputUri = it; passwordDialog = it != null
    }
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        outputUri = it; passwordDialog = it != null
    }
    val pkg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        packageUri = it
        if (it != null) destination.launch("archivos-recuperados.zip")
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (task.isEmpty()) {
            Text("Tu bóveda después de renovar el DNI", style = MaterialTheme.typography.titleLarge)
            Text("Renovar puede borrar la clave que abre tus archivos. Antes del cambio, guarda una protección de toda la bóveda con contraseña.")
            Button(onClick = { task = "prepare" }, enabled = !busy && state.riskAcknowledged && state.allItems > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Proteger toda mi bóveda")
            }
            Text("Incluye ${state.allItems} archivos y la papelera. Necesitas el DNI que los cifró.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { task = "recover" }, enabled = !busy && state.riskAcknowledged, modifier = Modifier.fillMaxWidth()) {
                Text("Recuperar toda mi bóveda")
            }
            Text("Usa la protección guardada y su contraseña, aunque ya no tengas la clave antigua.", style = MaterialTheme.typography.bodySmall)
        } else {
            TextButton(onClick = { task = "" }, enabled = !busy) { Text("Volver") }
            if (task == "prepare") {
                Text("Proteger ${state.allItems} archivos", style = MaterialTheme.typography.titleLarge)
                Text("1. Guarda una copia de los archivos en Copias → Guardar respaldo ZIP.")
                Text("2. Introduce CAN y PIN del DNI original. Se pedirán una sola vez.")
                VaultCredentialFields(state, onCanChange, onPinChange, onRememberCan)
                Text("3. Elige dónde guardar la protección, crea una contraseña y mantén el DNI cerca cuando se indique.")
                Button(onClick = { export.launch("proteccion-boveda.dnierecovery") },
                    enabled = !busy && state.riskAcknowledged && state.allItems > 0 && state.can.length == 6 && state.pin.length in 4..8) {
                    Text("Guardar protección completa")
                }
                Text("La protección guarda las claves de recuperación, no tus documentos. Guárdala separada del ZIP y prueba recuperar antes de renovar. Si agregas archivos, vuelve a actualizarla.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Recuperar todos mis archivos", style = MaterialTheme.typography.titleLarge)
                Text("1. Si tus archivos ya aparecen en Mis archivos, no debes importarlos de nuevo. En otro teléfono, importa primero tu respaldo desde Copias.")
                Text("2. Elige la protección guardada, dónde guardar el ZIP recuperado e introduce su contraseña.")
                Button(onClick = { pkg.launch(arrayOf("*/*")) }, enabled = !busy && state.riskAcknowledged && state.allItems > 0) {
                    Text("Elegir protección y recuperar")
                }
                Text("3. Obtendrás un ZIP con tus documentos originales sin cifrar. Para usar el nuevo DNI, vincúlalo desde Copias y cifra nuevamente esos documentos.")
                Text("No necesitas DNI ni PIN para esta recuperación. Debes tener la protección y contraseña creadas antes de perder la clave. Se comprobará toda la bóveda antes de entregar el ZIP.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (passwordDialog) {
        var password by remember { mutableStateOf("") }
        var confirmation by remember { mutableStateOf("") }
        val preparing = task == "prepare"
        val dismiss = { password = ""; confirmation = ""; passwordDialog = false; outputUri = null; packageUri = null }
        AlertDialog(onDismissRequest = dismiss, title = { Text(if (preparing) "Contraseña para toda la bóveda" else "Abrir protección de la bóveda") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (preparing) "Elige una contraseña larga. Con ella y tu protección podrás recuperar todos los archivos incluidos sin el DNI antiguo. Si la olvidas, no podremos recuperarla." else "La contraseña abrirá la protección guardada; no es el PIN del DNI.")
                OutlinedTextField(password, { if (it.length <= 1024) password = it }, label = { Text("Contraseña (mínimo 16 caracteres)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (preparing) OutlinedTextField(confirmation, { if (it.length <= 1024) confirmation = it }, label = { Text("Repetir contraseña") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (preparing) Text("Conserva copias antiguas bajo control: cambiar la contraseña no las invalida.", style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = {
                TextButton(enabled = !busy && password.length in 16..1024 && (!preparing || password == confirmation), onClick = {
                    val target = outputUri; val source = packageUri
                    if (target != null && (preparing || source != null)) {
                        if (preparing) actions.prepareAll(target, password.toCharArray())
                        else actions.restoreAll(requireNotNull(source), password.toCharArray(), target)
                    }
                    dismiss()
                }) { Text(if (preparing) "Autorizar con DNI" else "Recuperar todos") }
            }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
    }
}
