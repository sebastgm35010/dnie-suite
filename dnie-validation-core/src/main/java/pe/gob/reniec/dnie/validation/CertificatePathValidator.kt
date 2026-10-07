package pe.gob.reniec.dnie.validation

import java.security.cert.*
import java.util.Date

/** Trust anchors must be supplied by an authenticated policy, never inferred from a document. */
object CertificatePathValidator {
    fun validate(leaf: X509Certificate, intermediates: List<X509Certificate>,
        anchors: Set<TrustAnchor>, now: Date = Date()): TrustStatus {
        if (anchors.isEmpty()) return TrustStatus.UNKNOWN
        return try {
            val selector = X509CertSelector().apply { certificate = leaf }
            val params = PKIXBuilderParameters(anchors, selector).apply {
                date = now; isRevocationEnabled = false
                addCertStore(CertStore.getInstance("Collection", CollectionCertStoreParameters(intermediates + leaf)))
            }
            CertPathBuilder.getInstance("PKIX").build(params)
            TrustStatus.TRUSTED
        } catch (_: Exception) { TrustStatus.UNTRUSTED }
    }
}
