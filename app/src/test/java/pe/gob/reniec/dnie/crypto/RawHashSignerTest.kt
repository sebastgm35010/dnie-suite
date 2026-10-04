package pe.gob.reniec.dnie.crypto

import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.crypto.Cipher

class RawHashSignerTest {

    companion object {
        private lateinit var keyPair: KeyPair
        private lateinit var signerCert: X509Certificate

        @BeforeClass
        @JvmStatic
        fun setUp() {
            CryptoProviderInitializer.init()
            val kpg = KeyPairGenerator.getInstance("RSA", CryptoProviderInitializer.provider)
            kpg.initialize(2048)
            keyPair = kpg.generateKeyPair()

            val notBefore = Date(System.currentTimeMillis() - 86400000L)
            val notAfter = Date(System.currentTimeMillis() + 365L * 86400000L)

            val certBuilder = X509v3CertificateBuilder(
                X500Name("CN=ECP RENIEC ROOT CA, O=RENIEC, C=PE"),
                BigInteger(64, SecureRandom()),
                notBefore,
                notAfter,
                X500Name("CN=QUISPE MAMANI\\, CARLOS ENRIQUE (FIRMA), SERIALNUMBER=47851234, C=PE"),
                SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
            )
            certBuilder.addExtension(
                Extension.keyUsage,
                true,
                KeyUsage(KeyUsage.digitalSignature or KeyUsage.nonRepudiation)
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(CryptoProviderInitializer.provider)
                .build(keyPair.private)
            signerCert = JcaX509CertificateConverter()
                .setProvider(CryptoProviderInitializer.provider)
                .getCertificate(certBuilder.build(signer))
        }

        fun mockCardSigner(sha256Digest: ByteArray): ByteArray {
            val digestInfo = ByteArray(ApduConstants.PSO_CDS_PAYLOAD_SIZE)
            System.arraycopy(ApduConstants.SHA256_DIGEST_INFO_PREFIX, 0, digestInfo, 0, 19)
            System.arraycopy(sha256Digest, 0, digestInfo, 19, 32)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", CryptoProviderInitializer.provider)
            cipher.init(Cipher.ENCRYPT_MODE, keyPair.private)
            return cipher.doFinal(digestInfo)
        }
    }

    @Test
    fun testSignRawHashSuccess() {
        val sampleHash = ByteArray(32) { (it * 7).toByte() }

        val signature = RawHashSigner.signRawHash(sampleHash) { hash ->
            mockCardSigner(hash)
        }

        assertEquals(256, signature.size)
        val hex = RawHashSigner.toHex(signature)
        assertEquals(512, hex.length)
        val b64 = RawHashSigner.toBase64(signature)
        assertTrue(b64.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSignRawHashInvalidLengthThrows() {
        val invalidHash = ByteArray(16) // MD5 o longitud inválida
        RawHashSigner.signRawHash(invalidHash) { mockCardSigner(it) }
    }

    @Test
    fun testSignHashToCmsContainerAndVerify() = runTest {
        val sampleData = "Datos crudos para prueba de contenedor CMS detached".toByteArray(Charsets.UTF_8)
        val sampleHash = java.security.MessageDigest.getInstance("SHA-256").digest(sampleData)

        val p7sBytes = RawHashSigner.signHashToCmsContainer(
            hash32Bytes = sampleHash,
            signerCertificate = signerCert,
            caCertificates = emptyList(),
            cardSigner = { mockCardSigner(it) }
        )

        assertNotNull(p7sBytes)
        assertTrue(p7sBytes.isNotEmpty())

        val cms = CMSSignedData(org.bouncycastle.cms.CMSProcessableByteArray(sampleData), p7sBytes)
        val signers = cms.signerInfos.signers
        assertEquals(1, signers.size)

        val verifier = JcaSimpleSignerInfoVerifierBuilder()
            .setProvider(CryptoProviderInitializer.provider)
            .build(signerCert.publicKey)
        assertTrue("El contenedor CMS .p7s debe verificar exitosamente", signers.first().verify(verifier))

        // Exportar a archivo
        val tempP7s = File.createTempFile("test_sig_", ".p7s")
        try {
            RawHashSigner.exportToP7sFile(p7sBytes, tempP7s)
            assertEquals(p7sBytes.size.toLong(), tempP7s.length())
        } finally {
            tempP7s.delete()
        }
    }
}
