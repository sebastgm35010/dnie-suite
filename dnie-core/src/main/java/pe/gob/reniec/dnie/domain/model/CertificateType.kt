package pe.gob.reniec.dnie.domain.model

/**
 * Tipos de certificados digitales X.509 públicos almacenados en el chip del DNIe.
 */
enum class CertificateType(
    val fileId: ByteArray,
    val displayName: String,
    val isMandatory: Boolean
) {
    SIGNATURE(
        fileId = byteArrayOf(0x00, 0x1D),
        displayName = "Firma Digital",
        isMandatory = true
    ),
    AUTHENTICATION(
        fileId = byteArrayOf(0x00, 0x1C),
        displayName = "Autenticación",
        isMandatory = true
    ),
    ENCRYPTION(
        fileId = byteArrayOf(0x00, 0x1B),
        displayName = "Cifrado",
        isMandatory = false
    );

    val fileIdHex: String
        get() = fileId.joinToString("") { "%02X".format(it) }

    companion object {
        fun fromFileId(fileId: ByteArray): CertificateType? {
            return entries.find { it.fileId.contentEquals(fileId) }
        }
    }
}
