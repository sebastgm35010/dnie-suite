package pe.gob.reniec.dnie.pdf.desktop
import java.nio.file.Files
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.common.PDRectangle
class DesktopPdfPreviewTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun previewUsesCroppedRotatedPageAndClosesDocument() {
        val file=folder.root.toPath().resolve("view.pdf")
        PDDocument().use { doc -> doc.addPage(PDPage(PDRectangle(600f,900f)).apply {
            cropBox=PDRectangle(30f,40f,400f,700f);rotation=90
        });doc.save(file.toFile()) }
        val result=DesktopPdfPreview().render(file,1)
        assertEquals(1,result.count);assertTrue(result.image.width>result.image.height)
        assertTrue(result.image.width<=1400)
        Files.delete(file);assertFalse(Files.exists(file))
    }
    @Test fun invalidPageNeverReturnsAnotherPage() {
        val file=folder.root.toPath().resolve("view.pdf")
        PDDocument().use { doc -> doc.addPage(PDPage());doc.save(file.toFile()) }
        assertTrue(runCatching { DesktopPdfPreview().render(file,2) }.isFailure)
        assertTrue(runCatching { DesktopPdfPreview().render(file,0) }.isFailure)
    }
}
