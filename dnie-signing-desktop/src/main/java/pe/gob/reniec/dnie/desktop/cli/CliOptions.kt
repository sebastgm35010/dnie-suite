package pe.gob.reniec.dnie.desktop.cli

import java.nio.file.Path
import pe.gob.reniec.dnie.desktop.model.DesktopSigningRequest

object CliOptions {
    fun parsePdfMode(value: String) = when (value) {
        "invisible" -> pe.gob.reniec.dnie.desktop.model.DesktopPdfMode.INVISIBLE
        "detached" -> pe.gob.reniec.dnie.desktop.model.DesktopPdfMode.DETACHED
        else -> error("Modo PDF no admitido")
    }
    fun parse(args: Array<String>): DesktopSigningRequest {
        require(args.firstOrNull() == "demo") { "Backend no disponible; este hito solo admite demo" }
        var workspace: Path? = null
        var pdfMode: pe.gob.reniec.dnie.desktop.model.DesktopPdfMode? = null
        val inputs = mutableListOf<Path>()
        var index = 1
        while (index < args.size) {
            val flag = args[index++]
            require(index < args.size) { "Falta valor de la opción" }
            val value = args[index++]
            when (flag) {
                "--workspace" -> { require(workspace == null) { "Workspace duplicado" }; workspace = Path.of(value) }
                "--pdf-mode" -> { require(pdfMode == null); pdfMode = parsePdfMode(value) }
                "--input" -> inputs.add(Path.of(value))
                else -> error("Opción no admitida")
            }
        }
        require(inputs.isNotEmpty()) { "Falta --input" }
        return DesktopSigningRequest(inputs, requireNotNull(workspace) { "Falta --workspace" }, pdfMode ?: pe.gob.reniec.dnie.desktop.model.DesktopPdfMode.INVISIBLE)
    }
}
