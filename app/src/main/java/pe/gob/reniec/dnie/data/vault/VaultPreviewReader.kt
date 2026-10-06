package pe.gob.reniec.dnie.data.vault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class VaultPreviewFrame(val bitmap: Bitmap? = null, val text: String? = null, val pages: Int = 1)

/** Bounded native decoding, never WebView or external applications. Called off the UI thread. */
class VaultPreviewReader(private val file: File, private val mime: String) {
    suspend fun read(page: Int): VaultPreviewFrame = withContext(Dispatchers.IO) {
        require(file.isFile && file.length() <= VaultPreviewFiles.MAX_BYTES) { "La vista previa ya se cerró" }
        when {
            mime == "application/pdf" -> ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(page.coerceIn(0, renderer.pageCount - 1)).use { sheet ->
                        val scale = minOf(1400f / sheet.width, 1800f / sheet.height)
                        val bitmap = Bitmap.createBitmap((sheet.width * scale).roundToInt().coerceAtLeast(1),
                            (sheet.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            sheet.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            VaultPreviewFrame(bitmap = bitmap, pages = renderer.pageCount)
                        } catch (failure: Throwable) { bitmap.recycle(); throw failure }
                    }
                }
            }
            mime.startsWith("image/") -> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Imagen no compatible" }
                var sample = 1
                while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1800) sample *= 2
                val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }))
                VaultPreviewFrame(bitmap = bitmap)
            }
            mime.startsWith("text/") -> {
                val limit = 256 * 1024
                val bytes = file.inputStream().use { it.readBytesBounded(limit) }
                VaultPreviewFrame(text = bytes.toString(Charsets.UTF_8) + if (file.length() > limit) "\n\n[Vista limitada a 256 KiB]" else "")
            }
            else -> error("Formato no compatible")
        }
    }
    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (count < 0) break
            check(count > 0); out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}
