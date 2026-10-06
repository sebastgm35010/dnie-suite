package pe.gob.reniec.dnie.ui.identity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.gemalto.jp2.JP2Decoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.icao.IcaoImage

/** Keep Android/image codecs out of the JVM ICAO reader and bound decoded allocations. */
object IdentityImageDecoder {
    suspend fun decode(image: IcaoImage): Bitmap = withContext(Dispatchers.Default) {
        if (image.mimeType == "image/jp2") {
            val decoder = JP2Decoder(image.bytes)
            val header = requireNotNull(decoder.readHeader()) { "Cabecera JPEG2000 no reconocida" }
            checkDimensions(header.width, header.height)
            var skip = 0
            while (maxOf(header.width, header.height) shr skip > 1200 && skip + 1 < header.numResolutions) skip++
            requireNotNull(decoder.setSkipResolutions(skip).decode()) { "No se pudo decodificar JPEG2000" }
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, options)
            checkDimensions(options.outWidth, options.outHeight)
            options.inJustDecodeBounds = false
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1200) options.inSampleSize *= 2
            requireNotNull(BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, options)) { "Imagen JPEG inválida" }
        }
    }

    private fun checkDimensions(width: Int, height: Int) {
        require(width > 0 && height > 0 && width.toLong() * height <= 4_000_000) { "Dimensiones de imagen fuera de límites" }
    }
}
