package pe.gob.reniec.dnie.desktop.ui

import pe.gob.reniec.dnie.pdf.*
import pe.gob.reniec.dnie.desktop.model.DesktopPdfMode
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties
import java.util.prefs.Preferences
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class SuiteDesign(val options: PdfSigningOptions=PdfSigningOptions(placement=NormalizedStampPlacement(.36f,.80f,.60f,.16f)),
    val mode: DesktopPdfMode=DesktopPdfMode.VISIBLE,val previewName: String="")

/** Only public appearance settings; no PIN, CAN, keys, certificate or handwritten image. */
class SuiteDesignPreferences(private val prefs: Preferences) : AutoCloseable {
    private val writer=Executors.newSingleThreadScheduledExecutor { Thread(it,"suite-design-preferences") }
    private var pending: ScheduledFuture<*>?=null
    private var latest: SuiteDesign?=null
    private var closed=false
    fun load(): SuiteDesign=runCatching { decode(prefs.get(KEY,"")) }.getOrDefault(SuiteDesign())
    @Synchronized fun save(design: SuiteDesign) {
        if(closed)return
        latest=design;pending?.cancel(false);pending=writer.schedule({ write(design) },350,TimeUnit.MILLISECONDS)
    }
    private fun write(design: SuiteDesign) { runCatching { prefs.put(KEY,encode(design));prefs.flush() } }
    @Synchronized override fun close() { if(closed)return;closed=true;pending?.cancel(false);latest?.let { design -> writer.execute { write(design) } };writer.shutdown() }
    internal fun awaitClosed()=writer.awaitTermination(5,TimeUnit.SECONDS)
    companion object {
        private const val KEY="stamp.design.v1"
        fun encode(design: SuiteDesign): String {
            val p=Properties();val o=design.options;val a=o.appearance
            fun put(key: String,value: Any){p.setProperty(key,value.toString())}
            put("mode",design.mode.name);put("style",a.style.name);put("color",a.colorTheme.name);put("badge",a.badge.name)
            put("header",a.customHeader.take(160));put("reason",o.reason.take(512));put("location",o.location.take(512));put("name",design.previewName.take(160))
            put("tsa",o.reniecTimestamp);put("tsaBadge",a.showTsaBadge);put("dni",a.showDni);put("date",a.showDate);put("time",a.showTime);put("showReason",a.showReason);put("showLocation",a.showLocation)
            put("target",o.pageTarget.name);put("page",o.pageNumber);put("left",o.placement.left);put("top",o.placement.top);put("width",o.placement.width);put("height",o.placement.height)
            return StringWriter().also { p.store(it,null) }.toString()
        }
        fun decode(text: String): SuiteDesign {
            if(text.length>8192)return SuiteDesign()
            val p=Properties();runCatching { p.load(StringReader(text)) }.getOrElse { return SuiteDesign() }
            val d=SuiteDesign();val a=d.options.appearance
            fun value(key: String,default: String)=p.getProperty(key,default)
            fun flag(key: String,default: Boolean)=p.getProperty(key)?.toBooleanStrictOrNull()?:default
            fun number(key: String,default: Float)=p.getProperty(key)?.toFloatOrNull()?:default
            val placement=runCatching { NormalizedStampPlacement(number("left",d.options.placement.left),number("top",d.options.placement.top),number("width",d.options.placement.width),number("height",d.options.placement.height)) }.getOrDefault(d.options.placement)
            val style=StampStyle.entries.firstOrNull { it.name==value("style",a.style.name) && !it.requiresImage() }?:a.style
            val mode=DesktopPdfMode.entries.firstOrNull { it.name==value("mode",d.mode.name) }?:d.mode
            return SuiteDesign(PdfSigningOptions(pageNumber=(p.getProperty("page")?.toIntOrNull()?:1).coerceIn(1,10000),placement=placement,visible=mode==DesktopPdfMode.VISIBLE,
                reason=value("reason",d.options.reason).take(512),location=value("location","").take(512),reniecTimestamp=flag("tsa",false),
                pageTarget=StampPageTarget.entries.firstOrNull { it.name==value("target","") }?:StampPageTarget.SPECIFIC_PAGE,
                appearance=SignatureAppearance(style=style,colorTheme=StampColorTheme.entries.firstOrNull { it.name==value("color","") }?:a.colorTheme,
                    badge=StampBadgeType.entries.firstOrNull { it.name==value("badge","") }?:a.badge,customHeader=value("header",a.customHeader).take(160),
                    showTsaBadge=flag("tsaBadge",true),showDni=flag("dni",true),showDate=flag("date",true),showTime=flag("time",true),showReason=flag("showReason",true),showLocation=flag("showLocation",true))),mode,value("name","").take(160))
        }
        private fun StampStyle.requiresImage()=this==StampStyle.INK || this==StampStyle.HANDWRITTEN_ONLY
    }
}
