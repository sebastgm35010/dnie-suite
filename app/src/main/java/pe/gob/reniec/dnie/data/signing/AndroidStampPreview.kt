package pe.gob.reniec.dnie.data.signing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import pe.gob.reniec.dnie.pdf.*
import java.io.File
import java.util.Date
import kotlin.math.roundToInt

/** Bounded platform preview adapter, separate from Compose and cryptographic signing. */
object AndroidStampPreview {
    suspend fun page(file: File,index: Int,maxWidth: Int=1400): Bitmap=withContext(Dispatchers.IO) {
        require(file.length() in 1..256L*1024*1024) { "PDF fuera de límites" }
        currentCoroutineContext().ensureActive()
        val annotated=com.tom_roush.pdfbox.pdmodel.PDDocument.load(file,
            com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(32L*1024*1024).setTempDir(file.parentFile)).use { document ->
            require(index in 0 until document.numberOfPages)
            val page=document.getPage(index)
            if(page.annotations.isEmpty()) null else {
                val box=page.cropBox;val rotated=page.rotation%180!=0
                val width=if(rotated)box.height else box.width;val height=if(rotated)box.width else box.height
                require(width.isFinite() && height.isFinite() && width>0 && height>0)
                val scale=minOf(maxWidth.coerceIn(1,1800)/width,1800f/width,1800f/height)
                com.tom_roush.pdfbox.rendering.PDFRenderer(document).apply {setSubsamplingAllowed(true)}.renderImage(index,scale)
            }
        }
        val bitmap=annotated?:render(file,index,maxWidth.coerceIn(1,1800),false)
        try {currentCoroutineContext().ensureActive();bitmap}catch(cancelled: Exception){bitmap.recycle();throw cancelled}
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
