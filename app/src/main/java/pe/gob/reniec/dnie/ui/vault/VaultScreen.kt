package pe.gob.reniec.dnie.ui.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class VaultPage(val title: String) { FILES("Mis archivos"), DNI("Usar DNI"), RECOVERY("Respaldo"), TOOLS("Opciones") }

/** Navigation/pickers only; each page presents one task. Backend remains in repository/engines. */
@Composable
fun VaultScreen(state: VaultUiState, onCanChange: (String) -> Unit, onPinChange: (String) -> Unit,
    onRememberCan: (Boolean) -> Unit, onRun: () -> Unit, onBack: () -> Unit, onCancel: () -> Unit,
    onImportArchive: (Uri) -> Unit, onExportEncrypted: (String, Uri) -> Unit,
    onRequestOpen: (String, Uri) -> Unit, onStartOpen: () -> Unit, onClearSelection: () -> Unit,
    onDelete: (String) -> Unit, onAcknowledgeRisk: () -> Unit,
    library: VaultLibraryActions, recovery: VaultRecoveryActions) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val busy = state.running || state.awaitingCard
    var pageName by rememberSaveable { mutableStateOf(VaultPage.FILES.name) }
    val page = VaultPage.valueOf(pageName)
    val filesScroll = rememberLazyListState()
    val dniScroll = rememberLazyListState()
    val recoveryScroll = rememberLazyListState()
    val toolsScroll = rememberLazyListState()
    var importingVault by rememberSaveable { mutableStateOf(false) }
    var importChoice by remember { mutableStateOf(false) }
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var organize by remember { mutableStateOf<VaultItemUi?>(null) }
    var delete by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var bulkOrganize by remember { mutableStateOf(false) }
    var bulkTrash by remember { mutableStateOf(false) }
    val importFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) library.importFiles(it)
    }
    val importArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onImportArchive) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val id = pendingExport; pendingExport = null
        if (uri != null && id != null) onExportEncrypted(id, uri)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val id = pendingOpen; pendingOpen = null
        if (uri != null && id != null) { onRequestOpen(id, uri); pageName = VaultPage.DNI.name }
    }
    LaunchedEffect(state.selectedItemId) { if (state.selectedItemId != null) pageName = VaultPage.DNI.name }
    LaunchedEffect(state.items, state.showTrash) {
        selection = selection.intersect(state.items.map { it.id }.toSet())
    }
    fun finishSelection() { selection = emptySet(); selecting = false }
    Box(Modifier.fillMaxSize()) {
        pe.gob.reniec.dnie.ui.design.FeatureScaffold("Bóveda", "Archivos protegidos",
            subtitle = if (page == VaultPage.FILES) "Tu biblioteca cifrada. Importa, organiza y conserva un respaldo completo." else page.title,
            onBack = if (page == VaultPage.FILES) null else ({ pageName = VaultPage.FILES.name }),
            actions = {
                IconButton(onClick = { pageName = VaultPage.TOOLS.name }, enabled = !busy) {
                    Icon(Icons.Default.Settings, "Opciones de bóveda")
                }
            }) { padding ->
            // Separate scroll state per page; switching does not recreate the vault session.
            key(page) {
                LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(),
                    state = when (page) { VaultPage.FILES -> filesScroll; VaultPage.DNI -> dniScroll
                        VaultPage.RECOVERY -> recoveryScroll; VaultPage.TOOLS -> toolsScroll },
                    contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (page != VaultPage.FILES) item("return") {
                        TextButton(onClick = { pageName = VaultPage.FILES.name }) { Text("Mis archivos") }
                    }
                    if (!state.riskAcknowledged) item("risk") {
                        Card { Column(Modifier.padding(16.dp)) {
                            Text("Conserva tus originales", style = MaterialTheme.typography.titleMedium)
                            Text("Renovar el DNI puede sustituir sus claves. Prepara y prueba la recuperación antes del cambio.")
                            TextButton(onClick = onAcknowledgeRisk, enabled = !busy) { Text("Entendido") }
                        } }
                    }
                    if (busy || state.message != null) item("status") {
                        VaultResult(state.running, state.awaitingCard, state.verified, state.message, state.restoredName)
                        if (busy) TextButton(onClick = onCancel) { Text("Cancelar operación") }
                    }
                    when (page) {
                        VaultPage.DNI -> item("access") {
                            VaultAccessPanel(state, onCanChange, onPinChange, onRememberCan, onRun, onStartOpen, onClearSelection, onCancel)
                        }
                        VaultPage.RECOVERY -> {
                            item("recovery") { VaultRecoveryControls(state, recovery, onCanChange, onPinChange, onRememberCan, importingVault) }
                        }
                        VaultPage.TOOLS -> {
                            item("options") {
                                TextButton(onClick = { pageName = VaultPage.DNI.name }, enabled = !busy) { Text("Vincular o cambiar DNI") }
                                Text("Vincular cambia el DNI para nuevos archivos. Para trasladar los anteriores, exporta e importa la bóveda.")
                                TextButton(onClick = { note = true }, enabled = !busy && state.pairedFingerprint != null) { Text("Nueva nota cifrada") }
                            }
                        }
                        VaultPage.FILES -> {
                            item("add") {
                                pe.gob.reniec.dnie.ui.design.FeatureSection("Mis archivos", "Cifra nuevos archivos o recupera tu bóveda completa.") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { importFiles.launch(arrayOf("*/*")) },
                                            enabled = !busy && state.riskAcknowledged && state.pairedFingerprint != null,
                                            modifier = Modifier.weight(1f)) { Text("Cifrar archivos") }
                                        OutlinedButton(onClick = { importChoice = true },
                                            enabled = !busy && state.riskAcknowledged, modifier = Modifier.weight(1f)) { Text("Importar cifrados") }
                                    }
                                    OutlinedButton(onClick = { importingVault = false; pageName = VaultPage.RECOVERY.name },
                                        enabled = !busy && state.riskAcknowledged && state.allItems > 0,
                                        modifier = Modifier.fillMaxWidth()) { Text("Exportar bóveda") }
                                    if (state.pairedFingerprint == null) TextButton(onClick = { pageName = VaultPage.DNI.name }) { Text("Vincular DNI para cifrar") }
                                }
                            }
                            item("search") { VaultLibraryControls(state, library) }
                            if (state.incomingFiles > 0) item("incoming") {
                                Text("${state.incomingFiles} archivo(s) recibidos")
                                TextButton(onClick = library.encryptIncoming, enabled = !busy && state.riskAcknowledged && state.pairedFingerprint != null) { Text("Cifrar recibidos") }
                                TextButton(onClick = library.dismissIncoming, enabled = !busy) { Text("Descartar") }
                            }
                            item("selection") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { if (selecting) finishSelection() else selecting = true }, enabled = !busy) {
                                        Text(if (selecting) "Cancelar selección" else "Seleccionar varios")
                                    }
                                    if (selecting) TextButton(onClick = { selection = state.items.map { it.id }.toSet() }, enabled = !busy) { Text("Todos los visibles") }
                                }
                                if (selecting) {
                                    Text("${selection.size} seleccionado(s)")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (!state.showTrash) TextButton(onClick = { bulkOrganize = true }, enabled = !busy && selection.isNotEmpty() && state.riskAcknowledged) { Text("Organizar") }
                                        TextButton(onClick = { bulkTrash = true }, enabled = !busy && selection.isNotEmpty() && state.riskAcknowledged) {
                                            Text(if (state.showTrash) "Restaurar" else "A papelera")
                                        }
                                    }
                                }
                            }
                            if (state.items.isEmpty()) item("empty") { Text(if (state.showTrash) "No hay archivos en esta vista de papelera." else "No hay archivos en esta vista.") }
                            items(state.items, key = { it.id }) { item ->
                                VaultFileCard(item, !busy && state.riskAcknowledged, state.selectedItemId == item.id,
                                    onRestore = { pendingOpen = item.id; restore.launch(item.displayName ?: "documento-restaurado") },
                                    onExport = { pendingExport = item.id; export.launch("${item.displayName ?: item.id}.dnievault") },
                                    onDelete = { delete = item.id }, onPreview = { library.preview(item.id); pageName = VaultPage.DNI.name },
                                    onOrganize = { organize = item }, onRecover = { library.recover(item.id) }, onInspect = { library.inspect(item.id) },
                                    selectionMode = selecting, checked = item.id in selection,
                                    onToggle = { selection = if (item.id in selection) selection - item.id else selection + item.id })
                            }
                        }
                    }
                }
            }
        }
        if (importChoice) AlertDialog(onDismissRequest = { importChoice = false }, title = { Text("Importar cifrados") },
            text = { Column {
                TextButton(onClick = { importChoice = false; importArchive.launch(arrayOf("*/*")) }) { Text("Archivo cifrado (.dnievault)") }
                TextButton(onClick = { importChoice = false; importingVault = true; pageName = VaultPage.RECOVERY.name }) { Text("Bóveda completa (.dniebackup)") }
            } }, confirmButton = { TextButton(onClick = { importChoice = false }) { Text("Cancelar") } })
        organize?.let { item -> VaultOrganizeDialog(item, { organize = null }, { folder, tags -> organize = null; library.organize(item.id, folder, tags) }) }
        if (bulkOrganize) VaultOrganizeDialog(VaultItemUi("bulk", 0, "${selection.size} archivos: se reemplazarán carpeta y etiquetas"),
            { bulkOrganize = false }, { folder, tags -> bulkOrganize = false; library.organizeMany(selection.toList(), folder, tags); finishSelection() })
        if (note) VaultNoteDialog({ note = false }, { title, body -> note = false; library.createNote(title, body) })
        state.details?.let { VaultDetailsDialog(it, library.closeDetails) }
        if (bulkTrash) AlertDialog(onDismissRequest = { bulkTrash = false }, title = { Text(if (state.showTrash) "Restaurar selección" else "Mover selección a papelera") },
            text = { Text("${selection.size} archivo(s). El contenido permanece cifrado.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { bulkTrash = false; library.trashMany(selection.toList(), !state.showTrash); finishSelection() }) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = { bulkTrash = false }) { Text("Cancelar") } })
        delete?.let { id -> AlertDialog(onDismissRequest = { delete = null }, title = { Text(if (state.showTrash) "Eliminar definitivamente" else "Mover a papelera") },
            text = { Text(if (state.showTrash) "Se eliminará este contenedor local." else "Podrás restaurarlo desde la papelera.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { delete = null; if (state.showTrash) library.purge(id) else onDelete(id) }) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancelar") } }) }
    }
}
