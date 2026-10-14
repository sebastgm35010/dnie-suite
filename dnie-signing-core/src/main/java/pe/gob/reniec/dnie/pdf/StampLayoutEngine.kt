package pe.gob.reniec.dnie.pdf

import io.nayuki.qrcodegen.QrCode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Public appearance primitives, in points, with origin at the visible TOP LEFT.
 * No platform/graphics/PDF types; adapters only paint this immutable scene. */
sealed interface StampElement {
    data class Box(val x: Float,val y: Float,val width: Float,val height: Float,val radius: Float,
        val fill: Int?=null,val stroke: Int?=null,val thickness: Float=.6f): StampElement
    data class Line(val x: Float,val y: Float,val endX: Float,val endY: Float,val color: Int,val thickness: Float=.5f): StampElement
    data class Text(val x: Float,val baseline: Float,val text: String,val size: Float,val bold: Boolean,val color: Int): StampElement
    data class Path(val points: List<Pair<Float,Float>>,val color: Int,val thickness: Float=1f,val closed: Boolean=false,val fill: Int?=null): StampElement
    data class Circle(val cx: Float,val cy: Float,val radius: Float,val fill: Int?=null,val stroke: Int?=null,val thickness: Float=.6f): StampElement
    data class Handwriting(val x: Float,val y: Float,val width: Float,val height: Float): StampElement
}

data class StampLayout(val width: Float,val height: Float,val baseSize: Float,val elements: List<StampElement>)

/** Shared layout and word wrapping. Measurement is supplied by each PDF font adapter. */
object StampLayoutEngine {
    private data class Block(val text: String,val factor: Float,val bold: Boolean,val color: Int,val label: String="")
    private data class Row(val parts: List<Pair<String,Boolean>>,val size: Float,val color: Int)

    fun wrap(text: String,width: Float,bold: Boolean,size: Float,measure: (String,Boolean,Float)->Float): List<String> {
        require(width > 0 && width.isFinite() && size > 0)
        require(text.length <= 4096 && text.none { it.isISOControl() && !it.isWhitespace() }) { "Texto del sello no admitido" }
        val lines=mutableListOf<String>();var line=""
        for(word in text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)) {
            if(measure(word,bold,size)<=width) {
                val candidate=if(line.isEmpty()) word else "$line $word"
                if(measure(candidate,bold,size)<=width) line=candidate
                else { if(line.isNotEmpty()) lines+=line;line=word }
            } else {
                if(line.isNotEmpty()) { lines+=line;line="" }
                var fragment=""
                val characters=word.codePoints().toArray().map { String(Character.toChars(it)) }
                characters.forEachIndexed { index,char ->
                    val candidate=fragment+char
                    val suffix=if(index<characters.lastIndex) "-" else ""
                    if(measure(candidate+suffix,bold,size)>width) {
                        require(fragment.isNotEmpty()) { "Sello demasiado estrecho; amplíalo" }
                        lines+="$fragment-";fragment=char
                        require(measure(fragment+suffix,bold,size)<=width) { "Sello demasiado estrecho; amplíalo" }
                    } else fragment=candidate
                }
                line=fragment
            }
        }
        if(line.isNotEmpty()) lines+=line
        return lines
    }

    fun layout(width: Float,height: Float,name: String,dni: String,options: PdfSigningOptions,timestamp: Long,
        measure: (String,Boolean,Float)->Float): StampLayout {
        require(width.isFinite() && height.isFinite() && width>0 && height>0)
        val a=options.appearance;val c=a.colorTheme;val elements=mutableListOf<StampElement>()
        if(a.style==StampStyle.HANDWRITTEN_ONLY) {
            require(a.handwrittenPng!=null) { "Lee la firma manuscrita antes de incluirla" }
            return StampLayout(width,height,0f,listOf(StampElement.Handwriting(0f,0f,width,height)))
        }
        val padding=minOf(9f,width*.04f,height*.10f)
        require(!(a.requiresHandwritten && a.badge==StampBadgeType.QR_VERIFICATION)) { "Elige QR o rúbrica para la insignia del sello" }
        val radius=minOf(5f,height*.08f)
        when(a.style) {
            StampStyle.INSTITUTIONAL -> {
                elements+=StampElement.Box(.35f,.35f,width-.7f,height-.7f,radius,c.backgroundRgb,c.borderRgb)
                elements+=StampElement.Box(.65f,radius,3.5f,height-2*radius,1.5f,c.accentRgb)
            }
            StampStyle.CARD -> elements+=StampElement.Box(.35f,.35f,width-.7f,height-.7f,radius,0xFFFFFF,c.borderRgb,.7f)
            else -> elements+=StampElement.Line(padding*.4f,padding,padding*.4f,height-padding,c.accentRgb,2f)
        }
        val column=if(width>=110 && (a.badge!=StampBadgeType.NONE || a.requiresHandwritten)) width*if(a.requiresHandwritten) .34f else .26f else 0f
        if(a.requiresHandwritten) require(a.handwrittenPng!=null) { "Lee la firma manuscrita antes de incluirla" }
        if(column>0f) {
            elements+=StampElement.Line(column,padding,column,height-padding,c.borderRgb)
            val side=minOf(column-2*padding,height-2*padding)
            val x=(column-side)/2f;val y=(height-side)/2f
            when {
                a.requiresHandwritten -> elements+=StampElement.Handwriting(padding,padding,column-2*padding,height-2*padding)
                a.badge==StampBadgeType.SEAL_VECTOR -> seal(elements,x,y,side,c,measure)
                a.badge==StampBadgeType.QR_VERIFICATION -> qr(elements,x,y,side,publicQrPayload(name,dni,options,timestamp),c.accentRgb)
            }
        } else if(a.badge==StampBadgeType.QR_VERIFICATION) {
            error("Amplía el sello para incluir el QR")
        } else if(a.requiresHandwritten) {
            error("Amplía el sello para incluir la rúbrica y sus datos")
        }
        val x=padding+column;val usableWidth=width-x-padding
        val blocks=buildList {
            if(a.customHeader.isNotBlank()) add(Block(a.customHeader,.74f,true,c.accentRgb))
            add(Block(name,1.05f,true,c.titleRgb))
            if(a.showDni && dni.isNotBlank()) add(Block(dni,.82f,false,c.bodyRgb,"DNI: "))
            val moment=listOfNotNull(if(a.showDate) date("dd/MM/yyyy",timestamp) else null,
                if(a.showTime) date("HH:mm:ss 'UTC-05'",timestamp) else null).joinToString(" ")
            if(moment.isNotBlank()) add(Block(moment,.82f,false,c.bodyRgb,if(a.showDate) "Fecha: " else "Hora: "))
            if(a.showReason && options.reason.isNotBlank()) add(Block(options.reason,.82f,false,c.bodyRgb,"Motivo: "))
            if(a.showLocation && options.location.isNotBlank()) add(Block(options.location,.82f,false,c.bodyRgb,"Lugar: "))
        }
        fun rows(base: Float)=blocks.flatMap { block ->
            val size=base*block.factor
            // Measure with the widest face so a bold label can never overflow.
            wrap(block.label+block.text,usableWidth,true,size) { text,_,s -> maxOf(measure(text,true,s),measure(text,block.bold,s)) }
                .mapIndexed { index,text ->
                    val parts=if(index==0 && block.label.isNotEmpty() && text.startsWith(block.label))
                        listOf(block.label to true,text.removePrefix(block.label) to false) else listOf(text to block.bold)
                    Row(parts,size,block.color)
                }
        }
        val tsa=options.reniecTimestamp && a.showTsaBadge
        fun used(rows: List<Row>,base: Float)=rows.sumOf { (it.size*1.34f).toDouble() }.toFloat()+if(tsa) base*1.45f+2f else 0f
        val available=height-2*padding
        fun fits(rows: List<Row>,base: Float)=used(rows,base)<=available &&
            rows.count { kotlin.math.abs(it.size-base*1.05f)<.001f }<=2
        var low=5f;var high=11f
        var fitted=rows(low)
        require(fits(fitted,low)) { "El texto no cabe; amplía el sello o quita campos" }
        repeat(14) {
            val mid=(low+high)/2;val candidate=rows(mid)
            if(fits(candidate,mid)) { low=mid;fitted=candidate } else high=mid
        }
        var top=padding+(available-used(fitted,low))/2f
        fitted.forEach { row ->
            var runX=x;val baseline=top+row.size
            row.parts.forEach { (text,bold) ->
                if(text.isNotEmpty()) elements+=StampElement.Text(runX,baseline,text,row.size,bold,row.color)
                runX+=measure(text,bold,row.size)
            }
            top+=row.size*1.34f
        }
        if(tsa) {
            val text="PAdES-T · TSA RENIEC";val size=minOf(low*.65f,usableWidth/measure(text,true,1f))
            val boxH=low*1.45f;val boxW=minOf(usableWidth,measure(text,true,size)+8f)
            elements+=StampElement.Box(x,top+1f,boxW,boxH,minOf(4f,boxH/2),0xE5EEFF)
            elements+=StampElement.Text(x+4f,top+1f+(boxH+size*.65f)/2,text,size,true,0x00174B)
        }
        return StampLayout(width,height,low,elements.toList())
    }

    fun publicQrPayload(name: String,dni: String,options: PdfSigningOptions,timestamp: Long): String {
        val a=options.appearance
        val moment=when {
            a.showDate && a.showTime -> date("yyyy-MM-dd'T'HH:mm:ssXXX",timestamp)
            a.showDate -> date("yyyy-MM-dd",timestamp)
            a.showTime -> date("HH:mm:ssXXX",timestamp)
            else -> null
        }
        return StampQrCodec.encode(StampQrRecord(options.qrMetadata ?: StampQrMetadata.PREVIEW,
            name.trim(),dni.takeIf { a.showDni && it.isNotBlank() },moment,if(options.reniecTimestamp) "T" else "B"))
    }
    private fun date(pattern: String,time: Long)=SimpleDateFormat(pattern,Locale.ROOT).apply { timeZone=TimeZone.getTimeZone("America/Lima") }.format(Date(time))

    private fun qr(out: MutableList<StampElement>,x: Float,y: Float,side: Float,payload: String,color: Int) {
        val code=QrCode.encodeText(payload,QrCode.Ecc.MEDIUM);val unit=side/(code.size+8)
        require(unit>=.5f) { "Amplía el sello: el QR necesita más espacio para poder escanearse" }
        out+=StampElement.Box(x,y,side,side,0f,0xFFFFFF)
        for(row in 0 until code.size) {
            var col=0
            while(col<code.size) {
                if(!code.getModule(col,row)) { col++;continue }
                val start=col;while(col<code.size && code.getModule(col,row)) col++
                out+=StampElement.Box(x+(start+4)*unit,y+(row+4)*unit,(col-start)*unit,unit,0f,color)
            }
        }
    }
    private fun seal(out: MutableList<StampElement>,x: Float,y: Float,side: Float,theme: StampColorTheme,measure: (String,Boolean,Float)->Float) {
        // Decorative product emblem, not a government seal or trust assertion.
        val color=theme.accentRgb
        out+=StampElement.Circle(x+side*.5f,y+side*.5f,side*.44f,theme.backgroundRgb,color,.8f)
        out+=StampElement.Circle(x+side*.5f,y+side*.5f,side*.39f,null,theme.borderRgb,.45f)
        out+=StampElement.Path(listOf(.29f to .23f,.71f to .23f,.67f to .49f,.60f to .59f,.50f to .66f,.40f to .59f,.33f to .49f).map { (a,b)->x+side*a to y+side*b },color,.6f,true,color)
        out+=StampElement.Path(listOf(.39f to .41f,.47f to .49f,.62f to .33f).map { (a,b)->x+side*a to y+side*b },0xFFFFFF,side*.025f)
        out+=StampElement.Box(x+side*.28f,y+side*.69f,side*.44f,side*.18f,side*.06f,color)
        val size=side*.13f;val label="DNIe"
        out+=StampElement.Text(x+(side-measure(label,true,size))/2,y+side*.83f,label,size,true,0xFFFFFF)
    }
}
