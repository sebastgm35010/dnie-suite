package pe.gob.reniec.dnie.crypto

import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.crypto.Cipher

class CmsSignatureBuilderTest {

    companion object {
        private lateinit var keyPair: KeyPair
        private lateinit var signerCert: X509Certificate
        private lateinit var rootCert: X509Certificate

        @BeforeClass
        @JvmStatic
        fun setUp() {
            CryptoProviderInitializer.init()
            val kpg = KeyPairGenerator.getInstance("RSA", CryptoProviderInitializer.provider)
            kpg.initialize(2048)
            keyPair = kpg.generateKeyPair()

            val notBefore = Date(System.currentTimeMillis() - 86400000L)
            val notAfter = Date(System.currentTimeMillis() + 365L * 86400000L)

            // Crear Certificado Raíz de prueba
            val rootBuilder = X509v3CertificateBuilder(
                X500Name("CN=ECP RENIEC ROOT CA, O=RENIEC, C=PE"),
                BigInteger.valueOf(1),
                notBefore,
                notAfter,
                X500Name("CN=ECP RENIEC ROOT CA, O=RENIEC, C=PE"),
                SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
            )
            val rootSigner = JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(CryptoProviderInitializer.provider)
                .build(keyPair.private)
            rootCert = JcaX509CertificateConverter()
                .setProvider(CryptoProviderInitializer.provider)
                .getCertificate(rootBuilder.build(rootSigner))

            // Crear Certificado de Ciudadano
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
            signerCert = JcaX509CertificateConverter()
                .setProvider(CryptoProviderInitializer.provider)
                .getCertificate(certBuilder.build(rootSigner))
        }

        /**
         * Simula el comportamiento del coprocesador criptográfico del DNIe peruano.
         * Recibe el hash SHA-256 de 32 bytes, añade el encabezado DER DigestInfo de 19 bytes
         * y realiza la exponenciación modular RSA-2048 con la clave privada en tarjeta.
         */
        fun simulateDnieCardSigning(sha256Digest: ByteArray): ByteArray {
            require(sha256Digest.size == 32)
            val digestInfo = ByteArray(ApduConstants.PSO_CDS_PAYLOAD_SIZE)
            System.arraycopy(ApduConstants.SHA256_DIGEST_INFO_PREFIX, 0, digestInfo, 0, 19)
            System.arraycopy(sha256Digest, 0, digestInfo, 19, 32)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", CryptoProviderInitializer.provider)
            cipher.init(Cipher.ENCRYPT_MODE, keyPair.private)
            return cipher.doFinal(digestInfo)
        }
    }

    @Test
    fun testBuildDetachedCmsSignatureAndCryptographicVerification() {
        val sampleDocument = "Documento oficial del Estado Peruano para firma digital PAdES".toByteArray(Charsets.UTF_8)
        val documentHash = MessageDigest.getInstance("SHA-256").digest(sampleDocument)

        // Construir CMS Detached
        val cmsBytes = CmsSignatureBuilder.buildDetachedSignature(
            documentHash = documentHash,
            signerCertificate = signerCert,
            caCertificates = listOf(rootCert),
            cardSigner = { hash -> simulateDnieCardSigning(hash) }
        )

        assertNotNull(cmsBytes)
        assertTrue("El contenedor CMS debe tener tamaño mayor a cero", cmsBytes.isNotEmpty())

        // Parsear y verificar con BouncyCastle
        val cmsSignedData = CMSSignedData(org.bouncycastle.cms.CMSProcessableByteArray(sampleDocument), cmsBytes)
        val signers = cmsSignedData.signerInfos.signers
        assertEquals("Debe existir exactamente 1 SignerInfo", 1, signers.size)

        val signerInfo = signers.first()

        // 1. Verificar presencia de atributos firmados requeridos
        val signedAttrs = signerInfo.signedAttributes
        assertNotNull("Debe contener signedAttrs", signedAttrs)

        val contentTypeAttr = signedAttrs.get(CMSAttributes.contentType)
        assertNotNull("Debe contener id-contentType", contentTypeAttr)

        val signingTimeAttr = signedAttrs.get(CMSAttributes.signingTime)
        assertNotNull("Debe contener id-signingTime", signingTimeAttr)

        val messageDigestAttr = signedAttrs.get(CMSAttributes.messageDigest)
        assertNotNull("Debe contener id-messageDigest", messageDigestAttr)

        val signingCertV2Attr = signedAttrs.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2)
        assertNotNull("Debe contener RFC 5035 id-aa-signingCertificateV2 (PAdES)", signingCertV2Attr)

        // 2. Verificar que el messageDigest coincida exactamente con el hash del documento
        val octetString = messageDigestAttr.attrValues.getObjectAt(0) as org.bouncycastle.asn1.ASN1OctetString
        assertArrayEquals("El messageDigest en CMS debe coincidir con el hash del documento", documentHash, octetString.octets)

        // 3. Verificación criptográfica con SignerInformationVerifier
        val verifier = JcaSimpleSignerInfoVerifierBuilder()
            .setProvider(CryptoProviderInitializer.provider)
            .build(signerCert.publicKey)

        val isSignatureValid = signerInfo.verify(verifier)
        assertTrue("La firma CMS debe ser criptográficamente válida contra la clave pública del firmante", isSignatureValid)
    }
    @Test
    fun padesProfileUsesDerAndCertificateBindingWithoutCmsTime() {
        val bytes = "Synthetic PAdES profile test".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        val encoded = CmsSignatureBuilder.buildDetachedSignature(
            hash, signerCert, listOf(rootCert), profile = CmsSignatureProfile.PADES_BASELINE_B,
            cardSigner = { simulateDnieCardSigning(it) })
        val asn1 = org.bouncycastle.asn1.ASN1Primitive.fromByteArray(encoded)
        assertArrayEquals(asn1.getEncoded(org.bouncycastle.asn1.ASN1Encoding.DER), encoded)
        val cms = CMSSignedData(org.bouncycastle.cms.CMSProcessableByteArray(bytes), encoded)
        val signer = cms.signerInfos.signers.single()
        org.junit.Assert.assertNull(signer.signedAttributes.get(CMSAttributes.signingTime))
        val ess = org.bouncycastle.asn1.ess.SigningCertificateV2.getInstance(
            signer.signedAttributes.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2).attrValues.getObjectAt(0))
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(signerCert.encoded), ess.certs.single().certHash)
        assertEquals(2, cms.certificates.getMatches(null).size)
        val verifier = JcaSimpleSignerInfoVerifierBuilder().setProvider(CryptoProviderInitializer.provider).build(signerCert)
        assertTrue(signer.verify(verifier))
        val altered = bytes.clone().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        try {
            val changed = CMSSignedData(org.bouncycastle.cms.CMSProcessableByteArray(altered), encoded)
            org.junit.Assert.assertFalse(changed.signerInfos.signers.single().verify(verifier))
        } catch (expected: org.bouncycastle.cms.CMSSignerDigestMismatchException) { }
    }

    @Test fun expiredCertificateNeverInvokesCardSigner() {
        val now = System.currentTimeMillis()
        val holder = X509v3CertificateBuilder(X500Name("CN=Test Issuer"), BigInteger.TEN,
            Date(now - 100000), Date(now - 1000), X500Name("CN=Expired Test Holder"),
            SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)).build(
                JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
        val expired = JcaX509CertificateConverter().getCertificate(holder)
        var operations = 0
        val failure = runCatching { CmsSignatureBuilder.buildDetachedSignature(ByteArray(32),expired,
            cardSigner = { operations++; simulateDnieCardSigning(it) }) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(0,operations)
    }

    @Test fun bothProfilesBindSignedIssuerAndSerialToExactEmbeddedCertificate() {
        val document="Synthetic strict certificate identity".toByteArray()
        for(profile in CmsSignatureProfile.entries) {
            val encoded=CmsSignatureBuilder.buildDetachedSignature(MessageDigest.getInstance("SHA-256").digest(document),
                signerCert,profile=profile,cardSigner={ simulateDnieCardSigning(it) })
            val cms=CMSSignedData(org.bouncycastle.cms.CMSProcessableByteArray(document),encoded)
            val signer=cms.signerInfos.signers.single()
            val binding=org.bouncycastle.asn1.ess.SigningCertificateV2.getInstance(
                signer.signedAttributes.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2).attrValues.getObjectAt(0)).certs.single()
            val identity=binding.issuerSerial
            assertNotNull(identity)
            assertEquals(signerCert.serialNumber,identity.serial.value)
            assertEquals(org.bouncycastle.asn1.x509.GeneralName.directoryName,identity.issuer.names.single().tagNo)
            assertEquals(org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(signerCert).issuer,
                X500Name.getInstance(identity.issuer.names.single().name))
            assertEquals(1,cms.certificates.getMatches(null).count { signer.sid.match(it) })
            assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(signerCert)))
        }
    }

}
