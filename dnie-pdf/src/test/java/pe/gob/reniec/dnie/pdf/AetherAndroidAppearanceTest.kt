package pe.gob.reniec.dnie.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.Date

class AetherAndroidAppearanceTest {
    @Test fun vectorStylesAndColorsRenderWithAndroidPdfAdapterOnJvm() {
        val evidence=System.getProperty("dnie.android.pdf.evidence")?.let(::File)?.apply { mkdirs() }
        for(style in listOf(StampStyle.INSTITUTIONAL,StampStyle.CARD,StampStyle.MINIMAL)) for(color in StampColorTheme.entries) {
            val options=PdfSigningOptions(reniecTimestamp=true,reason="Doy conformidad con el documento y sus anexos",location="Lima, Perú",
                appearance=SignatureAppearance(style=style,colorTheme=color))
            val bytes=VisualStampRenderer.previewPdf(380f,145f,"ANA MARÍA QUISPE MAMANI","12345678",options,Date(0))
            PDDocument.load(bytes).use { doc ->
                val text=PDFTextStripper().getText(doc).replace(Regex("\\s+"),"")
                assertTrue(text.contains("ANAMARÍAQUISPEMAMANI"));assertTrue(text.contains("TSARENIEC"))
                assertTrue(doc.getPage(0).resources.fontNames.map { doc.getPage(0).resources.getFont(it).name }.any { it=="Helvetica-Bold" })
            }
            evidence?.resolve("android-${style.name}-${color.name}.pdf")?.writeBytes(bytes)
        }
    }
}
