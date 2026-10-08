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
    val restore: (String, Uri, CharArray, Uri) -> Unit
)

/** Local unsaved password fields; pickers never hold passwords across recreation. */
@Composable
internal fun VaultRecoveryControls(state: VaultUiState, actions: VaultRecoveryActions) {
    val busy = state.running || state.awaitingCard
    var id by remember { mutableStateOf<String?>(null) }
    var packageUri by remember { mutableStateOf<Uri?>(null) }
    var outputUri by remember { mutableStateOf<Uri?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        outputUri = it; passwordDialog = it != null
    }
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        outputUri = it; passwordDialog = it != null
    }
    val pkg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        packageUri = it
        if (it != null) destination.launch("archivo-recuperado")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("RECUPERACIÓN ANTES DE RENOVAR EL DNI")
        Text("El respaldo cifrado habitual no recupera una clave eliminada al renovar. Prepara y prueba un paquete separado para cada archivo mientras el DNI original funcione.", style = MaterialTheme.typography.bodySmall)
        Text("Con paquete y contraseña se puede abrir sin el DNI antiguo. Guarda el paquete separado del archivo. Cambiar la contraseña no invalida copias anteriores.", style = MaterialTheme.typography.bodySmall)
        val selected = state.items.firstOrNull { it.id == id && !it.trashed }
        var menu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { menu = true }, enabled = !busy && state.riskAcknowledged) {
                Text(selected?.displayName ?: selected?.id?.take(12) ?: "Seleccionar archivo para recuperación")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                state.items.filterNot { it.trashed }.forEach { item ->
                    DropdownMenuItem(text = { Text(item.displayName ?: item.id.take(12)) }, onClick = {
                        id = item.id; menu = false
                    })
                }
            }
        }
        Text("La selección usa los archivos de la lista filtrada. Puedes importar primero un .dnievault desde otro equipo.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            preparing = true; packageUri = null
            export.launch("recuperacion-${id?.take(12)}.dnierecovery")
        }, enabled = !busy && selected != null && state.riskAcknowledged && state.can.length == 6 && state.pin.length in 4..8) {
            Text("Preparar paquete con DNI y PIN")
        }
        OutlinedButton(onClick = {
            preparing = false; pkg.launch(arrayOf("*/*"))
        }, enabled = !busy && selected != null && state.riskAcknowledged) {
            Text("Recuperar con paquete y contraseña")
        }
        Text("Para preparar, introduce CAN y PIN arriba. La recuperación alternativa no pide CAN, PIN ni biometría.", style = MaterialTheme.typography.bodySmall)
    }
    if (passwordDialog) {
        var password by remember { mutableStateOf("") }
        var confirmation by remember { mutableStateOf("") }
        val dismiss = { password = ""; confirmation = ""; passwordDialog = false; outputUri = null; packageUri = null }
        AlertDialog(onDismissRequest = dismiss, title = { Text(if (preparing) "Proteger paquete de este archivo" else "Contraseña de recuperación") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (preparing) "Usa una contraseña larga y difícil de adivinar. Si la olvidas, este paquete no permitirá recuperar el archivo." else "Se comprobarán el paquete y el archivo completo antes de guardar contenido descifrado.")
                    OutlinedTextField(value = password, onValueChange = { if (it.length <= 1024) password = it },
                        label = { Text("Contraseña (mínimo 16 caracteres)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                    if (preparing) OutlinedTextField(value = confirmation, onValueChange = { if (it.length <= 1024) confirmation = it },
                        label = { Text("Repetir contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
            }, confirmButton = {
                TextButton(enabled = !busy && password.length in 16..1024 && (!preparing || password == confirmation), onClick = {
                    val fileId = id; val target = outputUri; val source = packageUri
                    if (fileId != null && target != null && (preparing || source != null)) {
                        val chars = password.toCharArray()
                        if (preparing) actions.prepare(fileId, target, chars)
                        else actions.restore(fileId, requireNotNull(source), chars, target)
                    }
                    dismiss()
                }) { Text(if (preparing) "Preparar y autorizar DNI" else "Recuperar archivo") }
            }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
    }
}
