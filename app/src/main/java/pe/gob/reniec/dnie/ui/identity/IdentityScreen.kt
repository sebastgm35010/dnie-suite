package pe.gob.reniec.dnie.ui.identity

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.icao.*
import pe.gob.reniec.dnie.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityScreen(viewModel: IdentityViewModel, onUseMark: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()
    var pendingImage by remember { mutableStateOf<IcaoImage?>(null) }
    val export = rememberLauncherForActivityResult(IdentityImageExportContract()) { uri ->
        val image = pendingImage
        pendingImage = null
        if (uri != null && image != null) {
            exportScope.launch {
                val success = try { withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(image.bytes) }
                    true
                } } catch (e: CancellationException) { throw e }
                catch (e: Exception) { false }
                Toast.makeText(context, if (success) "Imagen guardada" else "No se pudo guardar la imagen", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var operationVisible by remember { mutableStateOf(false) }
    var details by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.enteringCan, state.awaitingCard, state.reading) {
        if (state.enteringCan || state.awaitingCard || state.reading) operationVisible = true
    }
    fun exportImage(image: IcaoImage, name: String) {
        pendingImage = image
        export.launch(ImageExportRequest(name, image.mimeType))
    }
    DisposableEffect(viewModel) { onDispose { viewModel.cancelReading() } }
    pe.gob.reniec.dnie.ui.design.FeatureScaffold("Identidad del DNI", "Documento ICAO", subtitle = "Datos, foto y firma disponibles.") { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                pe.gob.reniec.dnie.ui.design.FeatureSection("Leer el documento") {
                    Text("Acerca tu DNI para leer sus datos. No necesitas PIN.",
                        style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        if (state.awaitingCard || state.reading) {
                            OutlinedButton(onClick = viewModel::cancelReading) { Text("Cancelar") }
                        } else {
                            Button(onClick = viewModel::requestReading) { Text(if (state.document == null) "Leer DNI" else "Volver a leer") }
                            if (state.document != null) TextButton(onClick = viewModel::clear) { Text("Borrar datos") }
                        }
                    }
                }
            }
            if (!state.reading && !state.awaitingCard) state.message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
            val document = state.document
            if (document != null) {
                item { DigitalIdCredentialCard(document, onUseMark) { exportImage(it, "dni_firma") } }
                item { TextButton(onClick = { details = !details }) { Text("Detalles técnicos ICAO (DG1–DG14, EF.COM, EF.SOD)" + if (details) " −" else " +") } }
                if (details) items(document.blocks.values.toList(), key = { it.file.name }) { block ->
                    IdentityBlockCard(block) { exportImage(it, "dni_" + block.file.name.lowercase()) }
                }
            }
        }
    }
    val hasData = state.document?.blocks?.values?.any { it.status == BlockStatus.AVAILABLE } == true
    val interrupted = state.document?.blocks?.values?.any { it.status == BlockStatus.INTERRUPTED } == true
    val phase = when {
        state.enteringCan -> DnieOperationPhase.CREDENTIALS
        state.awaitingCard -> DnieOperationPhase.AWAITING_TAG
        state.reading -> DnieOperationPhase.TAG_ENGAGED
        hasData && !interrupted -> DnieOperationPhase.COMPLETED
        else -> DnieOperationPhase.ERROR
    }
    DnieOperationSheet(operationVisible, phase, "Leer identidad del DNI", message = state.message, onRetry = viewModel::requestReading,
        onDismiss = { operationVisible = false; if (state.enteringCan) viewModel.cancelCan() else viewModel.cancelReading() },
        credentials = { CredentialsForm(requirePin = false, onConfirm = { can, _ -> viewModel.confirmCan(can) },
            onDismiss = { operationVisible = false; viewModel.cancelCan() }) })
}

@Composable
private fun IdentityBlockCard(block: IcaoBlock, onExport: (IcaoImage) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(block.file.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(block.status.label, color = when (block.status) {
                BlockStatus.ERROR, BlockStatus.INTERRUPTED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            })
            block.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            when (val content = block.content) {
                is IcaoContent.Identity -> {
                    Field("Nombres", content.givenNames)
                    Field("Apellidos", content.surnames)
                    Field("Número de documento", content.documentNumber)
                    Field("Nacionalidad", content.nationality)
                    Field("Nacimiento", IdentityPresentation.date(content.birthDateMrz, true))
                    Field("Sexo", content.sex)
                    Field("Vencimiento", IdentityPresentation.date(content.expiryDateMrz, false))
                    Field("Estado emisor", content.issuingState)
                    Field("Tipo de documento", content.documentType)
                    Field("Datos opcionales MRZ", content.optionalData)
                    val failed = content.checkDigits.filterValues { !it }.keys
                    Text(if (failed.isEmpty()) "Dígitos de control MRZ correctos" else "Revisar controles MRZ: ${failed.joinToString()}",
                        color = if (failed.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                is IcaoContent.Details -> content.fields.forEach { (label, value) -> Field(label, value) }
                is IcaoContent.Images -> content.images.forEach { image ->
                    IdentityImage(image)
                    TextButton(onClick = { onExport(image) }) { Text("Guardar imagen original") }
                }
                is IcaoContent.Inventory -> {
                    Field("Versión LDS", content.ldsVersion ?: "")
                    Field("Bloques anunciados", content.tags.joinToString { tag ->
                        IcaoFile.entries.firstOrNull { it.tag == tag }?.name ?: "Tag %X".format(tag)
                    })
                }
                is IcaoContent.Security -> {
                    Text(if (content.signatureValid) "Firma de EF.SOD comprobada con su certificado incluido"
                        else "La firma de EF.SOD no es válida", color = if (content.signatureValid)
                        MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                    Field("Algoritmo de integridad", content.digestAlgorithm)
                    Field("Firmante del documento", content.signerSubject)
                    Field("Emisor del certificado", content.signerIssuer)
                    Text("Confianza en el emisor pendiente: aún no se valida la cadena contra una autoridad de confianza. Esta lectura tampoco comprueba que el chip no sea un clon.",
                        style = MaterialTheme.typography.bodySmall)
                }
                is IcaoContent.Opaque -> Text("Bloque leído (${content.byteCount} bytes). Interpretación específica del emisor pendiente.")
                null -> Unit
            }
            if (block.file.group != null && block.rawBytes != null) Text(block.hashStatus.label,
                style = MaterialTheme.typography.bodySmall,
                color = if (block.hashStatus == HashStatus.MISMATCH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    if (value.isNotBlank()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}


private data class DecodedImage(val bitmap: Bitmap? = null, val error: String? = null)

@Composable
internal fun IdentityImage(image: IcaoImage, modifier: Modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
    val decoded by produceState<DecodedImage?>(null, image) {
        value = try { DecodedImage(bitmap = IdentityImageDecoder.decode(image)) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { DecodedImage(error = e.message ?: "No se pudo mostrar la imagen") }
        catch (e: LinkageError) { DecodedImage(error = "El decodificador no está disponible en este dispositivo") }
    }
    val bitmap = decoded?.bitmap
    when {
        bitmap != null -> Image(bitmap.asImageBitmap(), contentDescription = "Imagen del documento",
            modifier = modifier, contentScale = ContentScale.Fit)
        decoded?.error != null -> Text("Imagen leída; ${decoded?.error}. Puede guardar el original.")
        else -> Text("Preparando imagen…")
    }
}
