package pe.gob.reniec.dnie.pdf.desktop

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.*
import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.encryption.*
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.bouncycastle.cms.*
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import pe.gob.reniec.dnie.pdf.*

class JvmPdfSignerTest {
    @get:Rule val folder = TemporaryFolder()
    private val identity = TestSigningIdentity()
    private fun source(config: (PDDocument) -> Unit = {}): ByteArray = ByteArrayOutputStream().also { out ->
        PDDocument().use { doc -> doc.addPage(PDPage()); config(doc); doc.save(out) }
    }.toByteArray()
    private suspend fun sign(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val result = JvmPdfSigner(folder.root.toPath()).signConfiguredPdf(bytes.inputStream(), out,
            identity.certificate.encoded, { Result.success(identity.sign(it)) }, PdfSigningOptions(visible = false)).getOrThrow()
        assertEquals(0, result.pageNumber)
        assertTrue(folder.root.listFiles()!!.isEmpty())
        return out.toByteArray()
    }
    private fun verify(bytes: ByteArray, expected: Int) {
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(expected, doc.signatureDictionaries.size)
            doc.signatureDictionaries.forEach { signature ->
                assertPadesProfile(signature, bytes, identity.certificate)
                val cms = CMSSignedData(CMSProcessableByteArray(signature.getSignedContent(bytes)), signature.getContents(bytes))
                assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().build(identity.certificate)))
            }
            assertEquals(bytes.size.toLong(), doc.signatureDictionaries.last().byteRange.let { it[2].toLong()+it[3] })
        }
    }
    @Test fun invisiblePdfHasEmbeddedValidCmsAndNoVisibleWidget() = runBlocking<Unit> {
        val original = source(); val signed = sign(original)
        assertArrayEquals(original, signed.copyOf(original.size)); assertEquals(1, identity.operations)
        verify(signed, 1)
        Loader.loadPDF(signed).use { doc ->
            assertEquals("ETSI.CAdES.detached", doc.signatureDictionaries.single().subFilter)
            assertTrue(doc.documentCatalog.acroForm.fields.flatMap { it.widgets }.all { it.rectangle.width == 0f && it.rectangle.height == 0f })
        }
        val changed = signed.clone(); changed[8] = (changed[8].toInt() xor 1).toByte()
        Loader.loadPDF(signed).use { doc ->
            val signature = doc.signatureDictionaries.single()
            val cms = CMSSignedData(CMSProcessableByteArray(signature.getSignedContent(changed)), signature.getContents(signed))
            assertFalse(runCatching { cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().build(identity.certificate)) }.getOrDefault(false))
        }
    }
    @Test fun secondSignatureKeepsFirstRevisionAndBothCryptographicSignatures() = runBlocking<Unit> {
        val first = sign(source()); val second = sign(first)
        assertArrayEquals(first, second.copyOf(first.size))
        verify(second, 2); assertEquals(2, identity.operations)
    }
    @Test fun malformedEncryptedAndNoChangeCertificationNeverCallPrivateSigner() = runBlocking<Unit> {
        val noChanges = source { doc ->
            val signature = PDSignature()
            val reference = COSDictionary().apply {
                setName(COSName.TRANSFORM_METHOD, "DocMDP")
                setItem(COSName.TRANSFORM_PARAMS, COSDictionary().apply { setInt(COSName.P,1) })
            }
            signature.cosObject.setItem(COSName.REFERENCE, COSArray().apply { add(reference) })
            doc.documentCatalog.cosObject.setItem(COSName.PERMS, COSDictionary().apply { setItem(COSName.DOCMDP, signature.cosObject) })
        }
        val encrypted = source { doc ->
            doc.protect(StandardProtectionPolicy("owner", "", AccessPermission()).apply { encryptionKeyLength = 128 })
        }
        listOf("invalid PDF".toByteArray(), encrypted, noChanges).forEach { bytes ->
            val out = ByteArrayOutputStream()
            val result = JvmPdfSigner(folder.root.toPath()).signConfiguredPdf(bytes.inputStream(), out,
                identity.certificate.encoded, { Result.success(identity.sign(it)) }, PdfSigningOptions(visible=false))
            assertTrue(result.isFailure); assertEquals(0, out.size()); assertEquals(0, identity.operations)
            assertTrue(folder.root.listFiles()!!.isEmpty())
        }
    }
    @Test fun corruptCardResponseNeverPublishesPdf() = runBlocking<Unit> {
        val out = ByteArrayOutputStream()
        val result = JvmPdfSigner(folder.root.toPath()).signConfiguredPdf(source().inputStream(), out,
            identity.certificate.encoded, { Result.success(ByteArray(256)) }, PdfSigningOptions(visible=false))
        assertTrue(result.isFailure); assertEquals(0, out.size()); assertTrue(folder.root.listFiles()!!.isEmpty())
    }
    @Test fun timestampFailureDoesNotPublishOrRepeatPrivateOperation() = runBlocking<Unit> {
        var requests=0
        val out=ByteArrayOutputStream()
        val engine=JvmPdfSigner(folder.root.toPath()) { requests++; throw java.io.IOException("TSA offline") }
        val result=engine.signConfiguredPdf(source().inputStream(),out,identity.certificate.encoded,
            { Result.success(identity.sign(it)) },PdfSigningOptions(visible=false,reniecTimestamp=true))
        assertTrue(result.isFailure);assertEquals(0,out.size());assertEquals(1,identity.operations)
        assertEquals(1,requests);assertTrue(folder.root.listFiles()!!.isEmpty())
    }
    @Test fun timestampDisabledDoesNotContactService() = runBlocking<Unit> {
        val out=ByteArrayOutputStream()
        JvmPdfSigner(folder.root.toPath()) { error("Must not contact TSA") }.signConfiguredPdf(
            source().inputStream(),out,identity.certificate.encoded,{ Result.success(identity.sign(it)) },
            PdfSigningOptions(visible=false)).getOrThrow()
        verify(out.toByteArray(),1)
    }
    @Test fun verifiedTimestampPreservesVisibleAndInvisibleDocumentSignatures() = runBlocking<Unit> {
        val service=TestTimestampAuthority.service()
        for(visible in listOf(false,true)) {
            val out=ByteArrayOutputStream()
            JvmPdfSigner(folder.root.toPath(),service).signConfiguredPdf(source().inputStream(),out,
                identity.certificate.encoded,{ Result.success(identity.sign(it)) },
                PdfSigningOptions(visible=visible,reniecTimestamp=true)).getOrThrow()
            val bytes=out.toByteArray();verify(bytes,1)
            Loader.loadPDF(bytes).use { doc ->
                val sig=doc.signatureDictionaries.single()
                val cms=CMSSignedData(CMSProcessableByteArray(sig.getSignedContent(bytes)),sig.getContents(bytes))
                val signer=cms.signerInfos.signers.single()
                val attribute=signer.unsignedAttributes.get(org.bouncycastle.asn1.ASN1ObjectIdentifier("1.2.840.113549.1.9.16.2.14"))
                val token=org.bouncycastle.tsp.TimeStampToken(CMSSignedData(attribute.attrValues.getObjectAt(0).toASN1Primitive().encoded))
                assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256").digest(signer.signature),token.timeStampInfo.messageImprintDigest)
            }
        }
        assertEquals(2,identity.operations);assertTrue(folder.root.listFiles()!!.isEmpty())
    }

}
