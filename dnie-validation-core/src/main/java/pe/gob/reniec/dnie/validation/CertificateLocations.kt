package pe.gob.reniec.dnie.validation

import java.security.cert.X509Certificate
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.x509.*

object CertificateLocations {
    internal fun extension(cert: X509Certificate, oid: String): ByteArray? =
        cert.getExtensionValue(oid)?.let { ASN1OctetString.getInstance(it).octets }
    fun issuerUrls(cert: X509Certificate): List<String> = extension(cert, "1.3.6.1.5.5.7.1.1")?.let {
        AuthorityInformationAccess.getInstance(it).accessDescriptions.filter {
            it.accessMethod == AccessDescription.id_ad_caIssuers && it.accessLocation.tagNo == GeneralName.uniformResourceIdentifier
        }.map { it.accessLocation.name.toString() }.distinct().take(2)
    }.orEmpty()
    fun ocspUrls(cert: X509Certificate): List<String> = extension(cert,"1.3.6.1.5.5.7.1.1")?.let {
        AuthorityInformationAccess.getInstance(it).accessDescriptions.filter {
            it.accessMethod == AccessDescription.id_ad_ocsp && it.accessLocation.tagNo == GeneralName.uniformResourceIdentifier
        }.map { it.accessLocation.name.toString() }.distinct().take(2)
    }.orEmpty()
    fun completeCrlUrls(cert: X509Certificate): List<String> = extension(cert, "2.5.29.31")?.let {
        CRLDistPoint.getInstance(it).distributionPoints.filter {
            it.reasons == null && it.crlIssuer == null && it.distributionPoint?.type == DistributionPointName.FULL_NAME
        }.flatMap { GeneralNames.getInstance(it.distributionPoint.name).names.toList() }
            .filter { it.tagNo == GeneralName.uniformResourceIdentifier }.map { it.name.toString() }.distinct().take(2)
    }.orEmpty()
}
