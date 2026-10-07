package pe.gob.reniec.dnie.validation

import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.interfaces.ECPublicKey
import java.util.Date

object CertificateChecks {
    fun inspect(cert: X509Certificate, now: Date = Date(), forDocumentSignature: Boolean = true): CertificateAssessment {
        val issues = mutableListOf<String>()
        if (now.before(cert.notBefore)) issues += "Certificado todavía no vigente"
        if (!now.before(cert.notAfter)) issues += "Certificado vencido"
        if (forDocumentSignature) {
            if (cert.basicConstraints >= 0) issues += "El certificado es de una autoridad, no de un titular"
            cert.keyUsage?.let { usage ->
                if (!(usage.getOrElse(0) { false } || usage.getOrElse(1) { false }))
                    issues += "El certificado no permite firma digital"
            }
        }
        when (val key = cert.publicKey) {
                is RSAPublicKey -> if (key.modulus.bitLength() < 2048) issues += "Clave RSA menor de 2048 bits"
                is ECPublicKey -> if (key.params.curve.field.fieldSize < 256) issues += "Clave EC menor de 256 bits"
                else -> issues += "Algoritmo de clave no admitido"
        }
        return CertificateAssessment(issues.isEmpty(), issues)
    }
    fun requireForSigning(cert: X509Certificate) {
        val assessment = inspect(cert)
        require(assessment.acceptable) { assessment.issues.joinToString("; ") }
        KnownRevocationPolicy.requireNotKnownRevoked(cert)
    }
}
