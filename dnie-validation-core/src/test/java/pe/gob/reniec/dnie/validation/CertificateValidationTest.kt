package pe.gob.reniec.dnie.validation

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.cert.*
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.*
import org.bouncycastle.cert.X509v2CRLBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.*
import org.junit.Test

class CertificateValidationTest {
    companion object {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val otherKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }
    private val now = Date()
    private val name = X500Name("CN=Synthetic CA")
    private val signer get() = JcaContentSignerBuilder("SHA256withRSA").build(keys.private)
    internal fun cert(ca: Boolean = false, usage: Int = KeyUsage.digitalSignature,
        start: Long = now.time - 100000, end: Long = now.time + 1000000): X509Certificate {
        val builder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(if(ca) 1 else 2), Date(start), Date(end),
            if(ca) name else X500Name("CN=Synthetic Holder"), keys.public)
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(if(ca) KeyUsage.keyCertSign or KeyUsage.cRLSign else usage))
        builder.addExtension(Extension.authorityInfoAccess,false,AuthorityInformationAccess(AccessDescription(AccessDescription.id_ad_caIssuers,GeneralName(GeneralName.uniformResourceIdentifier,"http://www.reniec.gob.pe/crt/sha2/test.crt"))))
        builder.addExtension(Extension.cRLDistributionPoints, false, CRLDistPoint(arrayOf(DistributionPoint(
            DistributionPointName(GeneralNames(GeneralName(GeneralName.uniformResourceIdentifier,"http://crl.reniec.gob.pe/crl/sha2/test.crl"))),null,null))))
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }
    private fun crl(revoked: Boolean = false, stale: Boolean = false, forged: Boolean = false,
        delta: Boolean = false, scope: Boolean = false, future: Boolean = false): X509CRL {
        val builder = X509v2CRLBuilder(name, Date(now.time + if(future) 100000 else -10000))
        builder.setNextUpdate(Date(now.time + if(stale) -1000 else 500000))
        if(revoked) builder.addCRLEntry(BigInteger.valueOf(2),Date(now.time-5000),1)
        if(delta) builder.addExtension(Extension.deltaCRLIndicator,true,org.bouncycastle.asn1.ASN1Integer(1))
        if(scope) builder.addExtension(Extension.issuingDistributionPoint,true,IssuingDistributionPoint(null,false,false,null,false,false))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(if(forged) otherKeys.private else keys.private)
        return JcaX509CRLConverter().getCRL(builder.build(signer))
    }
    @Test fun currentSignedCompleteCrlReturnsGoodWithoutImplyingTrust() {
        assertEquals(RevocationStatus.GOOD,DirectCrlValidator.evaluate(cert(),cert(true),crl(),now).status)
        assertEquals(TrustStatus.UNKNOWN,CertificatePathValidator.validate(cert(),listOf(cert(true)),emptySet(),now))
    }
    @Test fun revokedCertificateIsRejectedByVerifiedCrl() {
        assertEquals(RevocationStatus.REVOKED,DirectCrlValidator.evaluate(cert(),cert(true),crl(revoked=true),now).status)
    }
    @Test fun staleFutureForgedDeltaAndScopedCrlNeverReturnGood() {
        listOf(crl(stale=true),crl(future=true),crl(forged=true),crl(delta=true),crl(scope=true)).forEach {
            assertEquals(RevocationStatus.UNKNOWN,DirectCrlValidator.evaluate(cert(),cert(true),it,now).status)
        }
    }
    @Test fun expiryNotYetValidWrongUsageAndCaCannotSignAsHolder() {
        listOf(cert(end=now.time-1),cert(start=now.time+5000),cert(usage=KeyUsage.keyEncipherment),cert(ca=true)).forEach {
            assertFalse(CertificateChecks.inspect(it,now).acceptable)
        }
        assertTrue(CertificateChecks.inspect(cert(usage=KeyUsage.nonRepudiation),now).acceptable)
    }
    @Test fun wrongIssuerCannotAuthenticateRevocation() {
        val other = JcaX509v3CertificateBuilder(X500Name("CN=Other CA"),BigInteger.ONE,Date(0),Date(now.time+1000000),
            X500Name("CN=Other CA"),otherKeys.public).build(JcaContentSignerBuilder("SHA256withRSA").build(otherKeys.private))
        assertEquals(RevocationStatus.UNKNOWN,DirectCrlValidator.evaluate(cert(),JcaX509CertificateConverter().getCertificate(other),crl(),now).status)
    }
    @Test fun anchorMustBeExplicitAndWrongAnchorIsRejected() {
        val ca = cert(true)
        assertEquals(TrustStatus.TRUSTED,CertificatePathValidator.validate(cert(),listOf(ca),setOf(TrustAnchor(ca,null)),now))
        val other = JcaX509v3CertificateBuilder(name,BigInteger.TEN,Date(0),Date(now.time+1000000),name,otherKeys.public)
            .build(JcaContentSignerBuilder("SHA256withRSA").build(otherKeys.private))
        assertEquals(TrustStatus.UNTRUSTED,CertificatePathValidator.validate(cert(),listOf(ca),
            setOf(TrustAnchor(JcaX509CertificateConverter().getCertificate(other),null)),now))
    }
    @Test fun sourcePolicyRejectsLocalAddressesCredentialsRedirectHostsAndEncodedTraversal() {
        assertTrue(ReniecPublicSource.allowed("http://crl.reniec.gob.pe/crl/sha2/caclass2.crl"))
        assertEquals("https://cdn.www.gob.pe/ecernep/root5/crt/ecpsva5.crt",
            ReniecPublicSource.transportUrl("http://cdn.www.gob.pe/ecernep/root5/crt/ecpsva5.crt"))
        assertFalse(ReniecPublicSource.allowed("https://cdn.www.gob.pe/ecernep/root5/crt/unknown.crt"))
        assertFalse(ReniecPublicSource.allowed("https://cdn.www.gob.pe/other/secret"))
        listOf("http://127.0.0.1/crl/sha2/a", "http://crl.reniec.gob.pe.evil.test/crl/sha2/a",
            "http://user@crl.reniec.gob.pe/crl/sha2/a", "http://crl.reniec.gob.pe:8080/crl/sha2/a",
            "http://crl.reniec.gob.pe/crl/sha2/%2e%2e/a").forEach { assertFalse(ReniecPublicSource.allowed(it)) }
    }
    @Test fun networkFailureIsUnknownNeverGood() {
        var requests = 0
        assertEquals(RevocationStatus.UNKNOWN, OnlineRevocationInspector { requests++; throw java.io.IOException("Offline") }.inspect(cert().encoded).revocation.status)
        assertEquals(1, requests)
    }
    @Test fun onlineInspectionVerifiesIssuerAndCrlButDoesNotTrustDownloadedCa() {
        val ca = cert(true); val list = crl()
        val report = OnlineRevocationInspector { url -> if(url.endsWith(".crt")) ca.encoded else list.encoded }.inspect(cert().encoded)
        assertEquals(RevocationStatus.GOOD,report.revocation.status)
        assertEquals(TrustStatus.UNKNOWN,report.trust)
    }

    @Test fun authenticatedRevocationBlocksSigningAndUnknownCannotEraseIt() {
        val leaf = cert(start=now.time-180000,end=now.time+1800000)
        KnownRevocationPolicy.recordVerified(leaf,RevocationStatus.REVOKED)
        KnownRevocationPolicy.recordVerified(leaf,RevocationStatus.UNKNOWN)
        assertTrue(runCatching { CertificateChecks.requireForSigning(leaf) }.exceptionOrNull() is IllegalArgumentException)
        KnownRevocationPolicy.recordVerified(leaf,RevocationStatus.GOOD)
        CertificateChecks.requireForSigning(leaf)
    }

}
