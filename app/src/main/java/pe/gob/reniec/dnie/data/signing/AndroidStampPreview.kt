package pe.gob.reniec.dnie.data.signing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.pdf.*
import java.io.File
import java.util.Date
import kotlin.math.roundToInt

/** Bounded platform preview adapter, separate from Compose and cryptographic signing. */
object AndroidStampPreview {
    suspend fun page(file: File,index: Int,maxWidth: Int=1400): Bitmap=withContext(Dispatchers.IO) {
        render(file,index,maxWidth,false)
    }
    suspend fun stamp(context: Context,width: Float,height: Float,name: String,dni: String,
        options: PdfSigningOptions,date: Date): Bitmap=withContext(Dispatchers.IO) {
        if(options.appearance.style==StampStyle.HANDWRITTEN_ONLY) {
            val bytes=requireNotNull(options.appearance.handwrittenPng) { "Lee la firma manuscrita para previsualizarla" }
            requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size)) { "Imagen manuscrita inválida" }
        } else {
            val file=File.createTempFile("stamp_preview_",".pdf",context.cacheDir)
            try { file.writeBytes(VisualStampRenderer.previewPdf(width,height,name,dni,options,date));render(file,0,800,true) }
            finally { file.delete() }
        }
    }
    private fun render(file: File,index: Int,maxWidth: Int,transparent: Boolean): Bitmap =
        ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.openPage(index).use { page ->
                val width=maxWidth.coerceAtMost((1800f*page.width/page.height).roundToInt()).coerceAtLeast(1)
                val height=(width.toFloat()*page.height/page.width).roundToInt().coerceAtLeast(1)
                Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(if(transparent) android.graphics.Color.TRANSPARENT else android.graphics.Color.WHITE)
                    page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            } }
        }
}
