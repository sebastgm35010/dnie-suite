package pe.gob.reniec.dnie.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Shared vector appearance: black ink, white card and serious monospaced lettering. */
internal object ModernStampRenderer {
    fun draw(doc: PDDocument, cs: PDPageContentStream, width: Float, height: Float,
             name: String, dni: String, reason: String, location: String, date: Date, appearance: SignatureAppearance) {
        require(width > 0 && height > 0)
        if (appearance.style == StampStyle.HANDWRITTEN_ONLY) {
            drawImage(doc, cs, appearance, 0f, 0f, width, height)
            return
        }
        val padding = minOf(9f, width * .04f, height * .10f)
        if (appearance.style == StampStyle.CARD) {
            cs.setNonStrokingColor(1f, 1f, 1f)
            cs.addRect(0f, 0f, width, height); cs.fill()
            cs.setStrokingColor(0f, 0f, 0f)
            cs.setLineWidth(.7f)
            cs.addRect(.35f, .35f, width - .7f, height - .7f); cs.stroke()
        } else {
            cs.setStrokingColor(0f, 0f, 0f)
            cs.setLineWidth(.6f)
            cs.moveTo(padding, .5f * padding); cs.lineTo(width - padding, .5f * padding); cs.stroke()
        }
        var textX = padding
        val usableHeight = height - 2 * padding
        if (appearance.requiresHandwritten) {
            val slot = width * if (appearance.style == StampStyle.INK) .40f else .30f
            drawImage(doc, cs, appearance, padding, padding, slot - padding, usableHeight)
            textX += slot
        }
        val widthForText = maxOf(1f, width - textX - padding)
        val blocks = buildList {
            add(StampTextBlock("FIRMADO DIGITALMENTE", bold = true, small = true))
            add(StampTextBlock(name, bold = true))
            if (appearance.showDni && dni.isNotBlank()) add(StampTextBlock("DNI " + dni, small = true))
            if (appearance.showReason && reason.isNotBlank()) add(StampTextBlock("Motivo: " + reason, small = true))
            if (appearance.showLocation && location.isNotBlank()) add(StampTextBlock("Lugar: " + location, small = true))
            val pattern = when {
                appearance.showDate && appearance.showTime -> "dd/MM/yyyy HH:mm:ss XXX"
                appearance.showDate -> "dd/MM/yyyy"
                appearance.showTime -> "HH:mm:ss XXX"
                else -> null
            }
            if (pattern != null) add(StampTextBlock(SimpleDateFormat(pattern, Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("America/Lima")
            }.format(date), small = true))
        }
        val layout = StampTextLayout.fit(blocks, widthForText, usableHeight)
        var y = height - padding
        cs.setNonStrokingColor(0f, 0f, 0f)
        layout.forEach { line ->
            y -= line.size
            cs.beginText(); cs.setFont(line.font, line.size); cs.newLineAtOffset(textX, y)
            cs.showText(line.text); cs.endText()
            y -= line.size * .35f
        }
    }

    private fun drawImage(doc: PDDocument, cs: PDPageContentStream, appearance: SignatureAppearance,
                          x: Float, y: Float, width: Float, height: Float) {
        val bytes = requireNotNull(appearance.handwrittenPng) { "Lea la firma manuscrita en Identidad antes de incluirla" }
        val image = PDImageXObject.createFromByteArray(doc, bytes, "Firma manuscrita del titular")
        val scale = minOf(width / image.width, height / image.height)
        val w = image.width * scale
        val h = image.height * scale
        cs.drawImage(image, x + (width - w) / 2, y + (height - h) / 2, w, h)
    }
}
