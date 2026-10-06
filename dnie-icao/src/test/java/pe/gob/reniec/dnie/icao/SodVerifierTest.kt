package pe.gob.reniec.dnie.icao

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.icao.DataGroupHash
import org.bouncycastle.asn1.icao.LDSSecurityObject
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.util.CollectionStore
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.util.Date

class SodVerifierTest {
    private fun sod(): ByteArray {
        val provider = CryptoProviderInitializer.provider
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val contentSigner = JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(kp.private)
        val name = X500Name("CN=Test document signer,C=PE")
        val cert = JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date(0), Date(4102444800000), name, kp.public).build(contentSigner)
        val hashes = arrayOf(DataGroupHash(1, DEROctetString(MessageDigest.getInstance("SHA-256").digest(dg1()))),
            DataGroupHash(2, DEROctetString(MessageDigest.getInstance("SHA-256").digest(face()))))
        val lds = LDSSecurityObject(AlgorithmIdentifier(ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.1")), hashes)
        val generator = CMSSignedDataGenerator()
        generator.addSignerInfoGenerator(JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().setProvider(provider).build())
            .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(kp.private), cert))
        generator.addCertificates(CollectionStore(listOf(cert)))
        return tlv(0x77, generator.generate(CMSProcessableByteArray(ASN1ObjectIdentifier("2.23.136.1.1.1"), lds.encoded), true).encoded)
    }

    @Test fun verifiesEmbeddedSignatureWithoutClaimingIssuerTrust() {
        val security = IcaoParser.parse(IcaoFile.SOD, sod()) as IcaoContent.Security
        assertTrue(security.signatureValid)
        assertFalse(security.issuerTrustVerified)
        assertEquals("SHA-256", security.digestAlgorithm)
    }
    @Test fun verifiesWholeDgIncludingItsBerHeader() {
        val security = IcaoParser.parse(IcaoFile.SOD, sod()) as IcaoContent.Security
        val document = IcaoDocument(blocks = mapOf(
            IcaoFile.SOD to IcaoBlock(IcaoFile.SOD, BlockStatus.AVAILABLE, security),
            IcaoFile.DG1 to IcaoBlock(IcaoFile.DG1, BlockStatus.AVAILABLE, rawBytes = dg1())))
        assertEquals(HashStatus.MATCH, SodVerifier.apply(document).blocks[IcaoFile.DG1]?.hashStatus)
    }
    @Test fun detectsTamperingAndUnlistedGroupsIndependently() {
        val security = IcaoParser.parse(IcaoFile.SOD, sod()) as IcaoContent.Security
        val changed = dg1().apply { this[lastIndex] = 'A'.code.toByte() }
        val document = IcaoDocument(blocks = mapOf(
            IcaoFile.SOD to IcaoBlock(IcaoFile.SOD, BlockStatus.AVAILABLE, security),
            IcaoFile.DG1 to IcaoBlock(IcaoFile.DG1, BlockStatus.AVAILABLE, rawBytes = changed),
            IcaoFile.DG7 to IcaoBlock(IcaoFile.DG7, BlockStatus.AVAILABLE, rawBytes = tlv(0x67, tlv(0x5F43, jpeg)))))
        val result = SodVerifier.apply(document)
        assertEquals(HashStatus.MISMATCH, result.blocks[IcaoFile.DG1]?.hashStatus)
        assertEquals(HashStatus.NOT_LISTED, result.blocks[IcaoFile.DG7]?.hashStatus)
    }
    @Test fun absentSodDoesNotDiscardReadableIdentity() {
        val doc = IcaoDocument(blocks = mapOf(IcaoFile.DG1 to IcaoBlock(IcaoFile.DG1, BlockStatus.AVAILABLE, rawBytes = dg1())))
        assertEquals(doc, SodVerifier.apply(doc))
        assertEquals(HashStatus.NOT_CHECKED, SodVerifier.apply(doc).blocks[IcaoFile.DG1]?.hashStatus)
    }
}
