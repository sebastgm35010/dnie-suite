package pe.gob.reniec.dnie.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import pe.gob.reniec.dnie.data.signing.AndroidStampPreview
import pe.gob.reniec.dnie.pdf.*
import pe.gob.reniec.dnie.ui.signer.StampPageTargetSelector
import pe.gob.reniec.dnie.ui.signer.StampPreviewCard
import pe.gob.reniec.dnie.ui.theme.DnieTheme
import java.io.ByteArrayOutputStream
import java.util.Date

/** Requires an Android runtime: BitmapFactory/PdfRenderer are intentionally not mocked. */
class AetherStampPresentationTest {
    @get:Rule val rule=createComposeRule()
    private fun ink(): ByteArray {
        val bitmap=Bitmap.createBitmap(240,80,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawLine(20f,65f,210f,15f,Paint().apply { color=android.graphics.Color.BLACK;strokeWidth=4f })
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle() }.toByteArray()
    }
    @Test fun allStylesRenderIncludingRealHandwrittenBitmapAndTransparency()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        val mark=ink()
        for(style in StampStyle.entries) {
            val options=PdfSigningOptions(appearance=SignatureAppearance(style=style,showHandwritten=style==StampStyle.INK,handwrittenPng=mark))
            val bitmap=AndroidStampPreview.stamp(context,350f,140f,"ANA MARÍA QUISPE MAMANI","12345678",options,Date(0))
            assertTrue(bitmap.width>0 && bitmap.height>0)
            if(style==StampStyle.HANDWRITTEN_ONLY) assertEquals(0,android.graphics.Color.alpha(bitmap.getPixel(0,0)))
            bitmap.recycle()
        }
    }
    @Test fun pageTargetsAndInstantPreviewShareTheConfiguredAppearance() {
        val options=mutableStateOf(PdfSigningOptions())
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        rule.setContent { DnieTheme(darkTheme=false) { Column {
            StampPageTargetSelector(options.value.pageTarget,true) { options.value=options.value.copy(pageTarget=it) }
            StampPreviewCard(350f,140f,"ANA MARÍA QUISPE MAMANI","12345678",options.value)
        } } }
        rule.onNodeWithText("Todas las páginas").performClick().assertIsSelected()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Vista previa del sello configurado").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Vista previa del sello configurado").assertIsDisplayed()
        assertEquals(StampPageTarget.ALL_PAGES,options.value.pageTarget)
    }
}
