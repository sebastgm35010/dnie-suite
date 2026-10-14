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
            val p=Properties().apply {load(StringReader(StampDesignCodec.encode(design.options)))}
            p.setProperty("mode",design.mode.name);p.setProperty("name",design.previewName.take(160))
            return StringWriter().also {p.store(it,null)}.toString()
        }
        fun decode(text: String): SuiteDesign {
            if(text.length>8192)return SuiteDesign()
            val p=Properties();runCatching {p.load(StringReader(text))}.getOrElse {return SuiteDesign()}
            val d=SuiteDesign();val mode=DesktopPdfMode.entries.firstOrNull {it.name==p.getProperty("mode")}?:d.mode
            return SuiteDesign(StampDesignCodec.decode(text,d.options).copy(visible=mode==DesktopPdfMode.VISIBLE),mode,p.getProperty("name","").take(160))
        }
    }
}
