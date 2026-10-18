package pe.gob.reniec.dnie.ui.vault

import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.vault.VaultSort

/** Actions only: no file IO, cryptography or card commands in presentation components. */
data class VaultLibraryActions(
    val importFiles: (List<Uri>) -> Unit,
    val exportBackup: (Uri) -> Unit,
    val importBackup: (Uri) -> Unit,
    val query: (String) -> Unit,
    val folder: (String?) -> Unit,
    val sort: (VaultSort) -> Unit,
    val trash: (Boolean) -> Unit,
    val organize: (String, String, List<String>) -> Unit,
    val recover: (String) -> Unit,
    val purge: (String) -> Unit,
    val preview: (String) -> Unit,
    val createNote: (String, String) -> Unit,
    val encryptIncoming: () -> Unit,
    val dismissIncoming: () -> Unit,
    val inspect: (String) -> Unit,
    val closeDetails: () -> Unit,
    val trashMany: (List<String>, Boolean) -> Unit,
    val organizeMany: (List<String>, String, List<String>) -> Unit
)

@Composable
internal fun VaultLibraryControls(state: VaultUiState, actions: VaultLibraryActions) {
    val busy = state.running || state.awaitingCard
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(state.query, actions.query, label = { Text("Buscar nombre, carpeta o etiqueta") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Text("⌕") },
            trailingIcon = { if (state.query.isNotBlank()) IconButton(onClick = { actions.query("") }) { Text("×") } })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.folder == null && !state.showTrash, onClick = { actions.trash(false); actions.folder(null) }, label = { Text("Todas") })
            FilterChip(selected = state.folder == "" && !state.showTrash, onClick = { actions.trash(false); actions.folder("") }, label = { Text("Sin carpeta") })
            state.folders.forEach { folder -> key(folder) { FilterChip(selected = state.folder == folder && !state.showTrash, onClick = { actions.trash(false); actions.folder(folder) }, label = { Text(folder) }) } }
            FilterChip(selected = state.showTrash, onClick = { actions.trash(true) }, label = { Text("Papelera") })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ordenar", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            VaultSort.entries.forEach { sort -> FilterChip(selected = state.sort == sort, onClick = { actions.sort(sort) }, label = { Text(when (sort) { VaultSort.NEWEST -> "Fecha"; VaultSort.NAME -> "Nombre"; VaultSort.SIZE -> "Tamaño" }) }) }
        }
    }
}

@Composable
internal fun VaultDetailsDialog(details: pe.gob.reniec.dnie.domain.vault.VaultFileDetails,
    onDismiss: () -> Unit) {
    val item = details.item.file
    val labels = details.item.labels
    fun date(value: Long) = if (value == 0L) "Sin registro" else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(value))
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Ficha del archivo") }, text = {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(item.displayName ?: "Nombre pendiente", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text("Contenedor: ${item.encryptedBytes} bytes")
            Text("Añadido: ${date(item.createdAt)}")
            Text("Carpeta: ${labels.folder.ifBlank { "Sin carpeta" }}")
            Text("Estado: ${if (labels.trashed) "Papelera" else "Activo"}")
            Text("Última apertura verificada: ${date(labels.verifiedAt)}")
            Text("Última copia cifrada guardada: ${date(labels.backedUpAt)}")
            Text("Destinatario · SHA-256 de la clave pública", style = MaterialTheme.typography.labelMedium)
            Text(
                text = details.recipient.chunked(16).joinToString("\n"),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
            Text("El destinatario procede de la cabecera. Los registros de actividad son informativos; la integridad se comprueba de nuevo al abrir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
internal fun VaultNoteDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    // No saved-state: plaintext drafts disappear when the screen/process is destroyed.
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val valid = remember(title, body) { runCatching {
        pe.gob.reniec.dnie.domain.vault.VaultNoteDraft.metadata(title, body)
    }.isSuccess }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nueva nota cifrada") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it.take(180) }, label = { Text("Nombre") }, singleLine = true)
            OutlinedTextField(body, { body = it.take(65536) }, label = { Text("Contenido privado") }, minLines = 4, maxLines = 8)
            Text("El nombre queda visible; el contenido se cifra. Para leerlo necesitarás el DNI.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onSave(title, body); body = ""; title = "" }, enabled = valid) { Text("Cifrar nota") } },
        dismissButton = { TextButton(onClick = { body = ""; onDismiss() }) { Text("Cancelar") } })
}

@Composable
internal fun VaultOrganizeDialog(item: VaultItemUi, onDismiss: () -> Unit, onSave: (String, List<String>) -> Unit) {
    var folder by remember(item.id) { mutableStateOf(item.folder) }
    var tags by remember(item.id) { mutableStateOf(item.tags.joinToString(", ")) }
    val parsed = tags.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Organizar archivo") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.displayName ?: "Archivo cifrado")
            OutlinedTextField(folder, { folder = it.take(80) }, label = { Text("Carpeta (vacío = sin carpeta)") })
            OutlinedTextField(tags, { tags = it.take(700) }, label = { Text("Etiquetas separadas por comas") })
            Text("Carpetas y etiquetas son visibles, como el nombre del archivo.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onSave(folder, parsed) }, enabled = parsed.size <= 16 && parsed.all { it.length <= 40 }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
