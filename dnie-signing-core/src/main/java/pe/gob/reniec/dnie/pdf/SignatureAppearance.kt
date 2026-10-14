package pe.gob.reniec.dnie.pdf

enum class StampStyle(val label: String) {
    CARD("Tarjeta clásica"), MINIMAL("Minimalista"), INK("Rúbrica + datos"), HANDWRITTEN_ONLY("Solo rúbrica"),
    INSTITUTIONAL("Institucional")
}

enum class StampColorTheme(val label: String, val accentRgb: Int, val backgroundRgb: Int,
    val borderRgb: Int, val titleRgb: Int, val bodyRgb: Int) {
    AETHER_BLUE("Azul Aether",0x004AC6,0xF8F9FF,0xB8CCF0,0x0B1C30,0x434655),
    CRIMSON_SEAL("Rojo institucional",0xB91C1C,0xFFFBFB,0xE5C4C4,0x1E293B,0x475569),
    EMERALD_TRUST("Esmeralda",0x006A63,0xF4FBF9,0xB2DFDB,0x00201D,0x37474F),
    SLATE_MONO("Monocromo impresión",0x1E293B,0xFFFFFF,0x64748B,0x0F172A,0x334155),
    ROYAL_INDIGO("Índigo notarial",0x3730A3,0xF5F6FF,0xC7D2FE,0x1E1B4B,0x312E81),
    AMBER_GOLD("Dorado ejecutivo",0x92400E,0xFFFBEB,0xFDE68A,0x451A03,0x57534E)
}

enum class StampBadgeType(val label: String) {
    SEAL_VECTOR("Escudo DNIe"), QR_VERIFICATION("QR de verificación"), NONE("Sin insignia"), CHECK_SEAL("Sello de verificación"), LOCK_SEAL("Candado")
}

enum class StampBackgroundMode(val label: String) { TINTED("Fondo suave"), SOLID_WHITE("Fondo blanco"), TRANSPARENT("Transparente") }
enum class StampDateFormat(val label: String,val datePattern: String,val timePattern: String) {
    CLASSIC_PE("Estándar", "dd/MM/yyyy", "HH:mm:ss 'UTC-05'"),
    COMPACT_HUMAN("Fecha corta", "dd MMM yyyy", "HH:mm"),
    ISO_8601("ISO 8601", "yyyy-MM-dd", "HH:mm:ssXXX")
}
enum class StampDensity(val label: String,val lineSpacing: Float) { COMPACT("Compacto",1.22f), NORMAL("Equilibrado",1.34f), SPACIOUS("Amplio",1.46f) }

enum class StampPageTarget(val label: String) {
    SPECIFIC_PAGE("Página seleccionada"), FIRST_PAGE("Primera página"), LAST_PAGE("Última página"), ALL_PAGES("Todas las páginas");
    fun pages(count: Int, selected: Int): List<Int> {
        require(count > 0) { "El PDF no tiene páginas" }
        return when(this) {
            FIRST_PAGE -> listOf(1)
            LAST_PAGE -> listOf(count)
            ALL_PAGES -> (1..count).toList()
            SPECIFIC_PAGE -> { require(selected in 1..count) { "La página seleccionada ya no existe" }; listOf(selected) }
        }
    }
}

data class SignatureAppearance(
    val style: StampStyle = StampStyle.INSTITUTIONAL,
    val showDni: Boolean = true,
    val showDate: Boolean = true,
    val showTime: Boolean = true,
    val showReason: Boolean = true,
    val showLocation: Boolean = true,
    val showHandwritten: Boolean = false,
    val handwrittenPng: ByteArray? = null,
    val colorTheme: StampColorTheme = StampColorTheme.AETHER_BLUE,
    val badge: StampBadgeType = StampBadgeType.SEAL_VECTOR,
    val customHeader: String = "FIRMADO DIGITALMENTE",
    val showTsaBadge: Boolean = true,
    val backgroundMode: StampBackgroundMode = StampBackgroundMode.TINTED,
    val dateFormat: StampDateFormat = StampDateFormat.CLASSIC_PE,
    val density: StampDensity = StampDensity.NORMAL,
    val showBorder: Boolean = true
) {
    val requiresHandwritten: Boolean get() = showHandwritten || style == StampStyle.INK || style == StampStyle.HANDWRITTEN_ONLY
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
    val location: String = "",
    val reniecTimestamp: Boolean = false,
    val pageTarget: StampPageTarget = StampPageTarget.SPECIFIC_PAGE,
    /** Per-document engine-derived data, never a persisted visual preference. Null means PREVIEW. */
    val qrMetadata: StampQrMetadata? = null
)
