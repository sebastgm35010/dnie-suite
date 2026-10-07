package pe.gob.reniec.dnie.desktop

import java.nio.file.Files
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
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
import pe.gob.reniec.dnie.domain.signing.DocumentSignStatus

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
}
