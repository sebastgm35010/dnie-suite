package pe.gob.reniec.dnie.domain.model

import pe.gob.reniec.dnie.pdf.SignaturePreset

/**
 * Parámetros de solicitud para el proceso de firma digital (PDF o hash crudo).
 *
 * @property documentUri URI o ruta del documento PDF a firmar.
 * @property rawHash Resumen criptográfico SHA-256 (32 bytes) si se firma un hash directamente.
 * @property preset Ubicación visual predefinida ([SignaturePreset]).
 * @property reason Motivo de la firma (estándar Perú: ej. "Soy el autor del documento").
 * @property location Lugar geográfico de la firma (ej. "Lima, Perú").
 * @property pageNumber Número de página donde estampar la firma visible (1-indexado).
 * @property customX Coordenada X personalizada en puntos tipográficos (pt).
 * @property customY Coordenada Y personalizada en puntos tipográficos (pt).
 * @property customWidth Ancho del estampado personalizado en pt.
 * @property customHeight Alto del estampado personalizado en pt.
 */
data class SigningRequest(
    val documentUri: String? = null,
    val rawHash: ByteArray? = null,
    val preset: SignaturePreset = SignaturePreset.BOTTOM_RIGHT,
    val reason: String = "Soy el autor del documento",
    val location: String = "Lima, Perú",
    val pageNumber: Int = 1,
    val customX: Float = 0f,
    val customY: Float = 0f,
    val customWidth: Float = 220f,
    val customHeight: Float = 65f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SigningRequest

        if (documentUri != other.documentUri) return false
        if (rawHash != null) {
            if (other.rawHash == null) return false
            if (!rawHash.contentEquals(other.rawHash)) return false
        } else if (other.rawHash != null) return false
        if (preset != other.preset) return false
        if (reason != other.reason) return false
        if (location != other.location) return false
        if (pageNumber != other.pageNumber) return false
        if (customX != other.customX) return false
        if (customY != other.customY) return false
        if (customWidth != other.customWidth) return false
        if (customHeight != other.customHeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = documentUri?.hashCode() ?: 0
        result = 31 * result + (rawHash?.contentHashCode() ?: 0)
        result = 31 * result + preset.hashCode()
        result = 31 * result + reason.hashCode()
        result = 31 * result + location.hashCode()
        result = 31 * result + pageNumber
        result = 31 * result + customX.hashCode()
        result = 31 * result + customY.hashCode()
        result = 31 * result + customWidth.hashCode()
        result = 31 * result + customHeight.hashCode()
        return result
    }
}
