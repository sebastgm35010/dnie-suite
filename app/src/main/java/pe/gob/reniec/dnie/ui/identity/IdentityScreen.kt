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
import pe.gob.reniec.dnie.ui.components.CredentialsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityScreen(viewModel: IdentityViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()
    var pendingImage by remember { mutableStateOf<IcaoImage?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
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
    DisposableEffect(viewModel) { onDispose { viewModel.cancelReading() } }
    Scaffold(topBar = { TopAppBar(title = { Text("Identidad del DNI") }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text("Lea los datos y las imágenes del chip. Cada bloque se procesa por separado; no necesita PIN.",
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
            if (state.awaitingCard || state.reading || state.message != null) item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (state.awaitingCard) "Acerque el DNI al sensor NFC y manténgalo inmóvil."
                            else state.message ?: "Leyendo el documento…")
                        if (state.reading || state.awaitingCard) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            val document = state.document
            if (document != null) items(document.blocks.values.toList(), key = { it.file.name }) { block ->
                IdentityBlockCard(block) { image ->
                    pendingImage = image
                    export.launch("dni_${block.file.name.lowercase()}.${if (image.mimeType == "image/jp2") "jp2" else "jpg"}")
                }
            }
        }
    }
    CredentialsDialog(show = state.enteringCan, title = "Leer identidad del DNI", subtitle = "Ingrese el CAN de 6 dígitos impreso en su DNI.",
        requireCan = true, requirePin = false, onConfirm = { can, _ -> viewModel.confirmCan(can) }, onDismiss = viewModel::cancelCan)
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
                    Field("Nacimiento (AA/MM/DD)", mrzDate(content.birthDateMrz))
                    Field("Sexo", content.sex)
                    Field("Vencimiento (AA/MM/DD)", mrzDate(content.expiryDateMrz))
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

private fun mrzDate(value: String): String = if (value.length == 6) value.chunked(2).joinToString("/") else value

private data class DecodedImage(val bitmap: Bitmap? = null, val error: String? = null)

@Composable
private fun IdentityImage(image: IcaoImage) {
    val decoded by produceState<DecodedImage?>(null, image) {
        value = try { DecodedImage(bitmap = IdentityImageDecoder.decode(image)) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { DecodedImage(error = e.message ?: "No se pudo mostrar la imagen") }
        catch (e: LinkageError) { DecodedImage(error = "El decodificador no está disponible en este dispositivo") }
    }
    val bitmap = decoded?.bitmap
    when {
        bitmap != null -> Image(bitmap.asImageBitmap(), contentDescription = "Imagen del documento",
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), contentScale = ContentScale.Fit)
        decoded?.error != null -> Text("Imagen leída; ${decoded?.error}. Puede guardar el original.")
        else -> Text("Preparando imagen…")
    }
}
