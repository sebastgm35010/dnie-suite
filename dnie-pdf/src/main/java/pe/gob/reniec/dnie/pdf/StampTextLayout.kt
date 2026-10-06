package pe.gob.reniec.dnie.pdf

import com.tom_roush.pdfbox.pdmodel.font.PDType1Font

internal data class StampTextBlock(val text: String, val bold: Boolean = false, val small: Boolean = false)
internal data class StampTextLine(val text: String, val font: PDType1Font, val size: Float)

/** Wrap words (including long unbroken words), then fit all lines inside the chosen height. */
internal object StampTextLayout {
    fun wrap(text: String, font: PDType1Font, size: Float, width: Float): List<String> {
        require(size > 0 && width > 0)
        val result = mutableListOf<String>()
        var line = ""
        val words = VisualStampRenderer.sanitizeText(text).split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun fits(value: String) = font.getStringWidth(value) / 1000 * size <= width + .001f
        for (word in words) {
            if (line.isNotEmpty() && fits(line + " " + word)) { line += " " + word; continue }
            if (line.isNotEmpty()) { result += line; line = "" }
            var part = ""
            for (character in word) {
                if (part.isNotEmpty() && !fits(part + character)) { result += part; part = "" }
                part += character
            }
            line = part
        }
        if (line.isNotEmpty()) result += line
        return result
    }

    fun fit(blocks: List<StampTextBlock>, width: Float, height: Float): List<StampTextLine> {
        require(width > 0 && height > 0)
        fun lines(base: Float) = blocks.flatMap { block ->
            val font = if (block.bold) PDType1Font.COURIER_BOLD else PDType1Font.COURIER
            val size = base * if (block.small) .82f else 1f
            wrap(block.text, font, size, width).map { StampTextLine(it, font, size) }
        }
        var low = .1f
        var high = 9f
        repeat(20) {
            val candidate = (low + high) / 2
            if (lines(candidate).sumOf { (it.size * 1.35f).toDouble() } <= height) low = candidate else high = candidate
        }
        return lines(low)
    }
}
