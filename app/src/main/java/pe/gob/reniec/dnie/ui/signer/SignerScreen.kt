package pe.gob.reniec.dnie.ui.signer

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.gob.reniec.dnie.domain.signing.*
import pe.gob.reniec.dnie.pdf.*
import pe.gob.reniec.dnie.ui.components.CredentialsDialog
import pe.gob.reniec.dnie.ui.components.NfcTapPromptSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SignerScreen(viewModel: SignerViewModel, batch: Boolean = false, onReadIdentity: () -> Unit = {}, onReadCertificates: () -> Unit = {}) {
    val context = LocalContext.current
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
    pe.gob.reniec.dnie.ui.design.FeatureScaffold(if (batch) "Firma por lotes" else "Firma PDF", "Firma digital", subtitle = if (batch) "Varios archivos · un solo PIN" else "Selecciona, coloca y firma.") { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (batch) "Varios documentos, una autorización" else "Tu firma, en el lugar que elijas", style = MaterialTheme.typography.titleLarge)
                    Text(if (batch) "PDF con firma integrada. Otros archivos conservan el original y reciben su firma .p7s." else "Selecciona un PDF y coloca la firma en una vista previa a pantalla completa.")
                    Button(enabled = editing, onClick = { if (batch) open.launch(arrayOf("*/*")) else openPdf.launch(arrayOf("application/pdf")) }) {
                        Text(if (batch) "Añadir archivos" else if (workspace.documents.isEmpty()) "Seleccionar PDF" else "Cambiar PDF")
                    }
                    Text(if (batch) "Hasta 50 archivos · 256 MiB por archivo · 512 MiB por lote" else "Hasta 256 MiB · guarda el resultado antes de cambiar de PDF", style = MaterialTheme.typography.bodySmall)
                    if (workspace.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    workspace.importErrors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (workspace.documents.isNotEmpty()) {
                Text(if (batch) "Documentos · " + workspace.documents.size else "Documento seleccionado", style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(workspace.documents, key = { it.id }) { doc ->
                    val result = workspace.artifacts.firstOrNull { it.document.id == doc.id }
                    OutlinedCard(onClick = { if (editing) viewModel.selectDocument(doc.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text((if (doc.id == workspace.selectedId) "● " else "") + doc.name, style = MaterialTheme.typography.titleSmall)
                            Text((if (doc.isPdf) "PDF · " + doc.pages.size + " páginas" else "Archivo · .p7s") + " · " + (doc.size / 1024) + " KiB", style = MaterialTheme.typography.bodySmall)
                            if (result?.status == DocumentSignStatus.SIGNED)
                                pe.gob.reniec.dnie.ui.design.StatusCheck(if (doc.isPdf && doc.options.reniecTimestamp) "Firmado · sello RENIEC" else "Firmado", true)
                            else if (result != null) Text(
                                (if (result.status == DocumentSignStatus.FAILED) "No firmado: " else "Pendiente: ") + result.message,
                                color = MaterialTheme.colorScheme.error)
                            Row {
                                if (result?.status == DocumentSignStatus.SIGNED) {
                                    TextButton(enabled = editing, onClick = { exportArtifact = result; exportPem = null; save.launch(requireNotNull(result.output).name) }) { Text("Guardar") }
                                    TextButton(enabled = editing, onClick = {
                                        val files = if (doc.isPdf) listOf(requireNotNull(result.output)) else listOf(doc.file, requireNotNull(result.output))
                                        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, context.packageName + ".fileprovider", it) })
                                        val intent = Intent(if (files.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                                            type = if (doc.isPdf) "application/pdf" else "*/*"
                                            if (files.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0]) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            clipData = android.content.ClipData.newUri(context.contentResolver, "Firma", uris[0]).also { clip -> uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) } }
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(intent, "Compartir documento y firma")) }
                                    }) { Text("Compartir") }
                                }
                                TextButton(enabled = editing, onClick = { viewModel.removeDocument(doc.id) }) { Text("Quitar") }
                            }
                        }
                    }
                }
                }
            }
            workspace.selected?.let { doc ->
                pe.gob.reniec.dnie.ui.design.FeatureSection("Configurar firma", "Las opciones se aplican al documento seleccionado.") {
                    val signed = workspace.artifacts.any { it.document.id == doc.id && it.status == DocumentSignStatus.SIGNED }
                    val enabled = editing && !signed
                    if (batch && doc.pages.isNotEmpty()) SwitchRow("Firmar este PDF como archivo separado (.p7s)", doc.detached, enabled, viewModel::setDetached)
                    if (doc.isPdf && !doc.detached) {
                        val config = doc.options
                        SwitchRow("Sello de tiempo RENIEC · PAdES-T", config.reniecTimestamp, enabled) { value ->
                            viewModel.updateOptions { it.copy(reniecTimestamp = value) }
                        }
                        if (config.reniecTimestamp) Text("Requiere internet. Solo se envía el resumen de la firma. Si el sello no se verifica, no se publica el PDF firmado.", style = MaterialTheme.typography.bodySmall)
                        val look = config.appearance
                        Text("Sello · " + doc.name, style = MaterialTheme.typography.titleMedium)
                        SwitchRow("Mostrar sello en el PDF", config.visible, enabled) { viewModel.updateOptions { c -> c.copy(visible = it) } }
                        if (config.visible) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(StampStyle.INSTITUTIONAL,StampStyle.CARD,StampStyle.MINIMAL,StampStyle.INK,StampStyle.HANDWRITTEN_ONLY).forEach { style ->
                                    FilterChip(selected = look.style == style, enabled = enabled, onClick = { viewModel.updateOptions { c -> c.copy(appearance = c.appearance.copy(style = style, showHandwritten = if (style == StampStyle.INK) true else c.appearance.showHandwritten)) } }, label = { Text(style.label) })
                                }
                            }
                            fun appearance(change: (SignatureAppearance) -> SignatureAppearance) { viewModel.updateOptions { it.copy(appearance = change(it.appearance)) } }
                            if(look.style != StampStyle.HANDWRITTEN_ONLY) {
                                Text("Color del sello",style=MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    StampColorTheme.entries.forEach { color ->
                                        FilterChip(selected=look.colorTheme==color,enabled=enabled,onClick={appearance { it.copy(colorTheme=color) }},
                                            leadingIcon={ androidx.compose.foundation.Canvas(Modifier.size(12.dp)) { drawCircle(androidx.compose.ui.graphics.Color(0xFF000000 or color.accentRgb.toLong())) } },label={Text(color.label)})
                                    }
                                }
                                Text("Insignia",style=MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    listOf(StampBadgeType.SEAL_VECTOR,StampBadgeType.NONE).forEach { badge -> FilterChip(selected=look.badge==badge,enabled=enabled,onClick={appearance { it.copy(badge=badge) }},label={Text(badge.label)}) }
                                }
                                OutlinedTextField(look.customHeader,{ value->appearance { it.copy(customHeader=value.take(160)) } },enabled=enabled,label={Text("Título del sello")},modifier=Modifier.fillMaxWidth())
                                if(config.reniecTimestamp) SwitchRow("Mostrar distintivo TSA",look.showTsaBadge,enabled) { value->appearance { it.copy(showTsaBadge=value) } }
                            }
                            StampPageTargetSelector(config.pageTarget,enabled) { value->viewModel.updateOptions { it.copy(pageTarget=value,
                                pageNumber=when(value) { StampPageTarget.FIRST_PAGE->1;StampPageTarget.LAST_PAGE->doc.pages.size;else->it.pageNumber }) } }
                            if (look.style == StampStyle.HANDWRITTEN_ONLY) Text("Solo se verá la imagen manuscrita: sin texto, fondo añadido ni borde. La firma digital sigue incorporada en el PDF.", style = MaterialTheme.typography.bodySmall)
                            else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(look.showDni, { appearance { it.copy(showDni = !it.showDni) } }, enabled = enabled, label = { Text("DNI") })
                                FilterChip(look.showDate, { appearance { it.copy(showDate = !it.showDate) } }, enabled = enabled, label = { Text("Fecha") })
                                FilterChip(look.showTime, { appearance { it.copy(showTime = !it.showTime) } }, enabled = enabled, label = { Text("Hora") })
                                FilterChip(look.showReason, { appearance { it.copy(showReason = !it.showReason) } }, enabled = enabled, label = { Text("Motivo") })
                                FilterChip(look.showLocation, { appearance { it.copy(showLocation = !it.showLocation) } }, enabled = enabled, label = { Text("Lugar") })
                            }
                            if (look.showReason) OutlinedTextField(config.reason, { text -> viewModel.updateOptions { it.copy(reason = text.take(160)) } }, enabled = enabled, label = { Text("Motivo") }, modifier = Modifier.fillMaxWidth())
                            if (look.showLocation) OutlinedTextField(config.location, { text -> viewModel.updateOptions { it.copy(location = text.take(120)) } }, enabled = enabled, label = { Text("Lugar") }, modifier = Modifier.fillMaxWidth())
                            SwitchRow("Incluir firma manuscrita del DNI", look.showHandwritten, enabled && look.style != StampStyle.INK && (workspace.mark != null || look.showHandwritten)) { value -> appearance { it.copy(showHandwritten = value) } }
                            }
                            if (workspace.mark == null) {
                                Text(workspace.markError ?: "Lee Identidad para obtener la imagen manuscrita (DG7), si está disponible.")
                                OutlinedButton(enabled = editing, onClick = onReadIdentity) { Text("Leer firma manuscrita") }
                            } else if (look.requiresHandwritten) {
                                Text("Imagen ICAO: " + workspace.mark!!.sourceName + ". La imagen acompaña la firma digital realizada por el chip.", style = MaterialTheme.typography.bodySmall)
                                SwitchRow("Confirmo que la imagen pertenece al titular que firmará", workspace.markConfirmed, enabled) { viewModel.confirmMark(it) }
                            }
                            val stampPage=doc.pages[(config.pageNumber-1).coerceIn(0,doc.pages.lastIndex)]
                            StampPreviewCard(stampPage.width*config.placement.width,stampPage.height*config.placement.height,
                                workspace.previewName,workspace.previewDni,config.copy(appearance=look.copy(handwrittenPng=if(look.requiresHandwritten) workspace.mark?.png else null)))
                            Button(enabled = enabled && (!look.requiresHandwritten || workspace.mark != null), onClick = { showPlacement = true }, modifier = Modifier.fillMaxWidth()) { Text("Colocar sello · pantalla completa") }
                            Text("Página " + config.pageNumber + " · Arrastrar, pellizcar y ajustar los bordes", style = MaterialTheme.typography.bodySmall)
                            if (showPlacement && enabled) PdfPlacementEditor(doc, workspace.previewName, workspace.previewDni,
                                config.copy(appearance = look.copy(handwrittenPng = if (look.requiresHandwritten) workspace.mark?.png else null)),
                                onDismiss = { showPlacement = false }, onApply = { updated ->
                                    viewModel.updateOptions { updated.copy(appearance = updated.appearance.copy(handwrittenPng = null)) }
                                    showPlacement = false
                                })
                            if (batch) OutlinedButton(enabled = enabled, onClick = viewModel::applyOptionsToAll) { Text("Copiar configuración a los PDF pendientes") }
                        }
                    } else Text("Este archivo se conservará intacto. Recibirás el original y su firma .p7s; no lleva sello visual.")
                }
            }
            pe.gob.reniec.dnie.ui.design.FeatureSection("Certificado del firmante") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Certificado público", style = MaterialTheme.typography.labelLarge)
                    Text(if (workspace.certificate == null) "Lee los certificados para previsualizar el nombre real y exportarlos." else workspace.previewName)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = editing, onClick = onReadCertificates) { Text("Leer certificados") }
                        if (workspace.certificate != null) {
                            TextButton(enabled = editing, onClick = { exportPem = false; exportArtifact = null; save.launch("certificado_firma.cer") }) { Text("CER") }
                            TextButton(enabled = editing, onClick = { exportPem = true; exportArtifact = null; save.launch("certificado_firma.pem") }) { Text("PEM") }
                        }
                    }
                    Text("Se exporta el certificado público; la clave privada permanece en el DNI.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state is SignerUiState.Signing) {
                val progress = state as SignerUiState.Signing
                Text(progress.message)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick = viewModel::cancelSigning) { Text(if (batch) "Cancelar; conservar firmados" else "Cancelar firma") }
            }
            if (state is SignerUiState.Error) Text((state as SignerUiState.Error).message, color = MaterialTheme.colorScheme.error)
            workspace.exportMessage?.let { Text(it) }
            if (workspace.exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (workspace.pending.isNotEmpty()) {
                Text(if (batch) "Al continuar autorizas la firma de los " + workspace.pending.size + " archivos pendientes. Mantén el DNI sobre el sensor durante todo el lote." else "Autoriza la firma de este PDF y mantén el DNI inmóvil hasta terminar.", style = MaterialTheme.typography.bodySmall)
                Button(enabled = editing, modifier = Modifier.fillMaxWidth(), onClick = { viewModel.restoreEditor(); viewModel.startPinEntry() }) { Text(if (batch) "Firmar " + workspace.pending.size + " archivo(s)" else "Firmar PDF") }
            }
            if (batch && workspace.artifacts.any { it.status == DocumentSignStatus.SIGNED }) {
                Text(workspace.artifacts.count { it.status == DocumentSignStatus.SIGNED }.toString() + " firmados · " + workspace.pending.size + " pendientes", style = MaterialTheme.typography.titleMedium)
                Button(enabled = editing, modifier = Modifier.fillMaxWidth(), onClick = { exportArtifact = null; exportPem = null; save.launch("documentos_firmados.zip") }) { Text("Guardar lote completo · ZIP") }
            }
            if (workspace.documents.isNotEmpty()) TextButton(enabled = editing, onClick = viewModel::reset) { Text(if (batch) "Vaciar lote" else "Quitar PDF") }
            Spacer(Modifier.height(24.dp))
        }
    }
    CredentialsDialog(show = state is SignerUiState.EnteringPin, title = if (batch) "Autorizar firma del lote" else "Autorizar firma PDF",
        subtitle = if (batch) "Se firmarán " + workspace.pending.size + " archivos." else workspace.selected?.name, requireCan = true, requirePin = true,
        initialCan = (state as? SignerUiState.EnteringPin)?.initialCan ?: "",
        onConfirm = viewModel::onCredentialsConfirmed, onDismiss = viewModel::cancelPinEntry)
    NfcTapPromptSheet(isShowing = state is SignerUiState.AwaitingCard,
        statusMessage = if (batch) "Acerca el DNI y mantenlo inmóvil hasta terminar el lote." else "Acerca el DNI y mantenlo inmóvil hasta terminar la firma.", onCancel = viewModel::cancelAwaitingCard)
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, modifier = Modifier.weight(1f).padding(top = 12.dp, end = 8.dp))
        Switch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}
