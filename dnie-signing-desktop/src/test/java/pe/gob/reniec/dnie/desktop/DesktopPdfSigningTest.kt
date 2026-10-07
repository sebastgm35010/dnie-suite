package pe.gob.reniec.dnie.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.*
import org.bouncycastle.cms.*
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import pe.gob.reniec.dnie.desktop.application.DesktopSigningService
import pe.gob.reniec.dnie.desktop.demo.SimulatedSigningCard
import pe.gob.reniec.dnie.desktop.model.*
import pe.gob.reniec.dnie.domain.signing.DocumentSignStatus

class DesktopPdfSigningTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun mixedBatchProducesPdfAndCmsWithOneCardSession() = runBlocking<Unit> {
        val pdf = folder.root.toPath().resolve("prueba.pdf")
        PDDocument().use { doc -> doc.addPage(PDPage()); doc.save(pdf.toFile()) }
        System.getProperty("dnie.pdf.fixture")?.let { value ->
            val fixture = Path.of(value); Files.createDirectories(fixture.parent); Files.copy(pdf, fixture, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        val binary = folder.root.toPath().resolve("original.bin"); Files.write(binary, byteArrayOf(1,2,3))
        val card = SimulatedSigningCard()
        val result = DesktopSigningService().execute(DesktopSigningRequest(listOf(pdf, binary), folder.root.toPath().resolve("jobs")),
            card, "0000", SigningBackend.SIMULATED)
        assertTrue(result.complete); assertEquals(2, card.signatures); assertEquals(1, card.selections); assertEquals(2, card.verifications)
        val signedPdf = result.documents[0].signature!!
        assertTrue(signedPdf.fileName.toString().endsWith("_firmado.pdf"))
        Loader.loadPDF(signedPdf.toFile()).use { doc ->
            val signature = doc.signatureDictionaries.single(); val bytes = Files.readAllBytes(signedPdf)
            val cms = CMSSignedData(CMSProcessableByteArray(signature.getSignedContent(bytes)),signature.getContents(bytes))
            assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().build(card.certificate)))
        }
        assertTrue(result.documents[1].signature!!.fileName.toString().endsWith(".p7s"))
    }
    @Test fun invalidPdfFailsIndividuallyAndNextFileStillSignsWithoutCmsFallback() = runBlocking<Unit> {
        val pdf = folder.root.toPath().resolve("invalido.pdf"); Files.writeString(pdf, "invalid PDF")
        val binary = folder.root.toPath().resolve("archivo.txt"); Files.writeString(binary, "synthetic")
        val card = SimulatedSigningCard()
        val result = DesktopSigningService().execute(DesktopSigningRequest(listOf(pdf, binary), folder.root.toPath().resolve("jobs")),
            card, "0000", SigningBackend.SIMULATED)
        assertEquals(listOf(DocumentSignStatus.FAILED, DocumentSignStatus.SIGNED), result.documents.map { it.status })
        assertNull(result.documents[0].signature); assertEquals(1, card.signatures)
    }
    @Test fun visibleModeCreatesARealSignatureWidgetInDesktopBatch() = runBlocking<Unit> {
        val pdf=folder.root.toPath().resolve("visible.pdf")
        PDDocument().use { doc -> doc.addPage(PDPage()); doc.save(pdf.toFile()) }
        val result=DesktopSigningService().execute(DesktopSigningRequest(listOf(pdf),folder.root.toPath().resolve("jobs"),
            DesktopPdfMode.VISIBLE, pe.gob.reniec.dnie.pdf.PdfSigningOptions(visible=true)),
            SimulatedSigningCard(),"0000",SigningBackend.SIMULATED)
        assertTrue(result.complete)
        Loader.loadPDF(result.documents.single().signature!!.toFile()).use { doc ->
            val field=doc.documentCatalog.acroForm.fields.single()
            assertTrue(field.widgets.single().rectangle.width>0f)
            assertNotNull(field.widgets.single().appearance.normalAppearance.appearanceStream)
        }
    }
}
