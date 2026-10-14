package pe.gob.reniec.dnie.pdf

import java.util.Properties
import java.io.StringReader
import java.io.StringWriter

/** Public design only. Never serializes an image, certificate, document digest or credentials. */
object StampDesignCodec {
    fun encode(o: PdfSigningOptions): String {
        val p=Properties();val a=o.appearance
        fun put(k: String,v: Any){p.setProperty(k,v.toString())}
        put("designVersion",2);put("rubric",a.showHandwritten);put("visible",o.visible);put("style",a.style.name);put("color",a.colorTheme.name);put("badge",a.badge.name)
        put("bgMode",a.backgroundMode.name);put("dateFormat",a.dateFormat.name);put("density",a.density.name);put("border",a.showBorder)
        put("header",a.customHeader.take(160));put("reason",o.reason.take(512));put("location",o.location.take(512))
        put("tsa",o.reniecTimestamp);put("tsaBadge",a.showTsaBadge);put("dni",a.showDni);put("date",a.showDate);put("time",a.showTime)
        put("showReason",a.showReason);put("showLocation",a.showLocation);put("target",o.pageTarget.name);put("page",o.pageNumber)
        put("left",o.placement.left);put("top",o.placement.top);put("width",o.placement.width);put("height",o.placement.height)
        return StringWriter().also {p.store(it,null)}.toString()
    }
    fun decode(text: String,default: PdfSigningOptions=PdfSigningOptions()): PdfSigningOptions {
        if(text.length>8192)return default
        return runCatching {
            val p=Properties().apply {load(StringReader(text))};val a=default.appearance
            fun value(k:String,d:String)=p.getProperty(k,d)
            fun flag(k:String,d:Boolean)=p.getProperty(k)?.toBooleanStrictOrNull()?:d
            fun number(k:String,d:Float)=p.getProperty(k)?.toFloatOrNull()?:d
            val position=runCatching {NormalizedStampPlacement(number("left",default.placement.left),number("top",default.placement.top),number("width",default.placement.width),number("height",default.placement.height))}.getOrDefault(default.placement)
            val style=StampStyle.entries.firstOrNull {it.name==value("style",a.style.name) && (p.getProperty("designVersion")=="2" || it!=StampStyle.INK && it!=StampStyle.HANDWRITTEN_ONLY)}?:StampStyle.INSTITUTIONAL
            default.copy(pageNumber=(p.getProperty("page")?.toIntOrNull()?:1).coerceIn(1,10000),visible=flag("visible",default.visible),placement=position,
                reason=value("reason",default.reason).take(512),location=value("location",default.location).take(512),reniecTimestamp=flag("tsa",default.reniecTimestamp),
                pageTarget=StampPageTarget.entries.firstOrNull {it.name==value("target","")}?:default.pageTarget,qrMetadata=null,
                appearance=SignatureAppearance(style=style,showHandwritten=flag("rubric",false) || style==StampStyle.INK,colorTheme=StampColorTheme.entries.firstOrNull {it.name==value("color","")}?:a.colorTheme,
                    badge=StampBadgeType.entries.firstOrNull {it.name==value("badge","") && it!=StampBadgeType.QR_VERIFICATION}?:StampBadgeType.SEAL_VECTOR,
                    backgroundMode=StampBackgroundMode.entries.firstOrNull {it.name==value("bgMode","")}?:a.backgroundMode,
                    dateFormat=StampDateFormat.entries.firstOrNull {it.name==value("dateFormat","")}?:a.dateFormat,
                    density=StampDensity.entries.firstOrNull {it.name==value("density","")}?:a.density,
                    showBorder=flag("border",a.showBorder),customHeader=value("header",a.customHeader).take(160),showTsaBadge=flag("tsaBadge",a.showTsaBadge),
                    showDni=flag("dni",a.showDni),showDate=flag("date",a.showDate),showTime=flag("time",a.showTime),showReason=flag("showReason",a.showReason),showLocation=flag("showLocation",a.showLocation)))
        }.getOrDefault(default)
    }
}

enum class StampTemplate(val label: String) {
    INSTITUTIONAL("Institucional"), EXECUTIVE("Ejecutivo transparente"), APPROVAL("Visto bueno compacto"), PRINT("Impresión B/N");
    fun apply(o: PdfSigningOptions): PdfSigningOptions {
        val a=when(this) {
            INSTITUTIONAL->SignatureAppearance()
            EXECUTIVE->SignatureAppearance(style=StampStyle.MINIMAL,colorTheme=StampColorTheme.ROYAL_INDIGO,badge=StampBadgeType.CHECK_SEAL,backgroundMode=StampBackgroundMode.TRANSPARENT)
            APPROVAL->SignatureAppearance(style=StampStyle.CARD,colorTheme=StampColorTheme.EMERALD_TRUST,badge=StampBadgeType.CHECK_SEAL,dateFormat=StampDateFormat.COMPACT_HUMAN,density=StampDensity.COMPACT,customHeader="",showTime=false,showReason=false,showLocation=false,showTsaBadge=false)
            PRINT->SignatureAppearance(style=StampStyle.CARD,colorTheme=StampColorTheme.SLATE_MONO,badge=StampBadgeType.NONE,backgroundMode=StampBackgroundMode.SOLID_WHITE)
        }
        return o.copy(visible=true,appearance=a)
    }
}
