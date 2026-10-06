package pe.gob.reniec.dnie.ui.signer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pe.gob.reniec.dnie.pdf.*
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class HandwrittenAppearanceTest {
    @Test fun handwrittenOnlyHasNoTextFontBorderOrAddedBackground() {
        val image = Bitmap.createBitmap(128, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val png = try { ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() } finally { image.recycle() }
        val bytes = VisualStampRenderer.previewPdf(300f, 100f, "TITULAR", "12345678",
            PdfSigningOptions(appearance = SignatureAppearance(style = StampStyle.HANDWRITTEN_ONLY, handwrittenPng = png)))
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(bytes).use { document ->
            org.junit.Assert.assertEquals("", com.tom_roush.pdfbox.text.PDFTextStripper().getText(document).trim())
            val page = document.getPage(0)
            org.junit.Assert.assertFalse(page.resources.fontNames.iterator().hasNext())
            assertTrue(page.resources.xObjectNames.iterator().hasNext())
            val contents = page.contents.bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
            org.junit.Assert.assertFalse(Regex("\\bre\\b").containsMatchIn(contents))
            org.junit.Assert.assertFalse(Regex("\\bBT\\b").containsMatchIn(contents))
        }
    }
    @Test fun pngFromIcaoCanBeEmbeddedAndRenderedInStampPreview() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = Bitmap.createBitmap(128, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val png = try { ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() } finally { image.recycle() }
        val options = PdfSigningOptions(appearance = SignatureAppearance(style = StampStyle.INK, showHandwritten = true, handwrittenPng = png))
        val file = File.createTempFile("test_stamp_", ".pdf", context.cacheDir)
        try {
            file.writeBytes(VisualStampRenderer.previewPdf(300f, 100f, "TITULAR", "12345678", options))
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer -> renderer.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
                try {
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    assertTrue(pixels.count { Color.green(it) > 220 && Color.red(it) < 30 && Color.blue(it) < 30 } > 100)
                } finally { bitmap.recycle() }
            } } }
        } finally { file.delete() }
    }
}
