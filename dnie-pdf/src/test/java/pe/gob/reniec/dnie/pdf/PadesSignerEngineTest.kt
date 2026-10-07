package pe.gob.reniec.dnie.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.crypto.Cipher

class PadesSignerEngineTest {

    companion object {
        private lateinit var keyPair: KeyPair
        private lateinit var testCert: X509Certificate

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
                X500Name("CN=QUISPE MAMANI\\, CARLOS ENRIQUE (FIRMA), SERIALNUMBER=47851234, C=PE, O=RENIEC"),
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
            testCert = JcaX509CertificateConverter()
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

        fun createSamplePdfBytes(title: String = "Documento de Prueba RENIEC"): ByteArray {
            val doc = PDDocument()
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)

            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14f)
                cs.newLineAtOffset(50f, 750f)
                cs.showText(title)
                cs.endText()

                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 11f)
                cs.newLineAtOffset(50f, 720f)
                cs.showText("Este documento sera firmado digitalmente usando PAdES y el chip del DNIe.")
                cs.endText()
            }

            val baos = ByteArrayOutputStream()
            doc.save(baos)
            doc.close()
            return baos.toByteArray()
        }
    }

    private val engine = PadesSignerEngine()

    @Test
    fun testSignInvisiblePdf() = runTest {
        val samplePdf = createSamplePdfBytes("Contrato Administrativo Invisible")

        val result = engine.signPdfBytes(
            inputPdfBytes = samplePdf,
            signingCertificateDer = testCert.encoded,
            cardSigner = { hash -> Result.success(mockCardSigner(hash)) },
            preset = SignaturePreset.INVISIBLE,
            reason = "Aprobacion legal",
            location = "Lima, Peru"
        ).getOrThrow()

        assertNotNull(result.outputBytes)
        assertTrue(result.outputBytes!!.size > samplePdf.size)
        assertEquals("QUISPE MAMANI, CARLOS ENRIQUE", result.signerName)
        assertEquals("47851234", result.signerDni)
        assertEquals(0, result.pageNumber) // 0 indica firma invisible
        assertTrue(result.signatureSize > 0)

        // Verificar estructura del PDF firmado
        val signedDoc = PDDocument.load(result.outputBytes)
        val sigDicts = signedDoc.signatureDictionaries
        assertEquals("Debe existir exactamente 1 diccionario de firma", 1, sigDicts.size)

        val sig = sigDicts.first()
        assertPadesStructureAndIntegrity(sig, result.outputBytes!!)
        assertEquals(PDSignature.FILTER_ADOBE_PPKLITE.name, sig.filter)
        assertEquals("ETSI.CAdES.detached", sig.subFilter)
        assertEquals("QUISPE MAMANI, CARLOS ENRIQUE", sig.name)
        assertEquals("Aprobacion legal", sig.reason)
        assertEquals("Lima, Peru", sig.location)
        assertNotNull(sig.contents)
        assertTrue(sig.contents.isNotEmpty())

        signedDoc.close()
    }

    @Test
    fun testSignVisiblePdfBottomRight() = runTest {
        val samplePdf = createSamplePdfBytes("Resolucion Directoral Visible")

        val result = engine.signPdfBytes(
            inputPdfBytes = samplePdf,
            signingCertificateDer = testCert.encoded,
            cardSigner = { hash -> Result.success(mockCardSigner(hash)) },
            preset = SignaturePreset.BOTTOM_RIGHT,
            reason = "Doy fe de la autenticidad",
            location = "Arequipa, Peru",
            pageNumber = 1
        ).getOrThrow()

        assertNotNull(result.outputBytes)
        assertEquals(1, result.pageNumber)

        val signedDoc = PDDocument.load(result.outputBytes)
        val sigDicts = signedDoc.signatureDictionaries
        assertEquals(1, sigDicts.size)

        val sig = sigDicts.first()
        assertPadesStructureAndIntegrity(sig, result.outputBytes!!)
        assertEquals("QUISPE MAMANI, CARLOS ENRIQUE", sig.name)
        assertEquals("Doy fe de la autenticidad", sig.reason)
        assertEquals("Arequipa, Peru", sig.location)

        signedDoc.close()
    }

    @Test
    fun testIncrementalSavePreservesPriorSignatures() = runTest {
        val samplePdf = createSamplePdfBytes("Documento Co-Firmado Multi-Firma")

        // 1. Primera firma (Invisible)
        val firstSignResult = engine.signPdfBytes(
            inputPdfBytes = samplePdf,
            signingCertificateDer = testCert.encoded,
            cardSigner = { hash -> Result.success(mockCardSigner(hash)) },
            preset = SignaturePreset.INVISIBLE,
            reason = "Firma del Autor",
            location = "Lima, Peru"
        ).getOrThrow()

        // 2. Segunda firma sobre el documento ya firmado (Visible Inferior Derecha)
        val secondSignResult = engine.signPdfBytes(
            inputPdfBytes = firstSignResult.outputBytes!!,
            signingCertificateDer = testCert.encoded,
            cardSigner = { hash -> Result.success(mockCardSigner(hash)) },
            preset = SignaturePreset.BOTTOM_RIGHT,
            reason = "Firma del Revisor",
            location = "Cusco, Peru"
        ).getOrThrow()

        // 3. Inspeccionar el documento con dos firmas incrementales
        val doublySignedDoc = PDDocument.load(secondSignResult.outputBytes)
        val sigDicts = doublySignedDoc.signatureDictionaries
        assertEquals("Deben coexistir exactamente 2 firmas digitales", 2, sigDicts.size)

        sigDicts.forEach { assertPadesStructureAndIntegrity(it, secondSignResult.outputBytes!!) }
        assertEquals("Firma del Autor", sigDicts[0].reason)
        assertEquals("Firma del Revisor", sigDicts[1].reason)

        doublySignedDoc.close()
    }
    private fun assertPadesStructureAndIntegrity(sig: PDSignature, pdf: ByteArray) {
        assertEquals("ETSI.CAdES.detached", sig.subFilter)
        assertEquals(0, sig.byteRange[0])
        val end = sig.byteRange[2] + sig.byteRange[3]
        assertTrue(end <= pdf.size)
        assertNotNull(sig.signDate)
        assertEquals(0, sig.signDate.timeZone.rawOffset)
        val contents = sig.getContents(pdf)
        val canonical = org.bouncycastle.asn1.ASN1InputStream(contents).use {
            it.readObject().getEncoded(org.bouncycastle.asn1.ASN1Encoding.DER)
        }
        org.junit.Assert.assertArrayEquals(canonical, contents.copyOf(canonical.size))
        val cms = org.bouncycastle.cms.CMSSignedData(
            org.bouncycastle.cms.CMSProcessableByteArray(sig.getSignedContent(pdf)), canonical)
        val signer = cms.signerInfos.signers.single()
        org.junit.Assert.assertNull(signer.signedAttributes.get(org.bouncycastle.asn1.cms.CMSAttributes.signingTime))
        val ess = org.bouncycastle.asn1.ess.SigningCertificateV2.getInstance(signer.signedAttributes.get(
            org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.id_aa_signingCertificateV2).attrValues.getObjectAt(0))
        org.junit.Assert.assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256").digest(testCert.encoded), ess.certs.single().certHash)
        assertTrue(signer.verify(org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder()
            .setProvider(CryptoProviderInitializer.provider).build(testCert)))
        val changed = pdf.clone().apply { this[10] = (this[10].toInt() xor 1).toByte() }
        try {
            val invalid = org.bouncycastle.cms.CMSSignedData(
                org.bouncycastle.cms.CMSProcessableByteArray(sig.getSignedContent(changed)), canonical)
            org.junit.Assert.assertFalse(invalid.signerInfos.signers.single().verify(
                org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder().build(testCert)))
        } catch (expected: org.bouncycastle.cms.CMSSignerDigestMismatchException) { }
    }

}
