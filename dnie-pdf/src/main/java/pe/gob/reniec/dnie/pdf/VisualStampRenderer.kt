package pe.gob.reniec.dnie.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Date
import com.tom_roush.pdfbox.util.Matrix

/**
 * Motor de renderizado del estampado visual de firma digital PAdES conforme a la
 * normativa peruana (Presidencia del Consejo de Ministros - PCM y RENIEC).
 *
 * Se encarga de:
 * 1. Calcular el rectángulo delimitador ([PDRectangle]) según dimensiones de página, presets y rotación (/Rotate).
 * 2. Generar el flujo de apariencia visual con vectores nítidos (escudo DNIe, tipografía estructurada, bordes).
 * 3. Sanitizar cadenas de texto contra la codificación WinAnsi para evitar fallos de renderizado.
 */
object VisualStampRenderer {

    const val DEFAULT_STAMP_WIDTH = 220f
    const val DEFAULT_STAMP_HEIGHT = 65f
    const val DEFAULT_MARGIN = 20f


    /**
     * Calcula las coordenadas del rectángulo delimitador de la firma en el espacio PDF de la página.
     *
     * En PDF, el origen (0, 0) se ubica en la esquina inferior izquierda.
     * Si la página posee un atributo /Rotate (90°, 180°, 270°), las coordenadas son adaptadas
     * para que visualmente el sello quede ubicado donde el usuario espera.
     */
    fun calculateBoundingBox(
        page: PDPage,
        preset: SignaturePreset,
        customX: Float = 0f,
        customY: Float = 0f,
        stampWidth: Float = DEFAULT_STAMP_WIDTH,
        stampHeight: Float = DEFAULT_STAMP_HEIGHT,
        margin: Float = DEFAULT_MARGIN
    ): PDRectangle {
        if (preset == SignaturePreset.INVISIBLE) {
            return PDRectangle(0f, 0f, 0f, 0f)
        }

        val cropBox = page.cropBox ?: page.mediaBox ?: PDRectangle(0f, 0f, 595f, 842f)
        val rotation = ((page.rotation % 360) + 360) % 360

        val pageWidth = cropBox.width
        val pageHeight = cropBox.height
        val ox = cropBox.lowerLeftX
        val oy = cropBox.lowerLeftY

        // Dimensiones efectivas visibles
        val visualWidth = if (rotation == 90 || rotation == 270) pageHeight else pageWidth
        val visualHeight = if (rotation == 90 || rotation == 270) pageWidth else pageHeight

        // Coordenadas relativas en espacio visual (0,0 en esquina inferior izquierda visible)
        val (vx, vy) = when (preset) {
            SignaturePreset.BOTTOM_RIGHT -> Pair(visualWidth - stampWidth - margin, margin)
            SignaturePreset.BOTTOM_LEFT -> Pair(margin, margin)
            SignaturePreset.TOP_RIGHT -> Pair(visualWidth - stampWidth - margin, visualHeight - stampHeight - margin)
            SignaturePreset.TOP_LEFT -> Pair(margin, visualHeight - stampHeight - margin)
            SignaturePreset.CUSTOM -> Pair(customX, customY)
            SignaturePreset.INVISIBLE -> Pair(0f, 0f)
        }

        // Mapear del espacio visual al espacio PDF nativo según la rotación
        val (finalX, finalY, finalW, finalH) = when (rotation) {
            90 -> {
                // Rotación de 90° horario: el eje X visual es el eje Y nativo, y el eje Y visual es (W - X) nativo
                val pdfX = ox + vy
                val pdfY = oy + (pageHeight - vx - stampWidth)
                listOf(pdfX, pdfY, stampHeight, stampWidth)
            }
            180 -> {
                // Rotación de 180°: invertido en ambos ejes
                val pdfX = ox + (pageWidth - vx - stampWidth)
                val pdfY = oy + (pageHeight - vy - stampHeight)
                listOf(pdfX, pdfY, stampWidth, stampHeight)
            }
            270 -> {
                // Rotación de 270° horario (90° antihorario)
                val pdfX = ox + (pageWidth - vy - stampHeight)
                val pdfY = oy + vx
                listOf(pdfX, pdfY, stampHeight, stampWidth)
            }
            else -> {
                // Rotación 0° estándar
                val pdfX = ox + vx
                val pdfY = oy + vy
                listOf(pdfX, pdfY, stampWidth, stampHeight)
            }
        }

        val clampedX = finalX.coerceIn(ox, ox + pageWidth - finalW)
        val clampedY = finalY.coerceIn(oy, oy + pageHeight - finalH)

        return PDRectangle(clampedX, clampedY, finalW, finalH)
    }

    /**
     * Construye un documento de plantilla con el formulario y la apariencia del estampado
     * para suministrar a [SignatureOptions.setVisualSignature].
     */
    fun createVisualSignatureTemplate(
        targetDoc: PDDocument,
        pageNumber: Int,
        rect: PDRectangle,
        signerName: String,
        signerDni: String,
        reason: String,
        location: String,
        date: Date = Date(),
        appearance: SignatureAppearance? = null
    ): InputStream {
        val templateDoc = PDDocument()
        try {
            val pageIndex = (pageNumber - 1).coerceIn(0, targetDoc.numberOfPages - 1)
            val targetPage = targetDoc.getPage(pageIndex)
            val templatePage = PDPage(targetPage.mediaBox)
            templateDoc.addPage(templatePage)

            val acroForm = PDAcroForm(templateDoc)
            templateDoc.documentCatalog.acroForm = acroForm

            val signatureField = PDSignatureField(acroForm)
            acroForm.fields.add(signatureField)

            val widget: PDAnnotationWidget = signatureField.widgets.first()
            widget.rectangle = rect
            widget.page = templatePage

            val apDict = PDAppearanceDictionary()
            val apStream = PDAppearanceStream(templateDoc)
            apStream.resources = PDResources()
            apStream.bBox = PDRectangle(rect.width, rect.height)
            apDict.setNormalAppearance(apStream)
            widget.appearance = apDict

            PDPageContentStream(templateDoc, apStream).use { cs ->
                val rotation = ((targetPage.rotation % 360) + 360) % 360
                val visualWidth = if (rotation == 90 || rotation == 270) rect.height else rect.width
                val visualHeight = if (rotation == 90 || rotation == 270) rect.width else rect.height
                when (rotation) {
                    90 -> cs.transform(Matrix(0f, 1f, -1f, 0f, rect.width, 0f))
                    180 -> cs.transform(Matrix(-1f, 0f, 0f, -1f, rect.width, rect.height))
                    270 -> cs.transform(Matrix(0f, -1f, 1f, 0f, 0f, rect.height))
                }
                ModernStampRenderer.draw(templateDoc, cs, visualWidth, visualHeight, signerName, signerDni, reason, location, date, appearance ?: SignatureAppearance())
            }

            val baos = ByteArrayOutputStream()
            templateDoc.save(baos)
            return ByteArrayInputStream(baos.toByteArray())
        } finally {
            templateDoc.close()
        }
    }

    /** Correct visual top-left mapping for crop offsets and all orthogonal PDF rotations. */
    fun normalizedBoundingBox(page: PDPage, placement: NormalizedStampPlacement): PDRectangle {
        val crop = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360
        require(rotation in setOf(0, 90, 180, 270)) { "Rotación PDF no compatible" }
        val visualW = if (rotation == 90 || rotation == 270) crop.height else crop.width
        val visualH = if (rotation == 90 || rotation == 270) crop.width else crop.height
        val x = placement.left * visualW
        val y = placement.top * visualH
        val w = placement.width * visualW
        val h = placement.height * visualH
        val native = when (rotation) {
            90 -> PDRectangle(y, x, h, w)
            180 -> PDRectangle(crop.width - x - w, y, w, h)
            270 -> PDRectangle(crop.width - y - h, crop.height - x - w, h, w)
            else -> PDRectangle(x, crop.height - y - h, w, h)
        }
        native.lowerLeftX += crop.lowerLeftX; native.upperRightX += crop.lowerLeftX
        native.lowerLeftY += crop.lowerLeftY; native.upperRightY += crop.lowerLeftY
        return native
    }

    fun previewPdf(width: Float, height: Float, name: String, dni: String,
                   options: PdfSigningOptions, date: Date = Date()): ByteArray {
        return PDDocument().use { doc ->
            val page = PDPage(PDRectangle(width, height)); doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                ModernStampRenderer.draw(doc, cs, width, height, name, dni, options.reason, options.location, date, options.appearance, options.reniecTimestamp)
            }
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
    }

    /**
     * Sanitiza caracteres Unicode que no están soportados por WinAnsiEncoding en PDType1Font.
     */
    fun sanitizeText(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            if (ch.code in 32..126 || ch in "áéíóúÁÉÍÓÚñÑüÜ°¿¡,.-_/:() ") {
                sb.append(ch)
            } else {
                sb.append(' ')
            }
        }
        return sb.toString().trim()
    }

}
