package pe.gob.reniec.dnie.validation

import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.Date

enum class RevocationMethod { NONE, OCSP, CRL }
data class CaRevocationEvidence(val depth: Int, val assessment: RevocationAssessment,
    val method: RevocationMethod, val diagnosticCode: String = "")

/** Issuer-bound CA evidence. Failures stay explicit; no names, serials or URLs in diagnostics. */
class CaRevocationInspector(private val source: PublicCertificateSource) {
    fun inspect(depth: Int, certificate: X509Certificate, issuer: X509Certificate,
        deadlineNanos: Long): CaRevocationEvidence {
        require(depth > 0)
        fun unavailable(code: String, method: RevocationMethod = RevocationMethod.NONE,
            detail: String = "No hay evidencia de revocación disponible para esta CA") =
            CaRevocationEvidence(depth, RevocationAssessment(RevocationStatus.UNKNOWN, detail), method, code)
        fun stopped(): CaRevocationEvidence? = when {
            Thread.currentThread().isInterrupted -> unavailable("CA_CANCELLED", detail = "Consulta de CA cancelada")
            System.nanoTime() > deadlineNanos -> unavailable("CA_DEADLINE", detail = "La consulta de CA alcanzó su límite de tiempo")
            else -> null
        }
        var last = unavailable("CA_NO_LOCATIONS")
        if (source is OcspResponseSource) for (url in CertificateLocations.ocspUrls(certificate)) {
            stopped()?.let { return it }
            last = try {
                val evidence = OcspValidator.evaluate(certificate, issuer,
                    source.query(url, OcspValidator.request(certificate, issuer)), Date())
                CaRevocationEvidence(depth, evidence, RevocationMethod.OCSP,
                    if (evidence.status == RevocationStatus.UNKNOWN) "CA_OCSP_UNVERIFIED" else "")
            } catch (failure: Exception) {
                unavailable("CA_OCSP_FETCH:${failure.javaClass.simpleName}", RevocationMethod.OCSP,
                    "No se pudo descargar o interpretar la respuesta OCSP de esta CA")
            }
            if (last.assessment.status != RevocationStatus.UNKNOWN) return last
        }
        for (url in CertificateLocations.completeCrlUrls(certificate)) {
            stopped()?.let { return it }
            val bytes = try { source.download(url) } catch (failure: Exception) {
                last = unavailable("CA_CRL_FETCH:${failure.javaClass.simpleName}", RevocationMethod.CRL,
                    "No se pudo descargar la CRL de esta CA")
                continue
            }
            last = try {
                val crl = CertificateFactory.getInstance("X.509").generateCRL(bytes.inputStream()) as X509CRL
                val evidence = DirectCrlValidator.evaluate(certificate, issuer, crl, Date())
                CaRevocationEvidence(depth, evidence, RevocationMethod.CRL,
                    if (evidence.status == RevocationStatus.UNKNOWN) "CA_CRL_UNVERIFIED" else "")
            } catch (failure: Exception) {
                unavailable("CA_CRL_PARSE:${failure.javaClass.simpleName}", RevocationMethod.CRL,
                    "No se pudo interpretar la CRL de esta CA")
            }
            if (last.assessment.status != RevocationStatus.UNKNOWN) return last
        }
        return last
    }
}
