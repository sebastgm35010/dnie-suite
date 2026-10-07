package pe.gob.reniec.dnie.timestamp

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.util.Date
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.*
import org.bouncycastle.cms.*
import org.bouncycastle.cms.jcajce.*
import org.bouncycastle.operator.jcajce.*
import org.bouncycastle.tsp.*
import org.bouncycastle.util.CollectionStore
import org.junit.Assert.*
import org.junit.Test

class TimestampServiceTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val certificate = JcaX509CertificateConverter().getCertificate(
        JcaX509v3CertificateBuilder(X500Name("CN=Synthetic TSA"), BigInteger.ONE,
            Date(System.currentTimeMillis()-60000), Date(System.currentTimeMillis()+3600000),
            X500Name("CN=Synthetic TSA"), keys.public).apply {
            addExtension(Extension.extendedKeyUsage, true, ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping))
            addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        }.build(JcaContentSignerBuilder("SHA256withRSA").build(keys.private)))
    private fun response(q: TimeStampRequest, time: Date = Date()): ByteArray {
        val digests = JcaDigestCalculatorProviderBuilder().build()
        val signer = JcaSignerInfoGeneratorBuilder(digests).build(JcaContentSignerBuilder("SHA256withRSA").build(keys.private), certificate)
        val generator = TimeStampTokenGenerator(signer, digests.get(AlgorithmIdentifier(TSPAlgorithms.SHA256)),
            ASN1ObjectIdentifier(TimestampTokenValidator.RENIEC_POLICY))
        generator.addCertificates(CollectionStore(listOf(JcaX509CertificateHolder(certificate))))
        return TimeStampResponseGenerator(generator, setOf(TSPAlgorithms.SHA256)).generateGrantedResponse(q, BigInteger.TEN, time).encoded
    }
    private fun query(hash: ByteArray = ByteArray(32), nonce: Long = 123) = TimeStampRequestGenerator().apply { setCertReq(true) }
        .generate(TSPAlgorithms.SHA256, hash, BigInteger.valueOf(nonce))
    private fun cms(): ByteArray = CMSSignedDataGenerator().apply {
        addSignerInfoGenerator(JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build()).build(
            JcaContentSignerBuilder("SHA256withRSA").build(keys.private), certificate))
        addCertificate(JcaX509CertificateHolder(certificate))
    }.generate(CMSProcessableByteArray(byteArrayOf(1,2,3)), false).encoded
    @Test fun verifiedTimestampPreservesDocumentSignatureAndBindsToItsValue() {
        var calls = 0; var trusts = 0
        val original = cms()
        val service = ReniecCmsTimestampService(TimestampClient { calls++; response(TimeStampRequest(it)) },
            TimestampTokenValidator(TimestampCertificateTrust { _, _ -> trusts++ }))
        val stamped = CMSSignedData(service.stamp(original))
        val before = CMSSignedData(original).signerInfos.signers.single()
        val after = stamped.signerInfos.signers.single()
        assertArrayEquals(before.signature, after.signature)
        assertEquals(1,calls); assertEquals(1,trusts)
        assertNotNull(after.unsignedAttributes.get(ASN1ObjectIdentifier("1.2.840.113549.1.9.16.2.14")))
    }
    @Test fun wrongNonceAndImprintAreRejectedBeforeTrust() {
        val q = query(); val bytes = response(q)
        val validator = TimestampTokenValidator(TimestampCertificateTrust { _, _ -> error("Must not trust mismatched response") })
        assertTrue(runCatching { validator.validate(query(nonce = 124),bytes) }.isFailure)
        assertTrue(runCatching { validator.validate(query(ByteArray(32) { 1 }),bytes) }.isFailure)
    }
    @Test fun untrustedAndForgedTokensCannotBeEmbedded() {
        val q = query()
        assertTrue(runCatching { TimestampTokenValidator(TimestampCertificateTrust { _, _ -> error("untrusted") }).validate(q,response(q)) }.isFailure)
        val bytes=response(q); bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        assertTrue(runCatching { TimestampTokenValidator(TimestampCertificateTrust { _, _ -> }).validate(q,bytes) }.isFailure)
    }
    @Test fun staleTimestampAndUnsupportedPolicyAreRejected() {
        val q=query(); val validator=TimestampTokenValidator(TimestampCertificateTrust { _, _ -> })
        assertTrue(runCatching { validator.validate(q,response(q,Date(System.currentTimeMillis()-600000))) }.isFailure)
        assertTrue(runCatching { TimestampTokenValidator(TimestampCertificateTrust { _, _ -> },setOf("1.2.3")).validate(q,response(q)) }.isFailure)
    }
    @Test fun existingTimestampIsNotRequestedAgain() {
        var calls=0
        val service=ReniecCmsTimestampService(TimestampClient { calls++; response(TimeStampRequest(it)) },TimestampTokenValidator(TimestampCertificateTrust { _, _ -> }))
        val stamped=service.stamp(cms())
        assertTrue(runCatching { service.stamp(stamped) }.isFailure)
        assertEquals(1,calls)
    }
}
