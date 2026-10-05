package pe.gob.reniec.dnie.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    private val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy HH:mm:ss XXX", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("America/Lima")
    }

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
        date: Date = Date()
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
                renderStampContent(
                    contentStream = cs,
                    width = rect.width,
                    height = rect.height,
                    signerName = signerName,
                    signerDni = signerDni,
                    reason = reason,
                    location = location,
                    date = date
                )
            }

            val baos = ByteArrayOutputStream()
            templateDoc.save(baos)
            return ByteArrayInputStream(baos.toByteArray())
        } finally {
            templateDoc.close()
        }
    }

    /**
     * Dibuja los elementos vectoriales del sello: fondo, bordes, escudo DNIe y bloques tipográficos.
     */
    fun renderStampContent(
        contentStream: PDPageContentStream,
        width: Float,
        height: Float,
        signerName: String,
        signerDni: String,
        reason: String,
        location: String,
        date: Date
    ) {
        // 1. Fondo suave #F8F9FA
        contentStream.setNonStrokingColor(0.973f, 0.976f, 0.980f)
        contentStream.addRect(0f, 0f, width, height)
        contentStream.fill()

        // 2. Borde exterior #2C3E50 (Azul marino oscuro)
        contentStream.setStrokingColor(0.173f, 0.243f, 0.314f)
        contentStream.setLineWidth(0.75f)
        contentStream.addRect(0f, 0f, width, height)
        contentStream.stroke()

        // 3. Línea vertical divisoria entre el emblema y los datos
        val dividerX = 52f
        if (width > dividerX + 20f) {
            contentStream.setStrokingColor(0.824f, 0.843f, 0.863f)
            contentStream.setLineWidth(0.5f)
            contentStream.moveTo(dividerX, 4f)
            contentStream.lineTo(dividerX, height - 4f)
            contentStream.stroke()
        }

        // 4. Emblema institucional de DNIe / RENIEC (Vectorial)
        val sealCenterX = 26f
        val sealCenterY = height / 2f
        val radius = 17f

        // Círculo exterior en rojo patrio (#DA291C)
        contentStream.setStrokingColor(0.855f, 0.161f, 0.110f)
        contentStream.setLineWidth(1.0f)
        drawCircle(contentStream, sealCenterX, sealCenterY, radius)
        contentStream.stroke()

        // Texto institucional interior "DNIe"
        contentStream.setNonStrokingColor(0.855f, 0.161f, 0.110f)
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 7f)
        contentStream.newLineAtOffset(sealCenterX - 8f, sealCenterY + 4f)
        contentStream.showText("DNIe")
        contentStream.endText()

        // Subtexto "RENIEC"
        contentStream.setNonStrokingColor(0.173f, 0.243f, 0.314f)
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 4.5f)
        contentStream.newLineAtOffset(sealCenterX - 9f, sealCenterY - 4f)
        contentStream.showText("RENIEC")
        contentStream.endText()

        // Subtexto "FIRMA"
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA, 4.0f)
        contentStream.newLineAtOffset(sealCenterX - 7f, sealCenterY - 10f)
        contentStream.showText("FIRMA")
        contentStream.endText()

        // 5. Bloque de texto con los metadatos de la firma
        val textStartX = dividerX + 6f
        val availableTextWidth = width - textStartX - 4f

        val safeSignerName = truncateText(sanitizeText(signerName), availableTextWidth, 6.5f)
        val safeDni = sanitizeText(signerDni)
        val safeReason = truncateText(sanitizeText(reason), availableTextWidth, 5.5f)
        val safeLocation = truncateText(sanitizeText(location), availableTextWidth, 5.5f)
        val safeDate = sanitizeText(DATE_FORMAT.format(date))

        // Línea 1: Encabezado estándar
        contentStream.setNonStrokingColor(0.102f, 0.322f, 0.463f) // Azul institucional #1A5276
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 6.5f)
        contentStream.newLineAtOffset(textStartX, height - 12f)
        contentStream.showText("FIRMADO DIGITALMENTE POR:")
        contentStream.endText()

        // Línea 2: Nombre del ciudadano
        contentStream.setNonStrokingColor(0.173f, 0.243f, 0.314f) // #2C3E50
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 6.5f)
        contentStream.newLineAtOffset(textStartX, height - 21f)
        contentStream.showText(safeSignerName)
        contentStream.endText()

        // Línea 3: DNI
        contentStream.setNonStrokingColor(0.204f, 0.286f, 0.369f) // #34495E
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA, 5.5f)
        contentStream.newLineAtOffset(textStartX, height - 30f)
        contentStream.showText("DNI: $safeDni")
        contentStream.endText()

        // Línea 4: Motivo
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA, 5.5f)
        contentStream.newLineAtOffset(textStartX, height - 39f)
        contentStream.showText("Motivo: $safeReason")
        contentStream.endText()

        // Línea 5: Lugar
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA, 5.5f)
        contentStream.newLineAtOffset(textStartX, height - 48f)
        contentStream.showText("Lugar: $safeLocation")
        contentStream.endText()

        // Línea 6: Fecha
        contentStream.setNonStrokingColor(0.498f, 0.549f, 0.553f) // #7F8C8D
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA, 5.0f)
        contentStream.newLineAtOffset(textStartX, height - 56f)
        contentStream.showText("Fecha: $safeDate")
        contentStream.endText()
    }

    /**
     * Dibuja un círculo suave mediante aproximación de curvas Bézier en el stream PDF.
     */
    private fun drawCircle(cs: PDPageContentStream, cx: Float, cy: Float, r: Float) {
        val k = 0.55228475f * r
        cs.moveTo(cx - r, cy)
        cs.curveTo(cx - r, cy + k, cx - k, cy + r, cx, cy + r)
        cs.curveTo(cx + k, cy + r, cx + r, cy + k, cx + r, cy)
        cs.curveTo(cx + r, cy - k, cx + k, cy - r, cx, cy - r)
        cs.curveTo(cx - k, cy - r, cx - r, cy - k, cx - r, cy)
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

    /**
     * Trunca el texto si excede el ancho disponible para evitar desbordamiento del sello.
     */
    private fun truncateText(text: String, maxWidth: Float, fontSize: Float): String {
        val approxCharWidth = fontSize * 0.55f
        val maxChars = (maxWidth / approxCharWidth).toInt()
        return if (text.length > maxChars && maxChars > 3) {
            text.take(maxChars - 3) + "..."
        } else {
            text
        }
    }
}
