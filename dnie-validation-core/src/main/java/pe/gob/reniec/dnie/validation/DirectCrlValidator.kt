package pe.gob.reniec.dnie.validation

import java.security.cert.X509Certificate
import java.security.cert.X509CRL
import java.util.Date
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier

/** Complete, direct CRLs only. Unsupported scope/deltas return UNKNOWN, never GOOD. */
object DirectCrlValidator {
    fun evaluate(cert: X509Certificate, issuer: X509Certificate, crl: X509CRL,
        now: Date = Date()): RevocationAssessment {
        fun unknown(text: String) = RevocationAssessment(RevocationStatus.UNKNOWN, text)
        try {
            if (cert.issuerX500Principal != issuer.subjectX500Principal || crl.issuerX500Principal != issuer.subjectX500Principal)
                return unknown("Emisor de certificado o CRL distinto")
            if (issuer.basicConstraints < 0 || issuer.keyUsage?.getOrElse(6) { false } == false)
                return unknown("Emisor no autorizado para firmar CRL")
            if (crl.sigAlgOID !in setOf("1.2.840.113549.1.1.11", "1.2.840.113549.1.1.12", "1.2.840.113549.1.1.13",
                "1.2.840.10045.4.3.2", "1.2.840.10045.4.3.3", "1.2.840.10045.4.3.4"))
                return unknown("Algoritmo de firma CRL no admitido")
            if(!CertificateChecks.inspect(issuer,now,false).acceptable) return unknown("Emisor CRL no apto")
            issuer.checkValidity(now)
            cert.verify(issuer.publicKey)
            crl.verify(issuer.publicKey)
            val aki = crl.getExtensionValue("2.5.29.35")?.let {
                AuthorityKeyIdentifier.getInstance(ASN1OctetString.getInstance(it).octets).keyIdentifier }
            val ski = issuer.getExtensionValue("2.5.29.14")?.let {
                SubjectKeyIdentifier.getInstance(ASN1OctetString.getInstance(it).octets).keyIdentifier }
            if (aki != null && ski != null && !aki.contentEquals(ski)) return unknown("Clave emisora de CRL distinta")
            if (crl.getExtensionValue("2.5.29.27") != null || crl.getExtensionValue("2.5.29.28") != null ||
                crl.getExtensionValue("2.5.29.46") != null || cert.getExtensionValue("2.5.29.46") != null)
                return unknown("CRL delta, indirecta o particionada no soportada")
            if (crl.criticalExtensionOIDs.orEmpty().any { it != "2.5.29.35" && it != "2.5.29.20" })
                return unknown("Extensión crítica CRL no soportada")
            if (crl.thisUpdate.after(now) || crl.nextUpdate == null || !now.before(crl.nextUpdate) ||
                !crl.thisUpdate.before(crl.nextUpdate)) return unknown("CRL fuera de vigencia")
            if (CertificateLocations.completeCrlUrls(cert).isEmpty()) return unknown("No hay punto CRL directo de alcance completo")
            val entry = crl.getRevokedCertificate(cert.serialNumber)
            if (entry != null) {
                if (entry.certificateIssuer != null || entry.hasUnsupportedCriticalExtension() ||
                    entry.revocationDate.after(now) || entry.revocationReason == java.security.cert.CRLReason.REMOVE_FROM_CRL)
                    return unknown("Entrada de revocación no interpretable")
                return RevocationAssessment(RevocationStatus.REVOKED, "Certificado revocado por su emisor", crl.thisUpdate, crl.nextUpdate)
            }
            return RevocationAssessment(RevocationStatus.GOOD, "Sin entrada en CRL vigente y firma verificada", crl.thisUpdate, crl.nextUpdate)
        } catch (_: Exception) { return unknown("No se pudo verificar la CRL o su emisor") }
    }
}
