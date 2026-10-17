package pe.gob.reniec.dnie.ui.signer

import android.graphics.Bitmap
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
import androidx.compose.ui.draw.drawBehind
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
import kotlinx.coroutines.delay
import pe.gob.reniec.dnie.domain.signing.SigningDocument
import pe.gob.reniec.dnie.pdf.*
import kotlin.math.roundToInt

/** Full-screen draft editor: cancellation leaves the approved document options untouched. */
@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
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
        try { pageBitmap = pe.gob.reniec.dnie.data.signing.AndroidStampPreview.page(document.file,draft.pageNumber-1) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "No se pudo mostrar la página" }
    }
    val previewTime=remember { System.currentTimeMillis() }
    var stamp by remember { mutableStateOf<Bitmap?>(null) }
    var stampError by remember { mutableStateOf<String?>(null) }
    var preparingStamp by remember { mutableStateOf(true) }
    LaunchedEffect(page, draft.placement.width, draft.placement.height, draft.appearance, draft.reason, draft.location, draft.reniecTimestamp, name, dni) {
        preparingStamp = true
        // Coalesce touch events while keeping the last appearance visible during a resize.
        delay(120)
        stampError = null
        try {
            stamp = pe.gob.reniec.dnie.data.signing.AndroidStampPreview.stamp(context,page.width*current.placement.width,
                page.height*current.placement.height,name,dni,current,java.util.Date(previewTime))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { stamp = null; stampError = e.message ?: "No se pudo preparar el sello" }
        preparingStamp = false
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var snapGuides by remember(document.id) { mutableStateOf<StampPlacementGeometry.Snap?>(null) }
    fun applySnap(next: NormalizedStampPlacement) {
        val snapped = StampPlacementGeometry.snap(next)
        snapGuides = snapped
        draft = current.copy(placement = snapped.placement)
    }
    var pageText by remember(draft.pageNumber) { mutableStateOf(draft.pageNumber.toString()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        pe.gob.reniec.dnie.ui.design.FeatureScaffold("Colocar firma", "Vista previa PDF", modifier = Modifier.fillMaxSize(),
            onBack = onDismiss, actions = {
                TextButton(enabled = pageBitmap != null && !preparingStamp && stampError == null && stamp != null,
                    onClick = { onApply(draft) }) { Text("Aplicar") }
            }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).navigationBarsPadding().background(MaterialTheme.colorScheme.surfaceContainer)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(10.dp).fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(enabled = draft.pageNumber > 1, onClick = { draft = draft.copy(pageNumber = draft.pageNumber - 1) }) { Text("‹") }
                    Text("Pág. ${draft.pageNumber}/${document.pages.size}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    TextButton(enabled = draft.pageNumber < document.pages.size, onClick = { draft = draft.copy(pageNumber = draft.pageNumber + 1) }) { Text("›") }
                    listOf(SignaturePreset.TOP_LEFT to "↖",SignaturePreset.TOP_RIGHT to "↗",SignaturePreset.BOTTOM_LEFT to "↙",SignaturePreset.BOTTOM_RIGHT to "↘").forEach { (preset,label) ->
                        OutlinedButton(onClick={draft=current.copy(placement=StampPlacementGeometry.preset(current.placement,preset))}) { Text(label) }
                    }
                }
                }
                StampPageTargetSelector(draft.pageTarget, true) { choice -> draft = current.copy(pageTarget = choice,
                    pageNumber = when (choice) { StampPageTarget.FIRST_PAGE -> 1; StampPageTarget.LAST_PAGE -> document.pages.size; else -> current.pageNumber }) }
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
                        .drawBehind {
                            val guides = snapGuides
                            val guideColor = Color(0xFF004AC6).copy(alpha = .7f)
                            guides?.vertical?.let { x -> drawLine(guideColor, Offset(x * size.width, 0f), Offset(x * size.width, size.height.toFloat()), 1.dp.toPx()) }
                            guides?.horizontal?.let { y -> drawLine(guideColor, Offset(0f, y * size.height), Offset(size.width.toFloat(), y * size.height), 1.dp.toPx()) }
                        }
                        .pointerInput(document.id) {
                            detectTapGestures { point ->
                                if (size.width > 0 && size.height > 0) {
                                    val p = current.placement
                                    val x = point.x / size.width
                                    val y = point.y / size.height
                                    if (x !in p.left..(p.left + p.width) || y !in p.top..(p.top + p.height))
                                        applySnap(p.moved(x - p.width / 2, y - p.height / 2))
                                }
                            }
                        }) {
                        pageBitmap?.let { Image(it.asImageBitmap(), "Página del documento", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                        if (pageBitmap == null) Text(error ?: "Preparando página…", color = Color.DarkGray, modifier = Modifier.padding(16.dp))
                        val p = draft.placement
                        Box(Modifier.offset { IntOffset((p.left * size.width).roundToInt(), (p.top * size.height).roundToInt()) }
                            .fillMaxWidth(p.width).fillMaxHeight(p.height).border(2.dp, Color(0xFF004AC6))
                            .semantics { contentDescription = "Sello: arrastrar para mover, pellizcar para cambiar tamaño" }
                            .pointerInput(document.id) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    if (size.width > 0 && size.height > 0) {
                                        val placement = current.placement
                                        applySnap(placement.transformed(zoom, pan.x / size.width, pan.y / size.height,
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
                                applySnap(placement.resizedAnchored(
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
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("Ancho ${(draft.placement.width * 100).toInt()}% · Alto ${(draft.placement.height * 100).toInt()}% · Página ${draft.pageNumber}", Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                        }
                        Text("Arrastra, pellizca o usa los puntos para ajustar el sello.", style = MaterialTheme.typography.bodySmall)
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
        Box(Modifier.size(13.dp).background(Color.White, CircleShape).border(2.dp, Color(0xFF004AC6), CircleShape))
    }
}
