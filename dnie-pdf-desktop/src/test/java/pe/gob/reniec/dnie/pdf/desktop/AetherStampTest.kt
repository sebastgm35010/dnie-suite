package pe.gob.reniec.dnie.pdf.desktop

import java.io.ByteArrayOutputStream
import java.nio.file.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField
import org.apache.pdfbox.rendering.PDFRenderer
import org.bouncycastle.cms.*
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import pe.gob.reniec.dnie.pdf.*

class AetherStampTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun allPagesOneSignaturePreservesOriginalAndIsCryptographicallyValid()=runBlocking<Unit> {
        val source=ByteArrayOutputStream().also { output -> PDDocument().use { doc ->
            for(rotation in listOf(0,90,180,270)) doc.addPage(PDPage(PDRectangle(700f,800f)).apply { cropBox=PDRectangle(30f,40f,500f,650f);this.rotation=rotation })
            doc.save(output)
        } }.toByteArray()
        val identity=TestSigningIdentity();val output=ByteArrayOutputStream()
        JvmPdfSigner(folder.root.toPath()).signConfiguredPdf(source.inputStream(),output,identity.certificate.encoded,
            { kotlin.Result.success(identity.sign(it)) },PdfSigningOptions(pageTarget=StampPageTarget.ALL_PAGES,
                placement=NormalizedStampPlacement(.1f,.2f,.7f,.4f))).getOrThrow()
        assertEquals(1,identity.operations);val bytes=output.toByteArray();assertArrayEquals(source,bytes.copyOf(source.size))
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1,doc.signatureDictionaries.size)
            val field=doc.documentCatalog.acroForm.fields.single() as PDSignatureField
            assertEquals(4,field.widgets.size)
            val fontObjects=mutableSetOf<org.apache.pdfbox.cos.COSDictionary>()
            field.widgets.forEachIndexed { index,widget ->
                assertTrue(widget.cosObject.getDictionaryObject("Parent")===field.cosObject)
                assertTrue(doc.getPage(index).annotations.any { it.cosObject===widget.cosObject })
                assertNotNull(widget.appearance.normalAppearance.appearanceStream)
                val resources=widget.appearance.normalAppearance.appearanceStream.resources
                resources.fontNames.forEach { fontObjects+=resources.getFont(it).cosObject }
                val rendered=PDFRenderer(doc).renderImage(index,.5f)
                assertTrue(rendered.width>0);rendered.flush()
            }
            assertEquals("Fonts are shared, never embedded again per page",2,fontObjects.size)
            val sig=doc.signatureDictionaries.single();assertPadesProfile(sig,bytes,identity.certificate)
            val cms=CMSSignedData(CMSProcessableByteArray(sig.getSignedContent(bytes)),sig.getContents(bytes))
            assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().build(identity.certificate)))
        }
    }
    @Test fun vectorQrCanBeDecodedIndependentlyAndMatchesPublicSummary() {
        val options=PdfSigningOptions(appearance=SignatureAppearance(badge=StampBadgeType.QR_VERIFICATION))
        val image=DesktopStampRenderer.renderPreviewImage(1600,640,"ANA MARIA QUISPE","12345678",options,600f,240f,0)
        val pixels=image.getRGB(0,0,image.width,image.height,null,0,image.width)
        // Composite transparent pixels on white for a scanner, without changing the PDF.
        for(i in pixels.indices) if(pixels[i] ushr 24==0) pixels[i]=0xFFFFFF
        val result=MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width,image.height,pixels))))
        assertEquals("DNI:12345678|NOMBRE:ANA MARIA QUISPE|FECHA:1969-12-31T19:00:00-05:00|PAdES",result.text)
        image.flush()
    }
    @Test fun galleryExercisesEveryStyleColorAndTypography() {
        val evidence=System.getProperty("dnie.pdf.visibleEvidence")?.let(Path::of)
        evidence?.let(Files::createDirectories)
        val ink=BufferedImage(240,80,BufferedImage.TYPE_INT_ARGB).also { image -> image.createGraphics().let { g ->
            try { g.color=java.awt.Color(0x163356);g.stroke=java.awt.BasicStroke(3f);g.drawLine(20,60,80,20);g.drawLine(80,20,120,60);g.drawArc(105,10,90,60,0,260) } finally { g.dispose() }
        } }
        val png=ByteArrayOutputStream().also { ImageIO.write(ink,"png",it) }.toByteArray();ink.flush()
        PDDocument().use { doc ->
            for(color in StampColorTheme.entries) for(style in StampStyle.entries) {
                val page=PDPage(PDRectangle(420f,210f));doc.addPage(page)
                val options=PdfSigningOptions(reason="Doy conformidad con el documento y sus anexos",location="Lima, Perú",reniecTimestamp=true,
                    appearance=SignatureAppearance(style=style,colorTheme=color,showHandwritten=style==StampStyle.INK,handwrittenPng=png))
                PDPageContentStream(doc,page).use { content ->
                    content.beginText();content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA),10f);content.newLineAtOffset(20f,190f)
                    content.showText("VISTA PREVIA SINTETICA - ${style.label} / ${color.label}");content.endText()
                    content.saveGraphicsState();content.transform(org.apache.pdfbox.util.Matrix.getTranslateInstance(20f,25f))
                    DesktopStampRenderer.draw(doc,content,380f,145f,"ANA MARÍA QUISPE MAMANI","12345678",options,0);content.restoreGraphicsState()
                }
                assertTrue(page.resources.fontNames.map { page.resources.getFont(it).name }.any { it.contains("Bold") } || style==StampStyle.HANDWRITTEN_ONLY)
                if(evidence!=null) { val image=PDFRenderer(doc).renderImage(doc.numberOfPages-1,2f)
                    ImageIO.write(image,"png",evidence.resolve("stamp-${style.name}-${color.name}.png").toFile());image.flush() }
            }
            evidence?.let { doc.save(it.resolve("aether-sellos-sinteticos.pdf").toFile()) }
        }
    }
}
