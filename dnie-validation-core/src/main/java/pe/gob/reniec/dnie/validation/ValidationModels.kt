package pe.gob.reniec.dnie.validation

import java.util.Date

enum class RevocationStatus { GOOD, REVOKED, UNKNOWN }
enum class TrustStatus { TRUSTED, UNTRUSTED, UNKNOWN }
data class CertificateAssessment(val acceptable: Boolean, val issues: List<String>)
data class RevocationAssessment(val status: RevocationStatus, val detail: String,
    val thisUpdate: Date? = null, val nextUpdate: Date? = null)
data class CertificateValidationReport(val certificate: CertificateAssessment,
    val revocation: RevocationAssessment, val trust: TrustStatus, val checkedAt: Date)
