package pe.gob.reniec.dnie.ui.vault

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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

/** Only visual transforms; decoding and access to plaintext remain in the data adapter. */
@Composable
internal fun VaultPreviewImage(bitmap: Bitmap, modifier: Modifier = Modifier) {
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Image(bitmap.asImageBitmap(), "Contenido del archivo", contentScale = ContentScale.Fit,
        modifier = modifier.onSizeChanged { size = it }.pointerInput(bitmap) {
            detectTransformGestures { _, pan, scale, _ ->
                zoom = (zoom * scale).coerceIn(1f, 5f)
                val limitX = size.width * (zoom - 1f) / 2f
                val limitY = size.height * (zoom - 1f) / 2f
                offset = Offset((offset.x + pan.x).coerceIn(-limitX, limitX),
                    (offset.y + pan.y).coerceIn(-limitY, limitY))
            }
        }.graphicsLayer {
            scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y
            clip = true
        })
}
