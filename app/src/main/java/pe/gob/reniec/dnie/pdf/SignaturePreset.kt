package pe.gob.reniec.dnie.pdf

/**
 * Presets de posicionamiento visual y visibilidad para la firma digital PAdES en documentos PDF.
 */
enum class SignaturePreset(
    val displayName: String,
    val isVisible: Boolean
) {
    BOTTOM_RIGHT("Inferior Derecha", true),
    BOTTOM_LEFT("Inferior Izquierda", true),
    TOP_RIGHT("Superior Derecha", true),
    TOP_LEFT("Superior Izquierda", true),
    CUSTOM("Personalizado", true),
    INVISIBLE("Invisible", false)
}
