package pe.gob.reniec.dnie.timestamp

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date
import pe.gob.reniec.dnie.validation.*

fun interface TimestampCertificateTrust { fun verify(certificate: X509Certificate, time: Date) }

/** TSA role is checked by TokenValidator; trust requires current authenticated TSL ROOT5 and verified revocation. */
class ReniecTimestampTrust(private val lists: TrustedListSource = PeruTrustedListProvider(),
    private val source: PublicCertificateSource = CachedPublicCertificateSource()) : TimestampCertificateTrust {
    override fun verify(certificate: X509Certificate, time: Date) {
        val now = Date()
        val list = lists.current(now)
        val root = list.currentCertificateAuthorities(now).singleOrNull {
            MessageDigest.getInstance("SHA-256").digest(it.encoded).joinToString("") { b -> "%02x".format(b.toInt() and 255) } == ROOT5_SHA256
        } ?: error("Raíz TSA RENIEC no admitida en TSL actual")
        root.verify(root.publicKey)
        // Restrict this operation's anchors to the reviewed ROOT5 identity, not every document CA.
        val restricted = list.copy(services = list.services.map { service ->
            service.copy(identities = service.identities.filter { it.encoded.contentEquals(root.encoded) })
        })
        val report = CertificateTrustInspector(TrustedListSource { restricted }, source).inspect(certificate.encoded, false)
        require(report.trust == TrustStatus.TRUSTED && report.chainRevocation == RevocationStatus.GOOD &&
            report.chainValidUntil?.after(Date()) == true) { "No se pudo verificar confianza y revocación de la TSA RENIEC" }
        certificate.checkValidity(time)
    }
    companion object {
        const val ROOT5_SHA256 = "e33d6a09352f98ba5cc9e358d44f3ed737e8c73f29e56656f96aa05a9d34da60"
    }
}
