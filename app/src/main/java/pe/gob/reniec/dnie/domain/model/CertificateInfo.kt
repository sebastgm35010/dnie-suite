package pe.gob.reniec.dnie.domain.model

import java.util.Date

/**
 * Modelo de datos con metadatos detallados de un certificado digital X.509 de DNIe.
 */
data class CertificateInfo(
    val type: CertificateType,
    val subjectDn: String,
    val issuerDn: String,
    val citizenName: String,
    val citizenDni: String,
    val serialNumberHex: String,
    val validFrom: Date,
    val validTo: Date,
    val isExpired: Boolean,
    val keyUsageDescription: String,
    val sha256FingerprintHex: String,
    val rawDerBytes: ByteArray,
    val sha1FingerprintHex: String = "",
    val daysRemaining: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CertificateInfo

        if (type != other.type) return false
        if (subjectDn != other.subjectDn) return false
        if (issuerDn != other.issuerDn) return false
        if (citizenName != other.citizenName) return false
        if (citizenDni != other.citizenDni) return false
        if (serialNumberHex != other.serialNumberHex) return false
        if (validFrom != other.validFrom) return false
        if (validTo != other.validTo) return false
        if (isExpired != other.isExpired) return false
        if (keyUsageDescription != other.keyUsageDescription) return false
        if (sha256FingerprintHex != other.sha256FingerprintHex) return false
        if (sha1FingerprintHex != other.sha1FingerprintHex) return false
        if (daysRemaining != other.daysRemaining) return false
        if (!rawDerBytes.contentEquals(other.rawDerBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + subjectDn.hashCode()
        result = 31 * result + issuerDn.hashCode()
        result = 31 * result + citizenName.hashCode()
        result = 31 * result + citizenDni.hashCode()
        result = 31 * result + serialNumberHex.hashCode()
        result = 31 * result + validFrom.hashCode()
        result = 31 * result + validTo.hashCode()
        result = 31 * result + isExpired.hashCode()
        result = 31 * result + keyUsageDescription.hashCode()
        result = 31 * result + sha256FingerprintHex.hashCode()
        result = 31 * result + sha1FingerprintHex.hashCode()
        result = 31 * result + daysRemaining.hashCode()
        result = 31 * result + rawDerBytes.contentHashCode()
        return result
    }
}
