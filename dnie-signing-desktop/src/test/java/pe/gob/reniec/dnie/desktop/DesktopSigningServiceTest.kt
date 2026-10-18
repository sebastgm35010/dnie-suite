package pe.gob.reniec.dnie.desktop

import java.nio.file.Files
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.cert.CertificateFactory
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer
import pe.gob.reniec.dnie.desktop.application.DesktopSigningService
import pe.gob.reniec.dnie.desktop.demo.SimulatedSigningCard
import pe.gob.reniec.dnie.desktop.model.*
import pe.gob.reniec.dnie.desktop.storage.DesktopWorkspace
import pe.gob.reniec.dnie.domain.signing.BatchSigner
import pe.gob.reniec.dnie.domain.signing.DocumentSignStatus
import pe.gob.reniec.dnie.domain.signing.SignedBatchExporter
import pe.gob.reniec.dnie.pdf.desktop.JvmPdfSigner

class DesktopSigningServiceTest {
    @get:Rule val directory = TemporaryFolder()
    private fun input(name: String, bytes: ByteArray = "original sintético".toByteArray()) =
        directory.newFolder().toPath().resolve(name).also { Files.write(it, bytes) }

    @Test fun streamingBinaryBatchUsesSharedEngineAndPreservesOriginalsAndCertificate() = runTest {
        val sources = listOf(input("acta.txt"), input("acta.txt", ByteArray(130000) { it.toByte() }), input("ñandú.pdf", "PDF tratado como binario CMS".toByteArray()))
        val originalBytes = sources.map { Files.readAllBytes(it) }
        val card = SimulatedSigningCard()
        val result = DesktopSigningService().execute(DesktopSigningRequest(sources, directory.root.toPath().resolve("jobs"), DesktopPdfMode.DETACHED),
            card, SimulatedSigningCard.DEMO_PIN, SigningBackend.SIMULATED)
        assertTrue(result.complete)
        assertEquals(3, card.signatures); assertEquals(3, card.verifications)
        assertEquals(1, card.selections); assertEquals(1, card.certificateReads); assertEquals(1, card.closes)
        result.documents.forEachIndexed { index, document ->
            assertArrayEquals(originalBytes[index], Files.readAllBytes(sources[index]))
            assertArrayEquals(originalBytes[index], Files.readAllBytes(document.original))
            val cms = CMSSignedData(CMSProcessableByteArray(originalBytes[index]), Files.readAllBytes(document.signature!!))
            assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(CryptoProviderInitializer.provider).build(card.certificate)))
        }
        assertArrayEquals(card.certificate.encoded, Files.readAllBytes(result.directory.resolve("certificate.cer")))
        val pem = Files.readAllBytes(result.directory.resolve("certificate.pem"))
        assertArrayEquals(card.certificate.encoded, CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(pem)).encoded)
        val manifest = Files.readString(result.directory.resolve("result.json"))
        assertTrue(manifest.contains("\"backend\":\"SIMULATED\"")); assertTrue(manifest.contains("ñandú.pdf"))
        assertFalse(manifest.contains("\"pin\"")); assertFalse(manifest.contains("\"can\""))
        assertTrue(Files.walk(result.directory).use { paths -> paths.noneMatch { it.fileName.toString().endsWith(".part") } })
        val names = mutableListOf<String>()
        ZipInputStream(Files.newInputStream(result.directory.resolve("signed-documents.zip"))).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; names += entry.name; zip.closeEntry() }
        }
        assertEquals(3, names.count { it.startsWith("originales/") }); assertEquals(3, names.count { it.startsWith("firmados/") })
        assertEquals(names.size, names.distinct().size)
    }

    @Test fun transportFailureReturnsPartialJobAndDoesNotReplaySigning() = runTest {
        val card = SimulatedSigningCard(failAtSignature = 2)
        val result = DesktopSigningService().execute(DesktopSigningRequest(listOf(input("uno.bin"), input("dos.bin"), input("tres.bin")), directory.newFolder().toPath()),
            card, SimulatedSigningCard.DEMO_PIN, SigningBackend.SIMULATED)
        assertFalse(result.complete)
        assertEquals(listOf(DocumentSignStatus.SIGNED, DocumentSignStatus.INTERRUPTED, DocumentSignStatus.INTERRUPTED), result.documents.map { it.status })
        assertTrue(Files.exists(result.documents.first().signature)); assertNull(result.documents[1].signature)
        assertEquals(2, card.signatures); assertEquals(1, card.closes)
        assertTrue(Files.exists(result.directory.resolve("signed-documents.zip")))
    }

    @Test fun invalidInputClosesCardBeforeSelectionAndCreatesNoJob() = runTest {
        val card = SimulatedSigningCard()
        val root = directory.root.toPath().resolve("jobs")
        try {
            DesktopSigningService().execute(DesktopSigningRequest(listOf(root.resolve("missing")), root), card,
                SimulatedSigningCard.DEMO_PIN, SigningBackend.SIMULATED)
            fail("Debe rechazar la entrada")
        } catch (_: IllegalArgumentException) {}
        assertFalse(Files.exists(root)); assertEquals(0, card.selections); assertEquals(1, card.closes)
    }

    @Test fun wrongPinNeverSignsAndProducesExplicitIncompleteResult() = runTest {
        val card = SimulatedSigningCard()
        val result = DesktopSigningService().execute(DesktopSigningRequest(listOf(input("uno.txt")), directory.newFolder().toPath()),
            card, "9999", SigningBackend.SIMULATED)
        assertFalse(result.complete); assertEquals(0, card.signatures); assertEquals(1, card.verifications); assertEquals(1, card.closes)
        assertFalse(Files.exists(result.directory.resolve("signed-documents.zip")))
        assertTrue(Files.readString(result.directory.resolve("result.json")).contains("\"complete\":false"))
    }

    @Test fun alteringOriginalInvalidatesGeneratedCms() = runTest {
        val card = SimulatedSigningCard()
        val source = input("bytes.bin", byteArrayOf(1, 2, 3))
        val result = DesktopSigningService().execute(DesktopSigningRequest(listOf(source), directory.newFolder().toPath()),
            card, SimulatedSigningCard.DEMO_PIN, SigningBackend.SIMULATED)
        val cms = CMSSignedData(CMSProcessableByteArray(byteArrayOf(1, 2, 4)), Files.readAllBytes(result.documents.single().signature!!))
        val valid = runCatching { cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(CryptoProviderInitializer.provider).build(card.certificate)) }.getOrDefault(false)
        assertFalse(valid)
    }

    @Test fun tamperingWorkspaceCopyOrSignedPdfWithSameSizeBeforeSignOrExportIsRejected() = runTest {
        val pdfBytes = PDDocument().use { pdf ->
            pdf.addPage(PDPage())
            ByteArrayOutputStream().also { pdf.save(it) }.toByteArray()
        }
        val workspace = DesktopWorkspace.create(directory.newFolder().toPath())
        val imported = workspace.import(
            listOf(
                input("uno.txt", "original1".toByteArray()),
                input("dos.txt", "original2".toByteArray()),
                input("tres.pdf", pdfBytes)
            ),
            DesktopPdfMode.INVISIBLE
        )
        // 1. Same-size replacement before signing must be rejected without invoking card signature.
        Files.write(imported[0].file.toPath(), "alterado1".toByteArray())
        assertEquals(imported[0].size, imported[0].file.length())
        val card = SimulatedSigningCard()
        val signed = BatchSigner(JvmPdfSigner(workspace.directory.resolve("pdf-work"))).sign(
            imported, card, SimulatedSigningCard.DEMO_PIN, workspace.directory.resolve("signed").toFile()
        )
        assertEquals(listOf(DocumentSignStatus.FAILED, DocumentSignStatus.SIGNED, DocumentSignStatus.SIGNED), signed.artifacts.map { it.status })
        assertEquals(2, card.signatures)
        // 2. Same-size replacement of approved original before export must be rejected.
        val validSecondBytes = Files.readAllBytes(imported[1].file.toPath())
        Files.write(imported[1].file.toPath(), "alterado2".toByteArray())
        assertEquals(imported[1].size, imported[1].file.length())
        assertTrue(runCatching {
            Files.newOutputStream(workspace.directory.resolve("test-orig.zip")).use { SignedBatchExporter.write(signed.artifacts, it) }
        }.isFailure)
        Files.write(imported[1].file.toPath(), validSecondBytes)
        // 3. Same-size replacement of signed PDF output before export must also be rejected.
        val signedPdfPath = signed.artifacts[2].output!!.toPath()
        val tamperedPdf = Files.readAllBytes(signedPdfPath).also { it[0] = (it[0].toInt() xor 1).toByte() }
        val file = signedPdfPath.toFile()
        file.delete()
        file.outputStream().use { it.write(tamperedPdf) }
        assertEquals(signed.artifacts[2].outputSize, Files.size(signedPdfPath))
        assertTrue(runCatching {
            Files.newOutputStream(workspace.directory.resolve("test-pdf.zip")).use { SignedBatchExporter.write(signed.artifacts, it) }
        }.isFailure)
    }
}
