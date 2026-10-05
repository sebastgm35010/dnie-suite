package pe.gob.reniec.dnie.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class VisualStampRendererTest {

    @Test
    fun testCoordinateCalculationUnrotatedPage() {
        val page = PDPage(PDRectangle(0f, 0f, 600f, 800f))
        page.rotation = 0

        // 1. BOTTOM_RIGHT
        val br = VisualStampRenderer.calculateBoundingBox(
            page = page,
            preset = SignaturePreset.BOTTOM_RIGHT,
            stampWidth = 220f,
            stampHeight = 65f,
            margin = 20f
        )
        assertEquals(360f, br.lowerLeftX, 0.01f) // 600 - 220 - 20 = 360
        assertEquals(20f, br.lowerLeftY, 0.01f)
        assertEquals(220f, br.width, 0.01f)
        assertEquals(65f, br.height, 0.01f)

        // 2. BOTTOM_LEFT
        val bl = VisualStampRenderer.calculateBoundingBox(
            page = page,
            preset = SignaturePreset.BOTTOM_LEFT,
            stampWidth = 220f,
            stampHeight = 65f,
            margin = 20f
        )
        assertEquals(20f, bl.lowerLeftX, 0.01f)
        assertEquals(20f, bl.lowerLeftY, 0.01f)

        // 3. TOP_RIGHT
        val tr = VisualStampRenderer.calculateBoundingBox(
            page = page,
            preset = SignaturePreset.TOP_RIGHT,
            stampWidth = 220f,
            stampHeight = 65f,
            margin = 20f
        )
        assertEquals(360f, tr.lowerLeftX, 0.01f)
        assertEquals(715f, tr.lowerLeftY, 0.01f) // 800 - 65 - 20 = 715

        // 4. TOP_LEFT
        val tl = VisualStampRenderer.calculateBoundingBox(
            page = page,
            preset = SignaturePreset.TOP_LEFT,
            stampWidth = 220f,
            stampHeight = 65f,
            margin = 20f
        )
        assertEquals(20f, tl.lowerLeftX, 0.01f)
        assertEquals(715f, tl.lowerLeftY, 0.01f)

        // 5. INVISIBLE
        val inv = VisualStampRenderer.calculateBoundingBox(
            page = page,
            preset = SignaturePreset.INVISIBLE
        )
        assertEquals(0f, inv.width, 0.01f)
        assertEquals(0f, inv.height, 0.01f)
    }

    @Test
    fun testCoordinateCalculationWithPageRotation() {
        val page = PDPage(PDRectangle(0f, 0f, 600f, 800f))
        page.rotation = 90

        val box90 = VisualStampRenderer.calculateBoundingBox(
            page = page,
            preset = SignaturePreset.BOTTOM_RIGHT,
            stampWidth = 220f,
            stampHeight = 65f,
            margin = 20f
        )
        assertNotNull(box90)
        assertTrue("Las coordenadas deben estar dentro del ancho de página", box90.lowerLeftX >= 0f)
        assertTrue("Las coordenadas deben estar dentro del alto de página", box90.lowerLeftY >= 0f)
    }

    @Test
    fun testCreateVisualSignatureTemplateGeneration() {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)

        val rect = PDRectangle(100f, 100f, 220f, 65f)
        val templateStream = VisualStampRenderer.createVisualSignatureTemplate(
            targetDoc = doc,
            pageNumber = 1,
            rect = rect,
            signerName = "QUISPE MAMANI, CARLOS ENRIQUE",
            signerDni = "47851234",
            reason = "Soy el autor del documento",
            location = "Lima, Perú",
            date = Date()
        )

        assertNotNull(templateStream)

        // Verificar que el template generado es un documento PDF válido
        val templateDoc = PDDocument.load(templateStream)
        assertEquals(1, templateDoc.numberOfPages)
        assertNotNull(templateDoc.documentCatalog.acroForm)
        val fields = templateDoc.documentCatalog.acroForm.fields
        assertEquals(1, fields.size)

        templateDoc.close()
        doc.close()
    }

    @Test
    fun testSanitizeTextForWinAnsi() {
        val textWithSpecial = "Carlos Quispe Peña - DNI: 47851234 (Lima, Perú)"
        val sanitized = VisualStampRenderer.sanitizeText(textWithSpecial)
        assertTrue(sanitized.contains("Carlos"))
        assertTrue(sanitized.contains("Peña"))
        assertTrue(sanitized.contains("Perú"))
    }
}
