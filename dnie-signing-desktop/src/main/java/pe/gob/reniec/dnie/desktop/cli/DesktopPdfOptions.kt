package pe.gob.reniec.dnie.desktop.cli

import pe.gob.reniec.dnie.desktop.model.DesktopPdfMode
import pe.gob.reniec.dnie.pdf.*

/** UI-neutral command options; the same appearance contract will be consumed by the future viewer. */
class DesktopPdfOptions {
    private val values=linkedMapOf<String,String>()
    fun accept(flag: String, value: String) {
        require(flag in FLAGS && !values.containsKey(flag)) { "Opcion PDF invalida o duplicada" }
        values[flag]=value
    }
    val mode get() = values["--pdf-mode"]?.let(CliOptions::parsePdfMode) ?: DesktopPdfMode.INVISIBLE
    fun build(): PdfSigningOptions {
        val page=values["--pdf-page"]?.toInt() ?: 1; require(page>0)
        val box=values["--pdf-box"]?.split(',')?.map { it.toFloat() }
        require(box==null || box.size==4)
        val fields=values["--pdf-fields"]?.let { if(it.isBlank()) emptyList() else it.split(',') }
            ?: listOf("dni","date","time","reason","location")
        require(fields.distinct().size==fields.size && fields.all { it in listOf("dni","date","time","reason","location") })
        val style=when(values["--pdf-style"] ?: "card") { "card" -> StampStyle.CARD; "minimal" -> StampStyle.MINIMAL; else -> error("Estilo PDF invalido") }
        val placement=if(box==null) NormalizedStampPlacement(0.36f,0.82f,0.60f,0.14f)
            else NormalizedStampPlacement(box[0],box[1],box[2],box[3])
        return PdfSigningOptions(pageNumber=page,visible=mode==DesktopPdfMode.VISIBLE,placement=placement,
            appearance=SignatureAppearance(style=style,showDni="dni" in fields,showDate="date" in fields,
                showTime="time" in fields,showReason="reason" in fields,showLocation="location" in fields),
            reason=values["--pdf-reason"] ?: "",location=values["--pdf-location"] ?: "")
    }
    companion object { val FLAGS=setOf("--pdf-mode","--pdf-page","--pdf-box","--pdf-fields","--pdf-reason","--pdf-location","--pdf-style") }
}
