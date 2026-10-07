package pe.gob.reniec.dnie.domain.signing

import pe.gob.reniec.dnie.data.signing.PdfDocumentMetadata

import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult
import pe.gob.reniec.dnie.pdf.*
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.util.Date
import java.util.zip.ZipInputStream
import javax.crypto.Cipher

class BatchSignerTest {
    @get:Rule val directory = TemporaryFolder()
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val certificate = run {
        CryptoProviderInitializer.init()
        val name = X500Name("CN=TITULAR PRUEBA, SERIALNUMBER=12345678, C=PE")
        JcaX509CertificateConverter().setProvider(CryptoProviderInitializer.provider).getCertificate(
            JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date(0), Date(4102444800000L), name, keys.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(CryptoProviderInitializer.provider).build(keys.private)))
    }
    private inner class Card(val failAt: Int = 0, val pin: PinVerificationResult = PinVerificationResult.Success, val corrupt: Boolean = false,
                             val rejectPinAt: Int = 0) : DnieTransceiverInterface {
        var verify = 0; var cert = 0; var sign = 0; var selects = 0; var closed = false
        var authorized = false
        override suspend fun selectPkiApplet(): Result<Unit> { selects++; return Result.success(Unit) }
        override suspend fun verifyPin(pin: String): Result<PinVerificationResult> {
            verify++
            val result = if (verify == rejectPinAt) PinVerificationResult.InvalidPin(2) else this.pin
            authorized = result == PinVerificationResult.Success
            return Result.success(result)
        }
        override suspend fun readCertificate(type: CertificateType): Result<ByteArray> { cert++; assertEquals(CertificateType.SIGNATURE, type); return Result.success(certificate.encoded) }
        override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> {
            if (!authorized) return Result.failure(IllegalStateException("6982: autorización consumida"))
            authorized = false
            sign++
            if (sign == failAt) return Result.failure(IllegalStateException("NFC interrumpido"))
            if (corrupt) return Result.success(ByteArray(256))
            return Result.success(Cipher.getInstance("RSA/ECB/PKCS1Padding", CryptoProviderInitializer.provider).run {
                init(Cipher.ENCRYPT_MODE, keys.private); doFinal(ApduConstants.SHA256_DIGEST_INFO_PREFIX + hash32Bytes)
            })
        }
        override fun close() { closed = true }
    }
    private fun doc(name: String, content: ByteArray = "texto original".toByteArray(), pdf: Boolean = false, options: PdfSigningOptions = PdfSigningOptions()): SigningDocument {
        val file = directory.newFile().apply { writeBytes(content) }
        return SigningDocument(name = name, file = file, size = file.length(), pages = if (pdf) listOf(PdfPageSize(595f, 842f)) else emptyList(), options = options)
    }
    private val signer get() = BatchSigner(PadesSignerEngine())
    @Test fun handwrittenOnlyCannotSignWithoutTheIcaoImage() = runTest {
        val card = Card()
        val document = doc("acta.pdf", pdf = true, options = PdfSigningOptions(appearance = SignatureAppearance(style = StampStyle.HANDWRITTEN_ONLY)))
        val result = signer.sign(listOf(document), card, "1234", directory.newFolder())
        assertEquals(0, card.sign); assertTrue(result.sessionError!!.contains("manuscrita"))
    }
    @Test fun protectedPdfIsSignedDetachedWithoutDecryptingOrChangingIt() = runTest {
        val file = directory.newFile()
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { pdf ->
            pdf.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
            pdf.protect(com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner-secret", "open-secret", com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission()))
            pdf.save(file)
        }
        val original = file.readBytes()
        val document = SigningDocument(name = "protegido.pdf", file = file, size = file.length(), pages = PdfDocumentMetadata.pages(file))
        assertFalse(document.isPdf)
        val result = signer.sign(listOf(document), Card(), "1234", directory.newFolder())
        assertEquals(DocumentSignStatus.SIGNED, result.artifacts.single().status)
        assertEquals("protegido.pdf.p7s", result.artifacts.single().output!!.name)
        assertArrayEquals(original, file.readBytes())
    }
    @Test fun userCanChooseDetachedSigningForAnOrdinaryPdf() = runTest {
        val document = doc("acta.pdf", pdf = true).copy(detached = true)
        val result = signer.sign(listOf(document), Card(), "1234", directory.newFolder())
        assertEquals(DocumentSignStatus.SIGNED, result.artifacts.single().status)
        assertEquals("acta.pdf.p7s", result.artifacts.single().output!!.name)
    }
    @Test fun multipleFilesUseOneSessionAndReauthorizeConsumedPinWithoutChangingOriginals() = runTest {
        val docs = listOf(doc("acta.txt"), doc("informe.docx", ByteArray(12345) { it.toByte() }), doc("planilla.xlsx"))
        val originals = docs.map { it.file.readBytes() }
        val card = Card()
        val result = signer.sign(docs, card, "1234", directory.newFolder())
        assertEquals(3, result.artifacts.count { it.status == DocumentSignStatus.SIGNED })
        assertEquals(3, card.verify); assertEquals(1, card.cert); assertEquals(1, card.selects); assertEquals(3, card.sign); assertTrue(card.closed)
        docs.forEachIndexed { i, d -> assertArrayEquals(originals[i], d.file.readBytes()) }
    }
    @Test fun nfcFailureRetainsCompletedOutputAndStopsRemainingPrivateOperations() = runTest {
        val card = Card(failAt = 2)
        val progress = mutableListOf<List<SignedArtifact>>()
        val result = signer.sign(listOf(doc("uno.txt"), doc("dos.txt"), doc("tres.txt")), card, "1234", directory.newFolder()) { _, _, results, _ -> progress += results }
        assertEquals(listOf(DocumentSignStatus.SIGNED, DocumentSignStatus.INTERRUPTED, DocumentSignStatus.INTERRUPTED), result.artifacts.map { it.status })
        assertTrue(result.artifacts[0].output!!.exists()); assertNull(result.artifacts[1].output)
        assertEquals(2, card.sign); assertEquals(2, card.verify); assertTrue(progress.any { it.size == 1 }); assertTrue(card.closed)
    }
    @Test fun mixedPdfAndBinaryBatchProducesFourVerifiableSignaturesOnOneConnection() = runTest {
        val pdfBytes = com.tom_roush.pdfbox.pdmodel.PDDocument().use { pdf ->
            pdf.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
            ByteArrayOutputStream().also { pdf.save(it) }.toByteArray()
        }
        val documents = listOf(doc("uno.pdf", pdfBytes, pdf = true), doc("dos.bin", ByteArray(8193) { it.toByte() }),
            doc("tres.pdf", pdfBytes, pdf = true, options = PdfSigningOptions(visible = false)), doc("cuatro.txt"))
        val card = Card()
        val result = signer.sign(documents, card, "1234", directory.newFolder())
        assertEquals(4, result.artifacts.count { it.status == DocumentSignStatus.SIGNED })
        assertEquals(1, card.selects); assertEquals(1, card.cert); assertEquals(4, card.verify); assertEquals(4, card.sign)
        val verifier = JcaSimpleSignerInfoVerifierBuilder().setProvider(CryptoProviderInitializer.provider).build(certificate)
        result.artifacts.forEach { artifact ->
            val signedBytes = artifact.output!!.readBytes()
            val cms = if (artifact.document.isPdf) com.tom_roush.pdfbox.pdmodel.PDDocument.load(signedBytes).use { pdf ->
                val signature = pdf.signatureDictionaries.single()
                CMSSignedData(CMSProcessableByteArray(signature.getSignedContent(signedBytes)), signature.getContents(signedBytes))
            } else CMSSignedData(CMSProcessableByteArray(artifact.document.file.readBytes()), signedBytes)
            assertTrue(cms.signerInfos.signers.single().verify(verifier))
        }
        assertTrue(card.closed)
    }
    @Test fun pinRejectionAfterFirstSignatureRetainsItAndStopsRemainingFiles() = runTest {
        val card = Card(rejectPinAt = 2)
        val output = directory.newFolder()
        val result = signer.sign(listOf(doc("uno.txt"), doc("dos.txt"), doc("tres.txt")), card, "1234", output)
        assertEquals(listOf(DocumentSignStatus.SIGNED, DocumentSignStatus.INTERRUPTED, DocumentSignStatus.INTERRUPTED), result.artifacts.map { it.status })
        assertTrue(result.artifacts.first().output!!.exists())
        assertEquals(2, card.verify); assertEquals(1, card.sign); assertEquals(1, card.selects)
        assertTrue(output.walkTopDown().none { it.extension == "part" }); assertTrue(card.closed)
    }
    @Test fun failedSignatureDoesNotPublishPartialFilesOrOverwriteExistingResults() = runTest {
        val document = doc("acta.txt")
        val output = directory.newFolder()
        val result = signer.sign(listOf(document), Card(corrupt = true), "1234", output)
        assertEquals(DocumentSignStatus.INTERRUPTED, result.artifacts.single().status)
        assertTrue(output.walkTopDown().none { it.isFile })
        val existing = java.io.File(output, document.id + "/acta.txt.p7s").apply { requireNotNull(parentFile).mkdirs(); writeText("resultado anterior") }
        val card = Card()
        val repeated = signer.sign(listOf(document), card, "1234", output)
        assertEquals(DocumentSignStatus.FAILED, repeated.artifacts.single().status)
        assertEquals("resultado anterior", existing.readText()); assertEquals(0, card.sign)
    }
    @Test fun localPdfFailureDoesNotPreventFollowingFileFromBeingSigned() = runTest {
        val card = Card()
        val result = signer.sign(listOf(doc("roto.pdf", pdf = true), doc("valido.txt")), card, "1234", directory.newFolder())
        assertEquals(listOf(DocumentSignStatus.FAILED, DocumentSignStatus.SIGNED), result.artifacts.map { it.status })
        assertEquals(1, card.sign)
    }
    @Test fun wrongPinIsNeverRetriedAndDoesNotReadCertificateOrSign() = runTest {
        val card = Card(pin = PinVerificationResult.InvalidPin(2))
        val result = signer.sign(listOf(doc("acta.txt")), card, "9999", directory.newFolder())
        assertEquals(1, card.verify); assertEquals(0, card.cert); assertEquals(0, card.sign)
        assertTrue(result.sessionError!!.contains("2 intento(s)")); assertTrue(card.closed)
    }
    @Test fun handwrittenImageOfAnotherPersonPreventsSigning() = runTest {
        val card = Card()
        val document = doc("acta.pdf", pdf = true, options = PdfSigningOptions(appearance = SignatureAppearance(showHandwritten = true)))
        val result = signer.sign(listOf(document), card, "1234", directory.newFolder(), HandwrittenMark(byteArrayOf(1), "Otro titular", "87654321"))
        assertEquals(0, card.sign); assertTrue(result.sessionError!!.contains("titulares diferentes"))
    }
    @Test fun invalidCardSignatureStopsBatchAndPublishesNoSuccessfulOutput() = runTest {
        val card = Card(corrupt = true)
        val result = signer.sign(listOf(doc("uno.txt"), doc("dos.txt")), card, "1234", directory.newFolder())
        assertTrue(result.artifacts.all { it.status == DocumentSignStatus.INTERRUPTED }); assertEquals(1, card.sign)
        assertTrue(result.artifacts.all { it.output == null })
    }
    @Test fun zipIncludesExactOriginalsSignaturesAndOnePublicCertificate() = runTest {
        val docs = listOf(doc("mismo.txt", "uno".toByteArray()), doc("mismo.txt", "dos".toByteArray()))
        val result = signer.sign(docs, Card(), "1234", directory.newFolder())
        val bytes = ByteArrayOutputStream().also { SignedBatchExporter.write(result.artifacts, it) }.toByteArray()
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip -> while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() } }
        assertArrayEquals(docs[0].file.readBytes(), entries["originales/001_mismo.txt"])
        assertArrayEquals(docs[1].file.readBytes(), entries["originales/002_mismo.txt"])
        assertTrue(entries.containsKey("firmados/001_mismo.txt.p7s"))
        assertEquals(1, entries.keys.count { it.startsWith("certificados/") }); assertTrue(entries.containsKey("LEEME.txt"))
    }
}
