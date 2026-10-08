package pe.gob.reniec.dnie.ui.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** File selectors run before NFC authorization; this UI never receives plaintext file contents. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    state: VaultUiState,
    onCanChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onRememberCan: (Boolean) -> Unit,
    onRun: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onImportArchive: (Uri) -> Unit,
    onExportEncrypted: (String, Uri) -> Unit,
    onRequestOpen: (String, Uri) -> Unit,
    onStartOpen: () -> Unit,
    onClearSelection: () -> Unit,
    onDelete: (String) -> Unit,
    onAcknowledgeRisk: () -> Unit,
    library: VaultLibraryActions,
    recovery: VaultRecoveryActions
) {
    val passkeyContext = androidx.compose.ui.platform.LocalContext.current
    val busy = state.awaitingCard || state.running
    val credentialsValid = state.can.length == 6 && state.can.all { it in '0'..'9' } &&
        state.pin.length in 4..8 && state.pin.all { it in '0'..'9' }
    // Only opaque random IDs survive picker recreation; never credentials or names.
    var pendingEncryptedExport by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOrganize by remember { mutableStateOf<VaultItemUi?>(null) }
    var writingNote by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val importFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) library.importFiles(uris)
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) library.exportBackup(uri)
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) library.importBackup(uri)
    }
    val importArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportArchive(uri)
    }
    val exportEncrypted = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val id = pendingEncryptedExport
        pendingEncryptedExport = null
        if (uri != null && id != null) onExportEncrypted(id, uri)
    }
    val restoreFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val id = pendingOpen
        pendingOpen = null
        if (uri != null && id != null) onRequestOpen(id, uri)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.selectedItemId) {
        if (state.selectedItemId != null) listState.animateScrollToItem(1)
    }

    MaterialTheme(colorScheme = VaultColors) {
        Scaffold(
            containerColor = VaultBackground,
            topBar = {
                TopAppBar(title = { Text("Bóveda personal") }, navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBackground))
            }
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).imePadding(), state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item(key = "hero") { VaultHero(state.pairedFingerprint) }
                item(key = "access") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel(if (state.selectedItemId == null) "01 / VINCULAR EL DNI" else "RECUPERAR TU ARCHIVO")
                        Text(if (state.selectedItemId == null)
                            "Vincula el certificado de cifrado y comprueba su clave privada por NFC. Después podrás guardar archivos sin acercar el DNI."
                        else if (state.previewMode) "Introduce CAN y PIN para ver el archivo dentro de la app. La vista se cerrará al salir."
                        else "Destino elegido. Introduce CAN y PIN; después acerca el DNI para descifrar la copia.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.selectedItemId?.let { id ->
                            Text("Archivo ${id.take(12)}…", fontFamily = FontFamily.Monospace,
                                color = VaultAccent, style = MaterialTheme.typography.bodySmall)
                            if (!state.previewMode) Text("La copia de destino quedará sin cifrar y fuera de la protección de la bóveda.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(value = state.can, onValueChange = onCanChange,
                            label = { Text("CAN") }, supportingText = { Text("6 dígitos impresos en el DNI") },
                            singleLine = true, enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp))
                        OutlinedTextField(value = state.pin, onValueChange = onPinChange,
                            label = { Text("PIN del DNI") }, supportingText = { Text("4 a 8 dígitos. Sin reintentos automáticos.") },
                            singleLine = true, enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp))
                        Row {
                            Checkbox(checked = state.rememberCan, onCheckedChange = onRememberCan, enabled = !busy)
                            Text("Recordar CAN · compartido con la pantalla principal", modifier = Modifier.padding(top = 12.dp))
                        }
                        if (busy) {
                            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                Text("Cancelar operación")
                            }
                        } else {
                            Button(onClick = if (state.selectedItemId == null) onRun else onStartOpen,
                                enabled = credentialsValid && (state.selectedItemId == null || state.riskAcknowledged),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Default.Nfc, null)
                                Spacer(Modifier.width(10.dp))
                                Text(when {
                                    state.selectedItemId != null -> "Descifrar con DNI"
                                    state.pairedFingerprint != null -> "Comprobar DNI vinculado"
                                    else -> "Vincular DNI"
                                })
                            }
                            if (state.selectedItemId != null) {
                                TextButton(onClick = onClearSelection, modifier = Modifier.fillMaxWidth()) {
                                    Text("Descartar destino elegido")
                                }
                            }
                        }
                    }
                }
                if (!state.riskAcknowledged) item(key = "risk") {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF352E22)),
                        shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Antes de guardar archivos", fontWeight = FontWeight.SemiBold)
                            Text("Si pierdes o cambias el DNI puedes perder acceso. Conserva originales y prueba con copias; esta versión es experimental.",
                                style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onAcknowledgeRisk, enabled = !busy) { Text("Entiendo, conservaré mis originales") }
                        }
                    }
                }
                if (busy || state.message != null || state.verified) item(key = "result") {
                    // Credentials do not affect this card. Pass only its own
                    // fields so typing does not recompose the result content.
                    VaultResult(state.running, state.awaitingCard, state.verified, state.message, state.restoredName)
                }
                item(key = "storage") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel("02 / ARCHIVOS CIFRADOS")
                        Text("El contenido permanece cifrado. El nombre original se conserva visible para reconocer tus archivos.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { importFiles.launch(arrayOf("*/*")) },
                            enabled = !busy && state.riskAcknowledged && state.pairedFingerprint != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                            Text("Elegir archivos y cifrar lote")
                        }
                        OutlinedButton(onClick = { importArchive.launch(arrayOf("*/*")) },
                            enabled = !busy && state.riskAcknowledged,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                            Text("Importar archivo .dnievault")
                        }
                        if (state.pairedFingerprint == null) Text("Vincula primero tu DNI para cifrar archivos nuevos.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Hasta 1 GiB por archivo. El original no se elimina. Exportar cifrado crea una copia de respaldo; descifrar requiere tu DNI.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.items.isEmpty()) {
                            Surface(shape = RoundedCornerShape(20.dp), color = VaultSurface) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Lock, null, tint = VaultAccent)
                                    Text("Tu bóveda está vacía", fontWeight = FontWeight.Medium)
                                    Text("Empieza con una copia de prueba y comprueba que puedes recuperarla con el DNI.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else Text("${state.items.size} archivo(s)", style = MaterialTheme.typography.labelMedium)
                    }
                }
                item(key = "library") {
                    VaultLibraryControls(state, library,
                        onBackup = { exportBackup.launch("boveda-completa.dniebackup.zip") },
                        onRestoreBackup = { restoreBackup.launch(arrayOf("application/zip", "application/octet-stream")) })
                }
                item(key = "recovery") { VaultRecoveryControls(state, recovery) }
                if (state.incomingFiles > 0) item(key = "incoming") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${state.incomingFiles} archivo(s) recibidos desde otra app")
                        Button(onClick = library.encryptIncoming,
                            enabled = !busy && state.riskAcknowledged && state.pairedFingerprint != null) { Text("Cifrar recibidos") }
                        TextButton(onClick = library.dismissIncoming) { Text("Descartar selección") }
                    }
                }
                item(key = "notes") {
                    OutlinedButton(onClick = { writingNote = true },
                        enabled = !busy && state.riskAcknowledged && state.pairedFingerprint != null,
                        modifier = Modifier.fillMaxWidth()) { Text("Crear nota cifrada") }
                }
                items(state.items, key = { "file:${it.id}" }) { item ->
                    VaultFileCard(item, !busy && state.riskAcknowledged,
                        selected = state.selectedItemId == item.id,
                        onRestore = { pendingOpen = item.id; restoreFile.launch(item.displayName ?: "documento-restaurado") },
                        onExport = { pendingEncryptedExport = item.id; exportEncrypted.launch("${item.displayName ?: item.id}.dnievault") },
                        onDelete = { pendingDelete = item.id },
                        onPreview = { library.preview(item.id) },
                        onOrganize = { pendingOrganize = item },
                        onRecover = { library.recover(item.id) }, onInspect = { library.inspect(item.id) })
                }
                item(key = "passkeys") {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 18.dp, bottom = 20.dp)) {
                        Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Passkeys con DNIe", fontWeight = FontWeight.Medium)
                            Text("Claves independientes por sitio, cifradas para tu DNI.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { passkeyContext.startActivity(android.content.Intent(passkeyContext,
                                pe.gob.reniec.dnie.ui.passkeys.PasskeySettingsActivity::class.java)) }, enabled = !busy) { Text("Configurar passkeys") }
                        }
                    }
                }
            }
        }
        pendingOrganize?.let { item ->
            VaultOrganizeDialog(item, onDismiss = { pendingOrganize = null }, onSave = { folder, tags ->
                pendingOrganize = null; library.organize(item.id, folder, tags)
            })
        }
        if (writingNote) VaultNoteDialog(onDismiss = { writingNote = false }, onSave = { title, body ->
            writingNote = false; library.createNote(title, body)
        })
        state.details?.let { VaultDetailsDialog(it, onDismiss = library.closeDetails) }
        pendingDelete?.let { id ->
            AlertDialog(onDismissRequest = { pendingDelete = null },
                title = { Text(if (state.showTrash) "¿Eliminar definitivamente?" else "¿Mover a la papelera?") },
                text = { Text(if (state.showTrash) "Se eliminará el contenedor de esta app. Los originales y respaldos externos se conservan." else "Puedes recuperar este archivo desde la papelera. Sigue cifrado.") },
                confirmButton = {
                    TextButton(onClick = { pendingDelete = null; if (state.showTrash) library.purge(id) else onDelete(id) }, enabled = !busy) { Text("Eliminar") }
                }, dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Conservar") } })
        }
    }
}
