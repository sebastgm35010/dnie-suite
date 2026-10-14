package pe.gob.reniec.dnie.ui.signer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.gob.reniec.dnie.domain.signing.*
import pe.gob.reniec.dnie.pdf.*
import pe.gob.reniec.dnie.ui.components.*
import pe.gob.reniec.dnie.ui.design.FeatureScaffold

/** Screen coordinates UI actions; engines, documents and authorization remain owned by the ViewModel. */
@Composable
fun SignerScreen(viewModel: SignerViewModel, batch: Boolean = false, onReadIdentity: () -> Unit = {}, onReadCertificates: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val busy = state is SignerUiState.EnteringPin || state is SignerUiState.AwaitingCard || state is SignerUiState.Signing
    val editing = !busy && !workspace.importing && !workspace.exporting
    var exportArtifact by remember { mutableStateOf<SignedArtifact?>(null) }
    var exportPem by remember { mutableStateOf<Boolean?>(null) }
    var showPlacement by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        uri?.let { viewModel.export(context, it, exportArtifact, exportPem) }
        exportArtifact = null; exportPem = null
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> viewModel.importDocuments(context, uris) }
    val openPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.importDocuments(context, listOf(it), singlePdf = true) } }
    fun selectFiles() { if (batch) open.launch(arrayOf("*/*")) else openPdf.launch(arrayOf("application/pdf")) }
    fun saveArtifact(artifact: SignedArtifact) { exportArtifact = artifact; exportPem = null; save.launch(requireNotNull(artifact.output).name) }
    val signed = workspace.artifacts.filter { it.status == DocumentSignStatus.SIGNED }.distinctBy { it.document.id }.filter { result -> workspace.documents.any { it.id == result.document.id } }
    val selectedResult = signed.firstOrNull { it.document.id == workspace.selectedId }
    val phase = signingSheetPhase(state, workspace)
    val signAction = { viewModel.restoreEditor(); viewModel.startPinEntry() }
    FeatureScaffold(if (batch) "Firma por lotes" else "Firma PDF", "Firma digital",
        bottomBar = {
            SignerActionBar(workspace, batch, editing, selectedResult,
                onSign = signAction, onSave = ::saveArtifact,
                onShare = { shareSignedArtifact(context, it) }, onNew = ::selectFiles,
                onSaveBatch = { exportArtifact = null; exportPem = null; save.launch("documentos_firmados.zip") })
        }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (signed.isNotEmpty()) item { SignedResultHeroCard(signed.size, workspace.documents.size, busy) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (batch) "${workspace.documents.size} archivos · ${signed.size} firmados" else workspace.selected?.name ?: "Selecciona un PDF", style = MaterialTheme.typography.titleMedium)
                        workspace.selected?.takeIf { !batch }?.let { Text("${it.pages.size} páginas · ${it.size / 1024} KiB", style = MaterialTheme.typography.bodySmall) }
                        OutlinedButton(enabled = editing, onClick = ::selectFiles) { Text(if (batch) "Añadir archivos" else if (workspace.documents.isEmpty()) "Seleccionar PDF" else "Cambiar PDF") }
                        if (workspace.documents.isEmpty()) Text(if (batch) "PDF con firma integrada; otros archivos reciben su firma .p7s. Hasta 50 archivos." else "Coloca el sello y firma con tu DNIe.")
                        if (workspace.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                        workspace.importErrors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (batch) items(workspace.documents, key = { it.id }) { doc ->
                SigningDocumentRow(doc, doc.id == workspace.selectedId,
                    workspace.artifacts.firstOrNull { it.document.id == doc.id }, editing,
                    onSelect = { viewModel.selectDocument(doc.id) }, onRemove = { viewModel.removeDocument(doc.id) },
                    onSave = ::saveArtifact, onShare = { shareSignedArtifact(context, it) })
            }
            workspace.selected?.let { doc ->
                val enabled = editing && workspace.artifacts.none { it.document.id == doc.id && it.status == DocumentSignStatus.SIGNED }
                val config = doc.options
                val look = config.appearance
                if (doc.isPdf && config.visible && doc.pages.isNotEmpty()) item {
                    val page = doc.pages[(config.pageNumber - 1).coerceIn(0, doc.pages.lastIndex)]
                    StampPreviewCard(page.width * config.placement.width, page.height * config.placement.height,
                        workspace.previewName, workspace.previewDni, config.copy(appearance = look.copy(handwrittenPng = if (look.requiresHandwritten) workspace.mark?.png else null)),
                        controls = {
                            Text("Página ${config.pageNumber} · ${config.pageTarget.label}", style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf(SignaturePreset.TOP_LEFT to "↖", SignaturePreset.TOP_RIGHT to "↗", SignaturePreset.BOTTOM_LEFT to "↙", SignaturePreset.BOTTOM_RIGHT to "↘").forEach { (preset, label) ->
                                    TextButton(enabled = enabled, onClick = { viewModel.updateOptions { it.copy(placement = StampPlacementGeometry.preset(it.placement, preset)) } }) { Text(label) }
                                }
                            }
                            Button(enabled = enabled && (!look.requiresHandwritten || workspace.mark != null), onClick = { showPlacement = true }, modifier = Modifier.fillMaxWidth()) { Text("Colocar sello · pantalla completa") }
                        })
                }
                item {
                    if (batch && doc.pages.isNotEmpty()) SwitchRow("Firma separada (.p7s)", doc.detached, enabled, viewModel::setDetached)
                    if (doc.isPdf && doc.pages.isNotEmpty()) SignerControls(viewModel, workspace, doc, enabled, editing, batch, onReadIdentity, onReadCertificates) { pem ->
                        exportPem = pem; exportArtifact = null; save.launch(if (pem) "certificado_firma.pem" else "certificado_firma.cer")
                    }
                    else Text("El original se conserva intacto. Recibirás su firma .p7s; no lleva sello visual.")
                }
            }
            if (state is SignerUiState.Error) item { Text((state as SignerUiState.Error).message, color = MaterialTheme.colorScheme.error) }
            workspace.exportMessage?.let { item { Text(it) } }
            if (workspace.exporting) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (workspace.documents.isNotEmpty()) item { TextButton(enabled = editing, onClick = viewModel::reset) { Text(if (batch) "Vaciar lote" else "Quitar PDF") } }
        }
    }
    workspace.selected?.let { doc ->
        val look = doc.options.appearance
        if (showPlacement && editing && doc.pages.isNotEmpty()) PdfPlacementEditor(doc, workspace.previewName, workspace.previewDni,
            doc.options.copy(appearance = look.copy(handwrittenPng = if (look.requiresHandwritten) workspace.mark?.png else null)),
            onDismiss = { showPlacement = false }, onApply = { updated ->
                viewModel.updateOptions { updated.copy(appearance = updated.appearance.copy(handwrittenPng = null)) }; showPlacement = false
            })
    }
    if (phase != null) DnieOperationSheet(true, phase, if (batch) "Firma del lote" else "Firma PDF",
        message = when (state) {
            is SignerUiState.Signing -> (state as SignerUiState.Signing).message
            is SignerUiState.Error -> (state as SignerUiState.Error).message
            is SignerUiState.SignedSuccess -> "${signed.size} de ${workspace.documents.size} firmados" + if (workspace.allSigned()) "" else ". Revisa los pendientes; no se repite ninguna firma."
            else -> null
        }, progress = (state as? SignerUiState.Signing)?.sheetProgress(),
        onRetry = if (workspace.pending.isNotEmpty() && (state as? SignerUiState.Error)?.canRetry != false) signAction else null,
        onDismiss = {
            when (state) {
                is SignerUiState.EnteringPin -> viewModel.cancelPinEntry()
                is SignerUiState.AwaitingCard -> viewModel.cancelAwaitingCard()
                is SignerUiState.Signing -> viewModel.cancelSigning()
                else -> viewModel.restoreEditor()
            }
        }, credentials = { CredentialsForm(subtitle = if (batch) "${workspace.pending.size} archivos pendientes" else workspace.selected?.name,
            requirePin = true, initialCan = (state as? SignerUiState.EnteringPin)?.initialCan ?: "",
            onConfirm = viewModel::onCredentialsConfirmed, onDismiss = viewModel::cancelPinEntry) },
        terminalActions = {
            if (selectedResult != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = editing, onClick = { viewModel.restoreEditor(); saveArtifact(selectedResult) }) { Text("Guardar") }
                FilledTonalButton(enabled = editing, onClick = { viewModel.restoreEditor(); shareSignedArtifact(context, selectedResult) }) { Text("Compartir") }
            }
        })
}
