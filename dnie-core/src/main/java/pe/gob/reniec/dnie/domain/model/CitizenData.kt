package pe.gob.reniec.dnie.domain.model

/**
 * Datos de identidad del ciudadano extraídos de su certificado digital DNIe.
 *
 * @property fullName Nombre completo del ciudadano (normalizado).
 * @property dniNumber Número de DNI de 8 dígitos.
 * @property email Correo electrónico (si está presente en el certificado).
 * @property organization Organización o entidad emisora (ej. RENIEC).
 */
data class CitizenData(
    val fullName: String,
    val dniNumber: String,
    val email: String? = null,
    val organization: String? = null
)
