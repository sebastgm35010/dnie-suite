package pe.gob.reniec.dnie.pdf.desktop

import java.awt.Color
import java.awt.geom.AffineTransform
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.interactive.annotation.*
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField
import pe.gob.reniec.dnie.pdf.*

/** Appearance only; consumes public identity, never PIN, transport or private signing operations. */
internal object DesktopStampRenderer {
    fun apply(document: PDDocument, signature: PDSignature, options: PdfSigningOptions,
              name: String, dni: String, timestamp: Long) {
        require(options.pageNumber in 1..document.numberOfPages) { "Pagina de sello fuera del PDF" }
        require(options.appearance.style in listOf(StampStyle.CARD, StampStyle.MINIMAL) && !options.appearance.requiresHandwritten) {
            "Este incremento admite tarjeta o minimalista, sin manuscrita"
        }
        val page = document.getPage(options.pageNumber - 1)
        val crop = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360
        require(rotation in listOf(0,90,180,270))
        val p = options.placement
        val w = p.width * if (rotation % 180 == 0) crop.width else crop.height
        val h = p.height * if (rotation % 180 == 0) crop.height else crop.width
        val rect = when(rotation) {
            90 -> PDRectangle(crop.lowerLeftX+p.top*crop.width, crop.lowerLeftY+p.left*crop.height, p.height*crop.width,p.width*crop.height)
            180 -> PDRectangle(crop.lowerLeftX+(1-p.left-p.width)*crop.width,crop.lowerLeftY+p.top*crop.height,w,h)
            270 -> PDRectangle(crop.lowerLeftX+(1-p.top-p.height)*crop.width,crop.lowerLeftY+(1-p.left-p.width)*crop.height,p.height*crop.width,p.width*crop.height)
            else -> PDRectangle(crop.lowerLeftX+p.left*crop.width,crop.lowerLeftY+(1-p.top-p.height)*crop.height,w,h)
        }
        val field = document.documentCatalog.acroForm.fieldTree.filterIsInstance<PDSignatureField>()
            .single { it.signature?.cosObject === signature.cosObject }
        val widget = field.widgets.single()
        widget.rectangle = rect; widget.page = page; widget.isPrinted = true
        val stream = PDAppearanceStream(document).apply {
            resources = PDResources(); bBox = PDRectangle(w,h)
            setMatrix(when(rotation) {
                90 -> AffineTransform(0.0,1.0,-1.0,0.0,h.toDouble(),0.0)
                180 -> AffineTransform(-1.0,0.0,0.0,-1.0,w.toDouble(),h.toDouble())
                270 -> AffineTransform(0.0,-1.0,1.0,0.0,0.0,w.toDouble())
                else -> AffineTransform()
            })
        }
        val appearance = options.appearance
        fun date(pattern: String) = SimpleDateFormat(pattern).apply { timeZone=TimeZone.getTimeZone("America/Lima") }.format(Date(timestamp))
        val rows = buildList {
            add("FIRMADO DIGITALMENTE"); add(name)
            if (appearance.showDni && dni.isNotBlank()) add("DNI: $dni")
            val moment = listOfNotNull(if(appearance.showDate) date("dd/MM/yyyy") else null,
                if(appearance.showTime) date("HH:mm:ss 'UTC-05'") else null).joinToString(" ")
            if(moment.isNotBlank()) add(moment)
            if(appearance.showReason && options.reason.isNotBlank()) add("Motivo: ${options.reason}")
            if(appearance.showLocation && options.location.isNotBlank()) add("Lugar: ${options.location}")
        }
        val font = requireNotNull(DesktopStampRenderer::class.java.getResourceAsStream(
            "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")).use { PDType0Font.load(document,it,false) }
        val margin=6f
        fun wrap(size: Float): List<String> = rows.flatMap { row ->
            require(row.length <= 4096 && row.none { it.isISOControl() }) { "Texto del sello no admitido" }
            val lines=mutableListOf<String>(); var line=""
            for (cp in row.codePoints().toArray()) {
                val c=String(Character.toChars(cp)); val next=line+c
                if(font.getStringWidth(next)*size/1000 > w-2*margin) {
                    require(line.isNotEmpty()) { "Sello demasiado estrecho" }; lines.add(line); line=c
                } else line=next
            }
            if(line.isNotEmpty()) lines.add(line)
            lines
        }
        var size=10f; var lines=wrap(size)
        while(lines.size*size*1.25f > h-2*margin && size > 6f) { size-=0.5f; lines=wrap(size) }
        require(lines.size*size*1.25f <= h-2*margin) { "El texto no cabe; amplie el sello o quite campos" }
        PDPageContentStream(document,stream).use { content ->
            content.setNonStrokingColor(Color.WHITE); content.addRect(0f,0f,w,h); content.fill()
            content.setStrokingColor(Color.BLACK)
            if(appearance.style==StampStyle.CARD) { content.setLineWidth(0.8f); content.addRect(0.5f,0.5f,w-1,h-1); content.stroke() }
            content.setNonStrokingColor(Color.BLACK); content.beginText(); content.setFont(font,size)
            content.setLeading(size*1.25f); content.newLineAtOffset(margin,h-margin-size)
            lines.forEachIndexed { i,line -> if(i>0) content.newLine(); content.showText(line) }; content.endText()
        }
        widget.appearance = PDAppearanceDictionary().apply { setNormalAppearance(stream) }
        widget.cosObject.isNeedToBeUpdated=true
        page.cosObject.isNeedToBeUpdated=true
        field.cosObject.isNeedToBeUpdated=true
    }
}
