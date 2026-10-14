package pe.gob.reniec.dnie.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.junit.*
import org.junit.Assert.*
import pe.gob.reniec.dnie.data.signing.AndroidStampPreview
import pe.gob.reniec.dnie.pdf.*
import pe.gob.reniec.dnie.ui.theme.DnieTheme
import pe.gob.reniec.dnie.ui.verification.*
import java.util.Date
import kotlinx.coroutines.runBlocking

class StampQrPresentationTest {
    @get:Rule val rule=createComposeRule()
    private fun preview()=StampQrRecord(StampQrMetadata.PREVIEW,"ANA QUISPE",null,null,"B")
    @Test fun androidSignedPdfQrHasActualCertificateAndOriginalAndCmsIsIntact()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        pe.gob.reniec.dnie.crypto.CryptoProviderInitializer.init()
        val keys=java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject=org.bouncycastle.asn1.x500.X500Name("CN=PRUEBA QR NO ES DNI, SERIALNUMBER=88888888, C=PE")
        val now=System.currentTimeMillis()
        val cert=org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(
            org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(subject,java.math.BigInteger.ONE,Date(now-60000),Date(now+86400000),subject,keys.public)
                .build(org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA").build(keys.private)))
        val original=java.io.ByteArrayOutputStream().also { out -> com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage());doc.save(out)
        } }.toByteArray()
        val output=java.io.ByteArrayOutputStream();var operations=0
        val options=PdfSigningOptions(placement=NormalizedStampPlacement(.1f,.3f,.8f,.3f),
            appearance=SignatureAppearance(badge=StampBadgeType.QR_VERIFICATION,showDni=false,showDate=false,showTime=false),
            qrMetadata=StampQrMetadata("AB".repeat(32),"DEAD","CD".repeat(32),1))
        PadesSignerEngine().signConfiguredPdf(original.inputStream(),output,cert.encoded,{ hash ->
            operations++
            kotlin.Result.success(javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(javax.crypto.Cipher.ENCRYPT_MODE,keys.private)
                doFinal(pe.gob.reniec.dnie.core.apdu.ApduConstants.SHA256_DIGEST_INFO_PREFIX+hash)
            })
        },options).getOrThrow()
        val signed=output.toByteArray()
        val file=java.io.File.createTempFile("qr_synthetic_",".pdf",context.cacheDir)
        try {
            file.writeBytes(signed)
            // Android's platform PdfRenderer omits widgets on this device. Use an
            // annotation-aware independent PDF renderer to inspect the final signature.
            val bitmap=com.tom_roush.pdfbox.pdmodel.PDDocument.load(signed).use { document ->
                com.tom_roush.pdfbox.rendering.PDFRenderer(document).renderImage(0,2f)
            }
            try {
                if(InstrumentationRegistry.getArguments().getString("qrEvidence")=="true") {
                    java.io.File(context.cacheDir,"qr-r5-pdfbox-render.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                    java.io.File(context.cacheDir,"qr-r5-synthetic-signed.pdf").writeBytes(signed)
                }
                val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                val payload=MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width,bitmap.height,pixels)))).text
                val record=StampQrCodec.decode(payload)
                assertFalse(record.metadata.preview);assertNull(record.dni);assertNull(record.moment)
                assertEquals(StampQrMatch.MATCH,StampQrCodec.compareCertificate(record,cert))
                assertEquals(StampQrMatch.MATCH,StampQrCodec.compareOriginal(record,StampQrCodec.digest(original.inputStream())))
                assertEquals(StampQrMatch.DIFFERENT,StampQrCodec.compareOriginal(record,StampQrCodec.digest(signed.inputStream())))
                val originalFile=java.io.File.createTempFile("qr_original_",".pdf",context.cacheDir)
                val certificateFile=java.io.File.createTempFile("qr_cert_",".cer",context.cacheDir)
                try {
                    originalFile.writeBytes(original);certificateFile.writeBytes(cert.encoded)
                    val repository=pe.gob.reniec.dnie.data.qr.AndroidStampQrRepository(context.contentResolver)
                    val digest=repository.original(android.net.Uri.fromFile(originalFile).toString())
                    val imported=repository.certificate(android.net.Uri.fromFile(certificateFile).toString())
                    assertEquals(StampQrMatch.MATCH,StampQrCodec.compareOriginal(record,digest))
                    assertEquals(record.name,imported.citizenName.trim())
                    assertEquals(StampQrMatch.MATCH,StampQrCodec.compareCertificate(record,
                        pe.gob.reniec.dnie.crypto.X509CertificateParser.toX509Certificate(imported.rawDerBytes)))
                } finally { originalFile.delete();certificateFile.delete() }
            } finally { bitmap.recycle() }
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(signed).use { doc ->
                val signature=doc.signatureDictionaries.single()
                val cms=org.bouncycastle.cms.CMSSignedData(org.bouncycastle.cms.CMSProcessableByteArray(signature.getSignedContent(signed)),signature.getContents(signed))
                assertTrue(cms.signerInfos.signers.single().verify(org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder().build(cert)))
            }
            assertEquals(1,operations)
        } finally { file.delete() }
    }
    @Test fun previewHasNoFileOrTrustActionsAndClearRemovesClaims() {
        val state=mutableStateOf(StampQrUiState(record=preview()))
        rule.setContent { DnieTheme(darkTheme=false) { StampQrScreen(state.value,{}, {},{}, {}, {}) { state.value=StampQrUiState() } } }
        rule.onNodeWithText("Vista previa · no cotejable").assertExists()
        rule.onNodeWithTag("stamp_qr_original").assertDoesNotExist()
        rule.onNodeWithTag("stamp_qr_certificate").assertDoesNotExist()
        rule.onNodeWithText("Comprobar confianza y revocación").assertDoesNotExist()
        rule.onNodeWithTag("stamp_qr_clear").performScrollTo().performClick()
        rule.onNodeWithText("ANA QUISPE").assertDoesNotExist()
    }
    @Test fun manualEntryUsesSameParserAndForeignQrIsRejected() {
        val state=mutableStateOf(StampQrUiState())
        rule.setContent { DnieTheme(darkTheme=true) { StampQrScreen(state.value,{}, {},{ payload ->
            state.value=try { StampQrUiState(record=StampQrCodec.decode(payload)) }
            catch(_: Exception) { StampQrUiState(message="QR no compatible") }
        }, {}, {}, {}) } }
        rule.onNodeWithText("Pegar contenido QR").performClick()
        rule.onNodeWithTag("stamp_qr_payload").performTextInput("https://example.com")
        rule.onNodeWithTag("stamp_qr_read").performScrollTo().performClick()
        rule.onNodeWithText("QR no compatible").assertExists()
        rule.onNodeWithTag("stamp_qr_original").assertDoesNotExist()
    }
    @Test fun androidVectorQrCanBeDecodedIndependently()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        val options=PdfSigningOptions(appearance=SignatureAppearance(badge=StampBadgeType.QR_VERIFICATION))
        val bitmap=AndroidStampPreview.stamp(context,600f,240f,"ANA MARÍA QUISPE","12345678",options,Date(0))
        try {
            val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            for(i in pixels.indices) if(pixels[i] ushr 24==0) pixels[i]=0xFFFFFF
            val payload=MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width,bitmap.height,pixels)))).text
            assertEquals(StampLayoutEngine.publicQrPayload("ANA MARÍA QUISPE","12345678",options,0),payload)
            assertTrue(StampQrCodec.decode(payload).metadata.preview)
        } finally { bitmap.recycle() }
    }
}
