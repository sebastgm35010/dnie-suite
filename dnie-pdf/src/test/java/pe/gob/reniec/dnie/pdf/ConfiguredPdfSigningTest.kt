package pe.gob.reniec.dnie.pdf

import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.util.Date
import javax.crypto.Cipher

class ConfiguredPdfSigningTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val cert = run {
        CryptoProviderInitializer.init()
        val name = X500Name("CN=TITULAR PRUEBA, SERIALNUMBER=12345678, C=PE")
        JcaX509CertificateConverter().setProvider(CryptoProviderInitializer.provider).getCertificate(
            JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date(0), Date(4102444800000L), name, keys.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(CryptoProviderInitializer.provider).build(keys.private)))
    }
    private fun sign(hash: ByteArray): ByteArray = Cipher.getInstance("RSA/ECB/PKCS1Padding", CryptoProviderInitializer.provider).run {
        init(Cipher.ENCRYPT_MODE, keys.private); doFinal(ApduConstants.SHA256_DIGEST_INFO_PREFIX + hash)
    }
    private suspend fun signed(bytes: ByteArray, options: PdfSigningOptions): ByteArray {
        val out = ByteArrayOutputStream()
        PadesSignerEngine().signConfiguredPdf(bytes.inputStream(), out, cert.encoded, { Result.success(sign(it)) }, options).getOrThrow()
        return out.toByteArray()
    }
    @Test fun normalizedPlacementHandlesEveryRotationAndCropOrigin() {
        val page = PDPage(PDRectangle(600f, 800f)).apply { cropBox = PDRectangle(10f, 20f, 400f, 600f) }
        val placement = NormalizedStampPlacement(.1f, .2f, .3f, .1f)
        val expected = listOf(floatArrayOf(50f, 440f, 120f, 60f), floatArrayOf(90f, 80f, 40f, 180f),
            floatArrayOf(250f, 140f, 120f, 60f), floatArrayOf(290f, 380f, 40f, 180f))
        listOf(0, 90, 180, 270).forEachIndexed { i, rotation ->
            page.rotation = rotation
            val rect = VisualStampRenderer.normalizedBoundingBox(page, placement)
            assertArrayEquals(expected[i], floatArrayOf(rect.lowerLeftX, rect.lowerLeftY, rect.width, rect.height), .001f)
        }
    }
    @Test fun selectedThirdPageContainsWidgetAndBothIncrementalSignaturesVerify() = runTest {
        val input = PDDocument().use { doc -> repeat(3) { doc.addPage(PDPage(PDRectangle.A4)) }; ByteArrayOutputStream().also { doc.save(it) }.toByteArray() }
        val first = signed(input, PdfSigningOptions(pageNumber = 3))
        PDDocument.load(first).use { doc ->
            val widget = (doc.documentCatalog.acroForm.fields.single() as PDSignatureField).widgets.single()
            assertEquals(doc.getPage(2).cosObject, widget.page.cosObject)
            assertTrue(doc.getPage(2).annotations.any { it.cosObject == widget.cosObject })
            assertTrue(doc.getPage(0).annotations.isEmpty())
        }
        val second = signed(first, PdfSigningOptions(pageNumber = 1, visible = false))
        PDDocument.load(second).use { doc ->
            assertEquals(2, doc.signatureDictionaries.size)
            doc.signatureDictionaries.forEach { sig ->
                val cms = CMSSignedData(CMSProcessableByteArray(sig.getSignedContent(second)), sig.getContents(second))
                assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(CryptoProviderInitializer.provider).build(cert)))
            }
        }
    }
    @Test fun uncheckedFieldsAreAbsentFromVectorPreview() {
        val appearance = SignatureAppearance(showDni = false, showDate = false, showTime = false, showReason = false, showLocation = false)
        val bytes = VisualStampRenderer.previewPdf(280f, 120f, "TITULAR", "12345678", PdfSigningOptions(appearance = appearance, reason = "MOTIVO_SECRETO", location = "LUGAR_SECRETO"))
        PDDocument.load(bytes).use { doc ->
            val text = PDFTextStripper().getText(doc)
            assertTrue(text.contains("TITULAR"))
            assertFalse(text.contains("12345678")); assertFalse(text.contains("MOTIVO_SECRETO")); assertFalse(text.contains("LUGAR_SECRETO"))
            assertFalse(text.contains("/202"))
        }
    }
    @Test fun movingAndResizingAlwaysKeepStampInsidePage() {
        val placement = NormalizedStampPlacement().moved(100f, -100f).resized(.9f, .9f)
        assertTrue(placement.left >= 0 && placement.top >= 0 && placement.left + placement.width <= 1.00001f && placement.top + placement.height <= 1.00001f)
    }
    @Test fun invalidPageFailsBeforeAnyCardOperation() = runTest {
        var calls = 0
        val input = PDDocument().use { doc -> doc.addPage(PDPage()); ByteArrayOutputStream().also { doc.save(it) }.toByteArray() }
        val result = PadesSignerEngine().signConfiguredPdf(input.inputStream(), ByteArrayOutputStream(), cert.encoded, { calls++; Result.success(sign(it)) }, PdfSigningOptions(pageNumber = 2))
        assertTrue(result.isFailure); assertEquals(0, calls)
    }
    @Test fun certifiedPdfWithNoChangesPermissionIsRejectedBeforeSigning() = runTest {
        val input = PDDocument().use { doc ->
            doc.addPage(PDPage())
            val params = com.tom_roush.pdfbox.cos.COSDictionary().apply { setInt(com.tom_roush.pdfbox.cos.COSName.P, 1) }
            val reference = com.tom_roush.pdfbox.cos.COSDictionary().apply {
                setName(com.tom_roush.pdfbox.cos.COSName.TRANSFORM_METHOD, "DocMDP")
                setItem(com.tom_roush.pdfbox.cos.COSName.TRANSFORM_PARAMS, params)
            }
            val certification = com.tom_roush.pdfbox.cos.COSDictionary().apply { setItem(com.tom_roush.pdfbox.cos.COSName.REFERENCE, com.tom_roush.pdfbox.cos.COSArray().apply { add(reference) }) }
            doc.documentCatalog.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.PERMS, com.tom_roush.pdfbox.cos.COSDictionary().apply { setItem(com.tom_roush.pdfbox.cos.COSName.DOCMDP, certification) })
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        var calls = 0
        val result = PadesSignerEngine().signConfiguredPdf(input.inputStream(), ByteArrayOutputStream(), cert.encoded, { calls++; Result.success(sign(it)) }, PdfSigningOptions())
        assertTrue(result.isFailure); assertTrue(result.exceptionOrNull()!!.message!!.contains("no permite")); assertEquals(0, calls)
    }
}
