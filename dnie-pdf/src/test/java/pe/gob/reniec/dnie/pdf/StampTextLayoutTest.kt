package pe.gob.reniec.dnie.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Test

class StampTextLayoutTest {
    @Test fun longTextWrapsWithoutDroppingWordsOrAddingEllipsis() {
        val text = "Firmado por un titular con varios nombres y apellidos extensos"
        val lines = StampLayoutEngine.wrap(text,100f,false,9f) { value,_,size -> PDType1Font.HELVETICA.getStringWidth(value)*size/1000f }
        assertTrue(lines.size > 1)
        assertEquals(text, lines.joinToString(" "))
        assertTrue(lines.all { PDType1Font.HELVETICA.getStringWidth(it) / 1000 * 9f <= 100.001f })
    }
    @Test fun unbrokenWordsWrapWithoutLosingCharacters() {
        val word = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val lines = StampLayoutEngine.wrap(word,35f,false,9f) { value,_,size -> PDType1Font.HELVETICA.getStringWidth(value)*size/1000f }
        assertTrue(lines.size > 1); assertEquals(word, lines.joinToString("").replace("-",""))
        assertTrue(lines.all { PDType1Font.HELVETICA.getStringWidth(it) / 1000 * 9f <= 35.001f })
    }
    @Test fun tooSmallStampsFailInsteadOfSilentlyShrinkingToUnreadableText() {
        assertThrows(IllegalArgumentException::class.java) {
            StampLayoutEngine.layout(50f,30f,"NOMBRE COMPLETO DEL TITULAR","12345678",PdfSigningOptions(
                reason="Doy conformidad con todas las condiciones del documento y sus anexos"),0) { text,bold,size ->
                (if(bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA).getStringWidth(text)*size/1000f
            }
        }
    }
    @Test fun vectorPreviewUsesHelveticaAndPreservesLongFieldValues() {
        val name = "TITULAR CON NOMBRES Y APELLIDOS COMPLETOS"
        val reason = "Doy conformidad con las condiciones y con todos los anexos del contrato"
        val bytes = VisualStampRenderer.previewPdf(130f, 150f, name, "12345678", PdfSigningOptions(reason = reason))
        PDDocument.load(bytes).use { doc ->
            val text = PDFTextStripper().getText(doc).replace(Regex("\\s"), "")
            assertTrue(text.contains(name.replace(" ", "")))
            assertTrue(text.contains(reason.replace(" ", "")))
            assertTrue(doc.getPage(0).resources.fontNames.all { doc.getPage(0).resources.getFont(it).name.startsWith("Helvetica") })
        }
    }
    @Test fun pinchPreservesTheFingerCentroidAndMovesWithTheGesture() {
        val placement = NormalizedStampPlacement(.2f, .2f, .3f, .2f)
        val resized = placement.transformed(1.5f, .02f, .03f, .35f, .30f)
        assertEquals(.45f, resized.width, .0001f); assertEquals(.30f, resized.height, .0001f)
        assertEquals(.37f, resized.left + resized.width / 2, .0001f)
        assertEquals(.33f, resized.top + resized.height / 2, .0001f)
    }
    @Test fun edgeHandlesKeepTheOtherEdgeFixedAndClampToPage() {
        val placement = NormalizedStampPlacement(.56f, .84f, .4f, .12f)
        val resized = placement.resizedAnchored(.9f, .8f)
        assertEquals(placement.left, resized.left, 0f); assertEquals(placement.top, resized.top, 0f)
        assertEquals(1f, resized.left + resized.width, .0001f); assertEquals(1f, resized.top + resized.height, .0001f)
        val tiny = placement.resizedAnchored(-2f, -2f)
        assertEquals(.05f, tiny.width, 0f); assertEquals(.02f, tiny.height, 0f)
    }
    @Test fun handwrittenOnlyRequiresAnImageEvenWhenTheImageSwitchIsOff() {
        val appearance = SignatureAppearance(style = StampStyle.HANDWRITTEN_ONLY, showHandwritten = false)
        assertTrue(appearance.requiresHandwritten)
        assertTrue(runCatching { VisualStampRenderer.previewPdf(200f, 100f, "Titular", "12345678", PdfSigningOptions(appearance = appearance)) }.isFailure)
    }
}
