package pe.gob.reniec.dnie.crypto

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.util.Date
import javax.crypto.Cipher

class DetachedFileSignerTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val cert = run {
        CryptoProviderInitializer.init()
        val name = X500Name("CN=PRUEBA, SERIALNUMBER=12345678, C=PE")
        JcaX509CertificateConverter().setProvider(CryptoProviderInitializer.provider).getCertificate(
            JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date(0), Date(4102444800000L), name, keys.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(CryptoProviderInitializer.provider).build(keys.private)))
    }
    private fun sign(hash: ByteArray): ByteArray = Cipher.getInstance("RSA/ECB/PKCS1Padding", CryptoProviderInitializer.provider).run {
        init(Cipher.ENCRYPT_MODE, keys.private); doFinal(ApduConstants.SHA256_DIGEST_INFO_PREFIX + hash)
    }
    @Test fun arbitraryBinaryIsVerifiableAndOriginalIsUnchanged() {
        val input = ByteArray(131073) { (it * 17).toByte() }
        val copy = input.copyOf()
        val output = ByteArrayOutputStream()
        assertTrue(DetachedFileSigner.sign(input.inputStream(), output, cert.encoded, ::sign) > 0)
        val cms = CMSSignedData(CMSProcessableByteArray(input), output.toByteArray())
        assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(CryptoProviderInitializer.provider).build(cert)))
        assertNull(org.bouncycastle.asn1.cms.SignedData.getInstance(cms.toASN1Structure().content).encapContentInfo.content)
        assertArrayEquals(copy, input)
        input[100] = (input[100].toInt() xor 1).toByte()
        val altered = runCatching { CMSSignedData(CMSProcessableByteArray(input), output.toByteArray()).signerInfos.signers.single()
            .verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(CryptoProviderInitializer.provider).build(cert)) }
        assertFalse(altered.getOrDefault(false))
    }
    @Test fun invalidCardSignatureNeverProducesAnOutput() {
        val output = ByteArrayOutputStream()
        assertTrue(runCatching { DetachedFileSigner.sign("documento".byteInputStream(), output, cert.encoded) { ByteArray(256) } }.isFailure)
        assertEquals(0, output.size())
    }
    @Test fun cardVerifierChecksTheExactDigest() {
        val hash = ByteArray(32) { it.toByte() }
        val signature = sign(hash)
        assertArrayEquals(signature, CardSignatureVerifier.verify(cert, hash, signature))
        hash[0] = 42
        assertTrue(runCatching { CardSignatureVerifier.verify(cert, hash, signature) }.exceptionOrNull() is SignatureIntegrityException)
    }
    @Test fun emptyFileAlsoHasAValidDetachedSignature() {
        val output = ByteArrayOutputStream()
        DetachedFileSigner.sign(byteArrayOf().inputStream(), output, cert.encoded, ::sign)
        val cms = CMSSignedData(CMSProcessableByteArray(byteArrayOf()), output.toByteArray())
        assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(CryptoProviderInitializer.provider).build(cert)))
    }
}
