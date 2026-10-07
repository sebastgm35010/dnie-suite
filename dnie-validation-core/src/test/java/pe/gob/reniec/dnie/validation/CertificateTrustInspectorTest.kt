package pe.gob.reniec.dnie.validation

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.X509v2CRLBuilder
import org.bouncycastle.cert.jcajce.*
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.*
import org.junit.Test

class CertificateTrustInspectorTest {
    private val now = Date()
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private fun name(value: String) = X500Name("CN=Synthetic $value")
    private fun cert(subject: String, issuer: String, serial: Long, ca: Boolean): X509Certificate {
        val builder = JcaX509v3CertificateBuilder(name(issuer), BigInteger.valueOf(serial), Date(now.time - 100000),
            Date(now.time + 1000000), name(subject), keys.public)
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(if (ca) KeyUsage.keyCertSign or KeyUsage.cRLSign else KeyUsage.nonRepudiation))
        builder.addExtension(Extension.authorityInfoAccess, false, AuthorityInformationAccess(AccessDescription(
            AccessDescription.id_ad_caIssuers, GeneralName(GeneralName.uniformResourceIdentifier, "http://www.reniec.gob.pe/crt/sha2/$issuer.crt"))))
        builder.addExtension(Extension.cRLDistributionPoints, false, CRLDistPoint(arrayOf(DistributionPoint(
            DistributionPointName(GeneralNames(GeneralName(GeneralName.uniformResourceIdentifier,
                "http://crl.reniec.gob.pe/crl/sha2/$issuer.crl"))), null, null))))
        return JcaX509CertificateConverter().getCertificate(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(keys.private)))
    }
    private val root = cert("root", "root", 1, true)
    private val ca = cert("ca", "root", 2, true)
    private val leaf = cert("leaf", "ca", 3, false)
    private fun crl(issuer: String, revokedSerial: Long? = null): ByteArray {
        val builder = X509v2CRLBuilder(name(issuer), Date(now.time - 10000)).setNextUpdate(Date(now.time + 500000))
        revokedSerial?.let { builder.addCRLEntry(BigInteger.valueOf(it), Date(now.time - 5000), 1) }
        return builder.build(JcaContentSignerBuilder("SHA256withRSA").build(keys.private)).encoded
    }
    private fun source(revokedCa: Boolean = false, missingCaCrl: Boolean = false): PublicCertificateSource = PublicCertificateSource { url ->
        when {
            url.endsWith("ca.crt") -> ca.encoded
            url.endsWith("root.crt") -> root.encoded
            url.endsWith("ca.crl") -> crl("ca")
            url.endsWith("root.crl") && !missingCaCrl -> crl("root", if (revokedCa) 2 else null)
            else -> throw java.io.IOException("Unavailable synthetic authority")
        }
    }
    private fun lists(withdrawnCa: Boolean = false): TrustedListSource = TrustedListSource {
        val status = "http://uri.etsi.org/TrstSvc/eSigDir-1999-93-EC-TrustedList/Svcstatus/"
        val services = mutableListOf(TrustedService("http://uri.etsi.org/TrstSvc/Svctype/CA/QC",
            status + "undersupervision", Date(0), listOf(root)))
        if (withdrawnCa) services += TrustedService("http://uri.etsi.org/TrstSvc/Svctype/CA/QC", status + "withdrawn", Date(0), listOf(ca))
        VerifiedTrustedList(25, Date(now.time - 1000), Date(now.time + 500000), leaf, services, ByteArray(32))
    }
    @Test fun authenticatedRootPathAndAllNonAnchorRevocationsAreEvaluated() {
        val report = CertificateTrustInspector(lists(), source()).inspect(leaf.encoded)
        assertEquals(TrustStatus.TRUSTED, report.trust)
        assertEquals(RevocationStatus.GOOD, report.revocation.status)
        assertEquals(RevocationStatus.GOOD, report.chainRevocation)
        assertEquals(1, report.caRevocationEvidence.size)
        assertEquals(RevocationMethod.CRL, report.caRevocationEvidence.single().method)
        assertEquals(RevocationStatus.GOOD, report.caRevocationEvidence.single().assessment.status)
    }
    @Test fun revokedIntermediateCannotAppearAsNonRevokedChain() {
        val report = CertificateTrustInspector(lists(), source(revokedCa = true)).inspect(leaf.encoded)
        assertEquals(RevocationStatus.GOOD, report.revocation.status)
        assertEquals(RevocationStatus.REVOKED, report.chainRevocation)
        assertEquals(RevocationStatus.REVOKED, report.caRevocationEvidence.single().assessment.status)
    }
    @Test fun unavailableIntermediateEvidenceIsIndeterminate() {
        val report = CertificateTrustInspector(lists(), source(missingCaCrl = true)).inspect(leaf.encoded)
        assertEquals(TrustStatus.TRUSTED, report.trust)
        assertEquals(RevocationStatus.UNKNOWN, report.chainRevocation)
        assertEquals("CA_CRL_FETCH:IOException", report.caRevocationEvidence.single().diagnosticCode)
    }
    @Test fun expiredDeadlineDoesNotStartCaDownloads() {
        val inspector = CaRevocationInspector(PublicCertificateSource { error("Must not download") })
        val evidence = inspector.inspect(1, ca, root, System.nanoTime() - 1)
        assertEquals(RevocationStatus.UNKNOWN, evidence.assessment.status)
        assertEquals("CA_DEADLINE", evidence.diagnosticCode)
    }
    @Test fun downloadFailureDoesNotExposeExceptionContents() {
        val inspector = CaRevocationInspector(PublicCertificateSource { throw java.io.IOException("private-identity") })
        val evidence = inspector.inspect(1, ca, root, System.nanoTime() + 1000000000L)
        assertEquals("CA_CRL_FETCH:IOException", evidence.diagnosticCode)
        assertFalse(evidence.toString().contains("private-identity"))
    }
    @Test fun malformedCaCrlIsDistinctFromNetworkFailure() {
        val inspector = CaRevocationInspector(PublicCertificateSource { byteArrayOf(1, 2, 3) })
        val evidence = inspector.inspect(1, ca, root, System.nanoTime() + 1000000000L)
        assertEquals(RevocationStatus.UNKNOWN, evidence.assessment.status)
        assertTrue(evidence.diagnosticCode.startsWith("CA_CRL_PARSE:"))
    }
    @Test fun unavailableTslDoesNotDiscardVerifiedLeafEvidenceOrInferTrust() {
        val report = CertificateTrustInspector(TrustedListSource { throw java.io.IOException("Offline") }, source()).inspect(leaf.encoded)
        assertEquals(RevocationStatus.GOOD, report.revocation.status)
        assertEquals(TrustStatus.UNKNOWN, report.trust)
    }
    @Test fun explicitlyWithdrawnIntermediateIsNotAdmittedThroughAnActiveRoot() {
        assertEquals(TrustStatus.UNTRUSTED, CertificateTrustInspector(lists(withdrawnCa = true), source()).inspect(leaf.encoded).trust)
    }
    @Test fun rollbackAndSameSequenceReplacementAreRejected() {
        val original = TrustedListRecord(26, "old")
        assertTrue(runCatching { TrustedListRollbackPolicy.check(original, TrustedListRecord(25, "new")) }.isFailure)
        assertTrue(runCatching { TrustedListRollbackPolicy.check(original, TrustedListRecord(26, "new")) }.isFailure)
        TrustedListRollbackPolicy.check(original, original)
        TrustedListRollbackPolicy.check(original, TrustedListRecord(27, "new"))
        assertTrue(runCatching { TrustedListRollbackPolicy.check(null, TrustedListRecord(24, "old")) }.isFailure)
    }
}
