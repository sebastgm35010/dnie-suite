package pe.gob.reniec.dnie.pdf.desktop

import java.io.ByteArrayOutputStream
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.rendering.PDFRenderer
import org.bouncycastle.cms.*
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.runBlocking
import pe.gob.reniec.dnie.pdf.*

class PersonalizedStampTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun transparentPreviewHasAlphaAndNewBadgesRemainVisible() {
        for(badge in listOf(StampBadgeType.CHECK_SEAL,StampBadgeType.LOCK_SEAL)) {
            val options=PdfSigningOptions(appearance=SignatureAppearance(badge=badge,backgroundMode=StampBackgroundMode.TRANSPARENT,showBorder=false))
            val image=DesktopStampRenderer.renderPreviewImage(800,360,"ANA QUISPE","12345678",options)
            try {
                assertEquals(0,image.getRGB(790,10) ushr 24)
                assertTrue((0 until image.width).any {x->(0 until image.height).any {y->image.getRGB(x,y) ushr 24>0}})
            } finally {image.flush()}
        }
    }
    @Test fun personalizedTwoPagePdfRetainsUnderlyingContentAndSingleVerifiedSignature()=runBlocking<Unit> {
        val source=ByteArrayOutputStream().also {out->PDDocument().use {doc->
            repeat(2) {val page=PDPage();doc.addPage(page);PDPageContentStream(doc,page).use {it.setNonStrokingColor(java.awt.Color(0x77CCAA));it.addRect(0f,0f,612f,792f);it.fill()}}
            doc.save(out)
        }}.toByteArray()
        val identity=TestSigningIdentity();val out=ByteArrayOutputStream();var signatures=0
        val options=PdfSigningOptions(pageTarget=StampPageTarget.ALL_PAGES,placement=NormalizedStampPlacement(.1f,.3f,.8f,.3f),
            appearance=SignatureAppearance(backgroundMode=StampBackgroundMode.TRANSPARENT,badge=StampBadgeType.LOCK_SEAL,colorTheme=StampColorTheme.ROYAL_INDIGO,dateFormat=StampDateFormat.ISO_8601,density=StampDensity.SPACIOUS))
        JvmPdfSigner(folder.root.toPath()).signConfiguredPdf(source.inputStream(),out,identity.certificate.encoded,{signatures++;Result.success(identity.sign(it))},options).getOrThrow()
        val bytes=out.toByteArray()
        Loader.loadPDF(bytes).use {doc->
            val signature=doc.signatureDictionaries.single()
            val cms=CMSSignedData(CMSProcessableByteArray(signature.getSignedContent(bytes)),signature.getContents(bytes))
            assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().build(identity.certificate)))
            assertEquals(1,signatures);assertTrue(doc.pages.all {it.annotations.size==1})
            val image=PDFRenderer(doc).renderImage(0)
            System.getProperty("dnie.pdf.visibleEvidence")?.let {output->
                val dir=java.nio.file.Path.of(output);java.nio.file.Files.createDirectories(dir)
                java.nio.file.Files.write(dir.resolve("r7-transparente-sintetico-firmado.pdf"),bytes)
                javax.imageio.ImageIO.write(image,"png",dir.resolve("r7-transparente-sintetico.png").toFile())
            }
            try {assertEquals(0x77CCAA,image.getRGB(500,240) and 0xFFFFFF)}finally{image.flush()}
        }
    }
}
