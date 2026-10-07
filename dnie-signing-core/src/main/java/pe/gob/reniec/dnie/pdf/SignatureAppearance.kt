package pe.gob.reniec.dnie.pdf

enum class StampStyle(val label: String) {
    CARD("Tarjeta"), MINIMAL("Minimalista"), INK("Manuscrita + datos"), HANDWRITTEN_ONLY("Solo manuscrita")
}

data class SignatureAppearance(
    val style: StampStyle = StampStyle.CARD,
    val showDni: Boolean = true,
    val showDate: Boolean = true,
    val showTime: Boolean = true,
    val showReason: Boolean = true,
    val showLocation: Boolean = true,
    val showHandwritten: Boolean = false,
    val handwrittenPng: ByteArray? = null
) {
    val requiresHandwritten: Boolean get() = showHandwritten || style == StampStyle.HANDWRITTEN_ONLY
}

/** Coordinates in the visible cropped/rotated page, measured from its TOP LEFT. */
data class NormalizedStampPlacement(
    val left: Float = 0.56f, val top: Float = 0.84f,
    val width: Float = 0.40f, val height: Float = 0.12f
) {
    init {
        require(listOf(left, top, width, height).all { it.isFinite() })
        require(width in 0.05f..1f && height in 0.02f..1f)
        require(left >= 0 && top >= 0 && left + width <= 1.00001f && top + height <= 1.00001f)
    }
    fun moved(x: Float, y: Float) = copy(left = x.coerceIn(0f, 1f - width), top = y.coerceIn(0f, 1f - height))
    fun resized(w: Float, h: Float): NormalizedStampPlacement {
        val nw = w.coerceIn(0.05f, 1f)
        val nh = h.coerceIn(0.02f, 1f)
        return copy(left = left.coerceAtMost(1f - nw), top = top.coerceAtMost(1f - nh), width = nw, height = nh)
    }
    /** Edge/corner handles keep the opposite edge fixed. */
    fun resizedAnchored(w: Float, h: Float) = copy(width = w.coerceIn(0.05f, 1f - left), height = h.coerceIn(0.02f, 1f - top))

    /** Pinch around a finger centroid, with pan expressed in visible-page fractions. */
    fun transformed(zoom: Float, panX: Float, panY: Float, anchorX: Float, anchorY: Float): NormalizedStampPlacement {
        require(zoom.isFinite() && zoom > 0 && listOf(panX, panY, anchorX, anchorY).all { it.isFinite() })
        val w = (width * zoom).coerceIn(0.05f, 1f)
        val h = (height * zoom).coerceIn(0.02f, 1f)
        val x = anchorX - (anchorX - left) * w / width + panX
        val y = anchorY - (anchorY - top) * h / height + panY
        return NormalizedStampPlacement(x.coerceIn(0f, 1f - w), y.coerceIn(0f, 1f - h), w, h)
    }
}

data class PdfSigningOptions(
    val pageNumber: Int = 1,
    val visible: Boolean = true,
    val placement: NormalizedStampPlacement = NormalizedStampPlacement(),
    val appearance: SignatureAppearance = SignatureAppearance(),
    val reason: String = "Doy conformidad",
    val location: String = ""
)
