package pe.gob.reniec.dnie.pdf.desktop

import java.io.ByteArrayOutputStream
import java.nio.file.*
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.bouncycastle.cms.*
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import pe.gob.reniec.dnie.pdf.*

class DesktopVisibleSignatureTest {
    @get:Rule val folder=TemporaryFolder()
    private val identity=TestSigningIdentity()
    private fun input(rotation: Int=0)=ByteArrayOutputStream().also { out ->
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle(700f,800f)).apply { cropBox=PDRectangle(30f,40f,500f,650f); this.rotation=rotation })
            doc.addPage(PDPage()); doc.save(out)
        }
    }.toByteArray()
    private suspend fun signed(bytes: ByteArray, options: PdfSigningOptions): ByteArray {
        val out=ByteArrayOutputStream()
        val result=JvmPdfSigner(folder.root.toPath()).signConfiguredPdf(bytes.inputStream(),out,
            identity.certificate.encoded,{Result.success(identity.sign(it))},options).getOrThrow()
        assertEquals(options.pageNumber,result.pageNumber)
        assertArrayEquals(bytes,out.toByteArray().copyOf(bytes.size)); return out.toByteArray()
    }
    private fun verify(bytes: ByteArray) = Loader.loadPDF(bytes).use { doc ->
        assertEquals(bytes.size, doc.signatureDictionaries.last().byteRange.let { it[2] + it[3] })
        doc.signatureDictionaries.forEach { sig ->
            assertPadesProfile(sig, bytes, identity.certificate)
            val cms=CMSSignedData(CMSProcessableByteArray(sig.getSignedContent(bytes)),sig.getContents(bytes))
            assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().build(identity.certificate)))
        }
    }
    @Test fun stampRendersAtNormalizedPositionForAllRotationsAndCropOffsets() = runBlocking<Unit> {
        for(rotation in listOf(0,90,180,270)) {
            val options=PdfSigningOptions(placement=NormalizedStampPlacement(0.1f,0.2f,0.7f,0.4f),
                reason="Revisión técnica con texto que debe ajustarse en líneas",location="Lima, Perú")
            val bytes=signed(input(rotation),options); verify(bytes)
            Loader.loadPDF(bytes).use { doc ->
                val widget=doc.documentCatalog.acroForm.fields.last().widgets.single()
                assertTrue(widget.rectangle.width>0); assertNotNull(widget.appearance.normalAppearance.appearanceStream)
                assertTrue(doc.getPage(0).annotations.any { it.cosObject===widget.cosObject })
                val image=PDFRenderer(doc).renderImageWithDPI(0,72f)
                var minX=image.width; var maxX=0; var minY=image.height; var maxY=0; var ink=0
                for(y in 0 until image.height) for(x in 0 until image.width) {
                    val rgb=image.getRGB(x,y)
                    if(minOf((rgb shr 16) and 255,(rgb shr 8) and 255,rgb and 255)<235) { ink++; minX=minOf(minX,x); maxX=maxOf(maxX,x); minY=minOf(minY,y); maxY=maxOf(maxY,y) }
                }
                assertTrue("Missing appearance $rotation",ink>200)
                assertEquals(image.width*0.1f,minX.toFloat(),3f); assertEquals(image.height*0.2f,minY.toFloat(),3f)
                assertEquals(image.width*0.8f,maxX.toFloat(),3f); assertEquals(image.height*0.6f,maxY.toFloat(),3f)
                System.getProperty("dnie.pdf.visibleEvidence")?.let { root ->
                    val path=Path.of(root); Files.createDirectories(path)
                    Files.write(path.resolve("visible-$rotation.pdf"),bytes); ImageIO.write(image,"png",path.resolve("visible-$rotation.png").toFile())
                }
            }
        }
    }
    @Test fun secondPageStampPreservesFirstSignatureAndHonorsHiddenFields() = runBlocking<Unit> {
        val first=signed(input(),PdfSigningOptions(pageNumber=2,placement=NormalizedStampPlacement(0.1f,0.2f,0.7f,0.4f)))
        val second=signed(first,PdfSigningOptions(pageNumber=1,placement=NormalizedStampPlacement(0.1f,0.2f,0.7f,0.4f),
            appearance=SignatureAppearance(showDni=false,showDate=false,showTime=false,showReason=false,showLocation=false),
            reason="HIDDEN REASON",location="HIDDEN LOCATION"))
        verify(second)
        Loader.loadPDF(second).use { doc ->
            assertEquals(2,doc.signatureDictionaries.size)
            val last=doc.documentCatalog.acroForm.fields.last().widgets.single()
            assertTrue(doc.getPage(0).annotations.any { it.cosObject===last.cosObject })
            val stream=last.appearance.normalAppearance.appearanceStream
            val encoded=stream.contents.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(encoded.contains("HIDDEN"))
            assertFalse(doc.signatureDictionaries.last().reason.orEmpty().contains("HIDDEN"))
        }
    }
    @Test fun invalidPageOrOverflowNeverCallsPrivateSigner() = runBlocking<Unit> {
        for(options in listOf(PdfSigningOptions(pageNumber=3), PdfSigningOptions(placement=NormalizedStampPlacement(0f,0f,0.05f,0.02f),reason="long ".repeat(200)))) {
            val out=ByteArrayOutputStream()
            assertTrue(JvmPdfSigner(folder.root.toPath()).signConfiguredPdf(input().inputStream(),out,identity.certificate.encoded,
                {Result.success(identity.sign(it))},options).isFailure)
            assertEquals(0,out.size()); assertEquals(0,identity.operations)
        }
    }
}
