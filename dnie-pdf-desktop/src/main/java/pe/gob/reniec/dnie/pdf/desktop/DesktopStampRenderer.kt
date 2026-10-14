package pe.gob.reniec.dnie.pdf.desktop

import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.interactive.annotation.*
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.rendering.ImageType
import pe.gob.reniec.dnie.pdf.*

/** Appearance adapter only. All composition is in the shared pure layout engine. */
object DesktopStampRenderer {
    fun apply(document: PDDocument,signature: PDSignature,options: PdfSigningOptions,name: String,dni: String,timestamp: Long) {
        val pages=options.pageTarget.pages(document.numberOfPages,options.pageNumber)
        if(pages.size>1) {
            val certification=document.documentCatalog.cosObject.getCOSDictionary(COSName.PERMS)?.getCOSDictionary(COSName.DOCMDP)
            val references=certification?.getCOSArray(COSName.REFERENCE)
            if(references!=null) for(index in 0 until references.size()) {
                val reference=references.getObject(index) as? org.apache.pdfbox.cos.COSDictionary ?: continue
                if(reference.getNameAsString(COSName.TRANSFORM_METHOD)=="DocMDP") require(
                    reference.getCOSDictionary(COSName.TRANSFORM_PARAMS)?.getInt(COSName.P,2)==3
                ) { "El PDF certificado no permite réplicas gráficas; selecciona una sola página" }
            }
        }
        val field=document.documentCatalog.acroForm.fieldTree.filterIsInstance<PDSignatureField>()
            .single { it.signature?.cosObject === signature.cosObject }
        val old=field.widgets.map { it.cosObject }
        document.pages.forEach { page ->
            val annotations=page.annotations
            val retained=annotations.filterNot { widget -> old.any { it === widget.cosObject } }
            // COSArrayList iterator removal does not reliably update /Annots.
            if(retained.size!=annotations.size) { page.annotations=retained;page.cosObject.isNeedToBeUpdated=true }
        }
        // Unmerge the default widget before adding children; no self-parented /Kids.
        listOf(COSName.TYPE,COSName.SUBTYPE,COSName.RECT,COSName.AP,COSName.P,COSName.F).forEach(field.cosObject::removeItem)
        val fonts=loadFonts(document)
        val primary=PDAnnotationWidget().apply { setParent(field) }
        pages.forEachIndexed { pageIndex,number ->
            val page=document.getPage(number-1)
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

        // ISO 32000-2 12.7.5.5: signature fields have one widget only.
        // Other pages carry ordinary visual copies covered by this same CMS.
        val widget: PDAnnotation=if(pageIndex==0) primary else PDAnnotationRubberStamp().apply {
            setName("DNIeVisualCopy");contents="Réplica visual. Comprueba la firma digital del documento."
            isReadOnly=true
        }
        widget.rectangle=rect;widget.page=page;widget.isPrinted=true
        val stream = PDAppearanceStream(document).apply {
            resources = PDResources(); bBox = PDRectangle(w,h)
            setMatrix(when(rotation) {
                90 -> AffineTransform(0.0,1.0,-1.0,0.0,h.toDouble(),0.0)
                180 -> AffineTransform(-1.0,0.0,0.0,-1.0,w.toDouble(),h.toDouble())
                270 -> AffineTransform(0.0,-1.0,1.0,0.0,0.0,w.toDouble())
                else -> AffineTransform()
            })
        }

        PDPageContentStream(document,stream).use { content -> draw(document,content,w,h,name,dni,options,timestamp,fonts) }
        widget.appearance=PDAppearanceDictionary().apply { setNormalAppearance(stream) }
        page.annotations.add(widget)
        // Incremental writers must reach replicas through the original page tree,
        // not through extra signature widgets. Mark every existing ancestor.
        var ancestor=page.cosObject
        val visited=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<org.apache.pdfbox.cos.COSDictionary,Boolean>())
        while(true) {
            require(visited.size<64 && visited.add(ancestor)) { "Árbol de páginas no compatible" }
            ancestor.isNeedToBeUpdated=true
            ancestor=ancestor.getCOSDictionary(COSName.PARENT) ?: break
        }
        widget.cosObject.isNeedToBeUpdated=true
        }
        field.setWidgets(listOf(primary));field.cosObject.isNeedToBeUpdated=true
        document.documentCatalog.acroForm.cosObject.isNeedToBeUpdated=true
        document.documentCatalog.cosObject.isNeedToBeUpdated=true
    }

    fun renderPreviewImage(widthPx: Int,heightPx: Int,name: String,dni: String,options: PdfSigningOptions,
        widthPoints: Float=widthPx/2f,heightPoints: Float=heightPx/2f,timestamp: Long=System.currentTimeMillis()): BufferedImage {
        require(widthPx in 1..4096 && heightPx in 1..4096 && widthPx.toLong()*heightPx<=8_000_000)
        return PDDocument().use { document ->
            val page=PDPage(PDRectangle(widthPoints,heightPoints));document.addPage(page)
            PDPageContentStream(document,page).use { draw(document,it,widthPoints,heightPoints,name,dni,options,timestamp) }
            val source=PDFRenderer(document).renderImage(0,minOf(widthPx/widthPoints,heightPx/heightPoints),ImageType.ARGB)
            source
        }
    }

    fun draw(document: PDDocument, content: PDPageContentStream, width: Float, height: Float,
        name: String, dni: String, options: PdfSigningOptions, timestamp: Long) =
        draw(document,content,width,height,name,dni,options,timestamp,loadFonts(document))

    private data class Fonts(val regular: PDType0Font,val bold: PDType0Font)
    private fun loadFonts(document: PDDocument)=Fonts(font(document,false),font(document,true))
    private fun draw(document: PDDocument, content: PDPageContentStream, width: Float, height: Float,
        name: String, dni: String, options: PdfSigningOptions, timestamp: Long,fonts: Fonts) {
        val regular=fonts.regular
        val bold=fonts.bold
        fun safe(text: String, strong: Boolean): String {
            val font=if(strong) bold else regular
            return text.codePoints().toArray().joinToString("") { cp ->
                val char=String(Character.toChars(cp))
                try { font.getStringWidth(char); char } catch(_: IllegalArgumentException) { "?" }
            }
        }
        val scene=StampLayoutEngine.layout(width,height,name,dni,options,timestamp) { text,strong,size ->
            (if(strong) bold else regular).getStringWidth(safe(text,strong))*size/1000f
        }
        fun fill(rgb: Int) { content.setNonStrokingColor(((rgb shr 16) and 255)/255f,((rgb shr 8) and 255)/255f,(rgb and 255)/255f) }
        fun stroke(rgb: Int) { content.setStrokingColor(((rgb shr 16) and 255)/255f,((rgb shr 8) and 255)/255f,(rgb and 255)/255f) }
        fun box(b: StampElement.Box) {
            val x=b.x;val y=height-b.y-b.height;val w=b.width;val h=b.height
            val r=minOf(b.radius,w/2,h/2);val k=.55228475f*r
            if(r<=0) content.addRect(x,y,w,h)
            else {
                content.moveTo(x+r,y);content.lineTo(x+w-r,y);content.curveTo(x+w-r+k,y,x+w,y+r-k,x+w,y+r)
                content.lineTo(x+w,y+h-r);content.curveTo(x+w,y+h-r+k,x+w-r+k,y+h,x+w-r,y+h)
                content.lineTo(x+r,y+h);content.curveTo(x+r-k,y+h,x,y+h-r+k,x,y+h-r)
                content.lineTo(x,y+r);content.curveTo(x,y+r-k,x+r-k,y,x+r,y);content.closePath()
            }
        }
        for(element in scene.elements) when(element) {
            is StampElement.Box -> {
                element.fill?.let { fill(it);box(element);content.fill() }
                element.stroke?.let { stroke(it);content.setLineWidth(element.thickness);box(element);content.stroke() }
            }
            is StampElement.Line -> { stroke(element.color);content.setLineWidth(element.thickness)
                content.moveTo(element.x,height-element.y);content.lineTo(element.endX,height-element.endY);content.stroke() }
            is StampElement.Path -> { stroke(element.color);content.setLineWidth(element.thickness)
                element.points.forEachIndexed { index,p -> if(index==0) content.moveTo(p.first,height-p.second) else content.lineTo(p.first,height-p.second) }
                if(element.closed) content.closePath();content.stroke() }
            is StampElement.Text -> { fill(element.color);content.beginText();content.setFont(if(element.bold) bold else regular,element.size)
                content.newLineAtOffset(element.x,height-element.baseline);content.showText(safe(element.text,element.bold));content.endText() }
            is StampElement.Handwriting -> {
                val image=PDImageXObject.createFromByteArray(document,requireNotNull(options.appearance.handwrittenPng),"Rúbrica")
                val scale=minOf(element.width/image.width,element.height/image.height)
                val w=image.width*scale;val h=image.height*scale
                content.drawImage(image,element.x+(element.width-w)/2,height-element.y-(element.height+h)/2,w,h)
            }
        }
    }

    private fun font(document: PDDocument,bold: Boolean)=requireNotNull(DesktopStampRenderer::class.java.getResourceAsStream(
        "/fonts/LiberationSans-${if(bold) "Bold" else "Regular"}.ttf")).use { PDType0Font.load(document,it,false) }
}
