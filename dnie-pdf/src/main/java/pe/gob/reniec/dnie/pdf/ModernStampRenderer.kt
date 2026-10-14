package pe.gob.reniec.dnie.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.util.Date

/** Android painter for the exact shared vector scene used on desktop. */
internal object ModernStampRenderer {
    fun draw(doc: PDDocument,cs: PDPageContentStream,width: Float,height: Float,name: String,dni: String,
        reason: String,location: String,date: Date,appearance: SignatureAppearance,reniecTimestamp: Boolean=false) =
        draw(doc,cs,width,height,name,dni,PdfSigningOptions(appearance=appearance,reason=reason,location=location,reniecTimestamp=reniecTimestamp),date.time)

    fun draw(document: PDDocument, content: PDPageContentStream, width: Float, height: Float,
        name: String, dni: String, options: PdfSigningOptions, timestamp: Long) {
        val regular=PDType1Font.HELVETICA
        val bold=PDType1Font.HELVETICA_BOLD
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
}
