package pe.gob.reniec.dnie.pdf.desktop

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer

/** Bounded preview adapter. Reopens/closes per request, no shared PDFDocument or signing state. */
class DesktopPdfPreview {
    data class Page(val image: BufferedImage,val number: Int,val count: Int,
        val widthPoints: Float=image.width.toFloat(),val heightPoints: Float=image.height.toFloat())
    fun render(file: Path,page: Int): Page {
        require(Files.size(file) in 1..JvmPdfSigner.MAX_INPUT_BYTES)
        return Loader.loadPDF(file.toFile()).use { doc ->
            require(!doc.isEncrypted && page in 1..doc.numberOfPages)
            val p=doc.getPage(page-1); val box=p.cropBox
            require(box.width>0 && box.height>0)
            val scale=minOf(1.5f,1400f/maxOf(box.width,box.height))
            val rotated=p.rotation%180!=0
            Page(PDFRenderer(doc).renderImage(page-1,scale),page,doc.numberOfPages,
                if(rotated) box.height else box.width,if(rotated) box.width else box.height)
        }
    }
}
