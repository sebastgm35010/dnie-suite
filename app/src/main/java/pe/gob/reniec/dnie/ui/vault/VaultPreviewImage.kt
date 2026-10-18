package pe.gob.reniec.dnie.ui.vault

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

/** Only visual transforms; decoding and access to plaintext remain in the data adapter. */
@Composable
internal fun VaultPreviewImage(bitmap: Bitmap, modifier: Modifier = Modifier) {
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var size by remember(bitmap) { mutableStateOf(IntSize.Zero) }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    Image(
        bitmap = imageBitmap,
        contentDescription = "Contenido del archivo",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(bitmap) {
                detectTapGestures(
                    onDoubleTap = {
                        if (zoom > 1f) {
                            zoom = 1f
                            offset = Offset.Zero
                        } else {
                            zoom = 2.5f
                        }
                    }
                )
            }
            .pointerInput(bitmap) {
                detectTransformGestures { _, pan, scale, _ ->
                    val nextZoom = (zoom * scale).coerceIn(1f, 5f)
                    zoom = nextZoom
                    if (nextZoom <= 1f || size.width <= 0 || size.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) {
                        offset = Offset.Zero
                    } else {
                        val fitScale = min(size.width.toFloat() / bitmap.width.toFloat(), size.height.toFloat() / bitmap.height.toFloat())
                        val fittedWidth = bitmap.width * fitScale
                        val fittedHeight = bitmap.height * fitScale
                        val limitX = ((fittedWidth * nextZoom - size.width).coerceAtLeast(0f)) / 2f
                        val limitY = ((fittedHeight * nextZoom - size.height).coerceAtLeast(0f)) / 2f
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-limitX, limitX),
                            y = (offset.y + pan.y).coerceIn(-limitY, limitY)
                        )
                    }
                }
            }
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = offset.x
                translationY = offset.y
                clip = true
            }
    )
}
