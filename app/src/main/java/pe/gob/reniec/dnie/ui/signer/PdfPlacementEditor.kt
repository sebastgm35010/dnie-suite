package pe.gob.reniec.dnie.ui.signer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.domain.signing.SigningDocument
import pe.gob.reniec.dnie.pdf.*
import java.io.File
import kotlin.math.roundToInt

private fun renderPdf(file: File, page: Int, maxWidth: Int): Bitmap =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(page).use { sheet ->
                val width = maxWidth.coerceAtMost((1800f * sheet.width / sheet.height).roundToInt()).coerceAtLeast(1)
                val height = (width.toFloat() * sheet.height / sheet.width).roundToInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(android.graphics.Color.WHITE)
                    sheet.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

/** Full-screen draft editor: cancellation leaves the approved document options untouched. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPlacementEditor(document: SigningDocument, name: String, dni: String, options: PdfSigningOptions,
                       onDismiss: () -> Unit, onApply: (PdfSigningOptions) -> Unit) {
    var draft by remember(document.id) { mutableStateOf(options) }
    val current by rememberUpdatedState(draft)
    val context = LocalContext.current
    val page = document.pages[draft.pageNumber - 1]
    var pageBitmap by remember(document.id, draft.pageNumber) { mutableStateOf<Bitmap?>(null) }
    var error by remember(document.id, draft.pageNumber) { mutableStateOf<String?>(null) }
    LaunchedEffect(document.id, draft.pageNumber) {
        try { pageBitmap = withContext(Dispatchers.IO) { renderPdf(document.file, draft.pageNumber - 1, 1400) } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "No se pudo mostrar la página" }
    }
    var stamp by remember { mutableStateOf<Bitmap?>(null) }
    var stampError by remember { mutableStateOf<String?>(null) }
    var preparingStamp by remember { mutableStateOf(true) }
    LaunchedEffect(page, draft.placement.width, draft.placement.height, draft.appearance, draft.reason, draft.location, name, dni) {
        preparingStamp = true
        // Coalesce touch events while keeping the last appearance visible during a resize.
        delay(120)
        stampError = null
        try {
            stamp = withContext(Dispatchers.IO) {
                if (current.appearance.style == StampStyle.HANDWRITTEN_ONLY) {
                    val png = requireNotNull(current.appearance.handwrittenPng) { "Lee la imagen manuscrita antes de colocarla" }
                    requireNotNull(BitmapFactory.decodeByteArray(png, 0, png.size)) { "Imagen manuscrita inválida" }
                } else {
                    val file = File.createTempFile("stamp_preview_", ".pdf", context.cacheDir)
                    try {
                        file.writeBytes(VisualStampRenderer.previewPdf(page.width * current.placement.width,
                            page.height * current.placement.height, name, dni, current))
                        renderPdf(file, 0, 800)
                    } finally { file.delete() }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { stamp = null; stampError = e.message ?: "No se pudo preparar el sello" }
        preparingStamp = false
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var pageText by remember(draft.pageNumber) { mutableStateOf(draft.pageNumber.toString()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        pe.gob.reniec.dnie.ui.design.FeatureScaffold("Colocar firma", "Vista previa PDF", modifier = Modifier.fillMaxSize(),
            onBack = onDismiss, actions = {
                TextButton(enabled = pageBitmap != null && !preparingStamp && stampError == null && stamp != null,
                    onClick = { onApply(draft) }) { Text("Aplicar") }
            }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = draft.pageNumber > 1, onClick = { draft = draft.copy(pageNumber = draft.pageNumber - 1) }) { Text("Anterior") }
                    Text("Página " + draft.pageNumber + " de " + document.pages.size)
                    TextButton(enabled = draft.pageNumber < document.pages.size, onClick = { draft = draft.copy(pageNumber = draft.pageNumber + 1) }) { Text("Siguiente") }
                }
                if (document.pages.size > 3) OutlinedTextField(pageText, { text ->
                    pageText = text.filter { it.isDigit() }.take(5)
                    pageText.toIntOrNull()?.takeIf { it in 1..document.pages.size }?.let { draft = draft.copy(pageNumber = it) }
                }, label = { Text("Ir a página") }, singleLine = true, modifier = Modifier.padding(horizontal = 12.dp).heightIn(max = 70.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                    val ratio = page.width / page.height
                    val fitWidth = minOf(maxWidth, maxHeight * ratio)
                    Box(Modifier.width(fitWidth).aspectRatio(ratio).background(Color.White).border(1.dp, Color.LightGray)
                        .onSizeChanged { size = it }
                        .pointerInput(document.id) {
                            detectTapGestures { point ->
                                if (size.width > 0 && size.height > 0) {
                                    val p = current.placement
                                    val x = point.x / size.width
                                    val y = point.y / size.height
                                    if (x !in p.left..(p.left + p.width) || y !in p.top..(p.top + p.height))
                                        draft = current.copy(placement = p.moved(x - p.width / 2, y - p.height / 2))
                                }
                            }
                        }) {
                        pageBitmap?.let { Image(it.asImageBitmap(), "Página del documento", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                        if (pageBitmap == null) Text(error ?: "Preparando página…", color = Color.DarkGray, modifier = Modifier.padding(16.dp))
                        val p = draft.placement
                        Box(Modifier.offset { IntOffset((p.left * size.width).roundToInt(), (p.top * size.height).roundToInt()) }
                            .fillMaxWidth(p.width).fillMaxHeight(p.height).border(1.dp, Color.Black)
                            .semantics { contentDescription = "Sello: arrastrar para mover, pellizcar para cambiar tamaño" }
                            .pointerInput(document.id) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    if (size.width > 0 && size.height > 0) {
                                        val placement = current.placement
                                        draft = current.copy(placement = placement.transformed(zoom, pan.x / size.width, pan.y / size.height,
                                            placement.left + centroid.x / size.width, placement.top + centroid.y / size.height))
                                    }
                                }
                            }) {
                            stamp?.let { Image(it.asImageBitmap(), "Vista previa de la firma", Modifier.fillMaxSize(),
                                contentScale = if (draft.appearance.style == StampStyle.HANDWRITTEN_ONLY) ContentScale.Fit else ContentScale.FillBounds) }
                        }
                        fun resize(delta: Offset, width: Boolean, height: Boolean) {
                            if (size.width > 0 && size.height > 0) {
                                val placement = current.placement
                                draft = current.copy(placement = placement.resizedAnchored(
                                    placement.width + if (width) delta.x / size.width else 0f,
                                    placement.height + if (height) delta.y / size.height else 0f))
                            }
                        }
                        ResizeHandle("Ajustar ancho", Modifier.offset {
                            IntOffset(((p.left + p.width) * size.width).roundToInt() - 18.dp.roundToPx(), ((p.top + p.height / 2) * size.height).roundToInt() - 18.dp.roundToPx())
                        }) { resize(it, true, false) }
                        ResizeHandle("Ajustar alto", Modifier.offset {
                            IntOffset(((p.left + p.width / 2) * size.width).roundToInt() - 18.dp.roundToPx(), ((p.top + p.height) * size.height).roundToInt() - 18.dp.roundToPx())
                        }) { resize(it, false, true) }
                        ResizeHandle("Redimensionar esquina", Modifier.offset {
                            IntOffset(((p.left + p.width) * size.width).roundToInt() - 18.dp.roundToPx(), ((p.top + p.height) * size.height).roundToInt() - 18.dp.roundToPx())
                        }) { resize(it, true, true) }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Arrastra para mover. Pellizca con dos dedos para escalar. Arrastra los puntos del borde para ajustar ancho y alto.", style = MaterialTheme.typography.bodySmall)
                        Text("El texto se distribuye en líneas dentro del sello. Amplíalo para mejorar su legibilidad.", style = MaterialTheme.typography.bodySmall)
                        stampError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResizeHandle(label: String, modifier: Modifier, onDrag: (Offset) -> Unit) {
    val change by rememberUpdatedState(onDrag)
    Box(modifier.size(36.dp).semantics { contentDescription = label }.pointerInput(Unit) {
        detectDragGestures { event, drag -> event.consume(); change(drag) }
    }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(13.dp).background(Color.White, CircleShape).border(2.dp, Color.Black, CircleShape))
    }
}
