package pe.gob.reniec.dnie.desktop.application

import java.nio.file.Files
import pe.gob.reniec.dnie.desktop.model.DesktopSigningResult

/** Local job manifest, separate from console diagnostics; never contains credentials. */
object DesktopResultWriter {
    private fun quote(value: String?): String {
        if (value == null) return "null"
        return buildString {
            append('"')
            value.forEach { c -> when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                else -> if (c.code < 32) append("\\u%04x".format(c.code)) else append(c)
            } }
            append('"')
        }
    }
    fun write(result: DesktopSigningResult) {
        val documents = result.documents.joinToString(",\n") {
            "{\"id\":${quote(it.id)},\"name\":${quote(it.name)},\"status\":${quote(it.status.name)}," +
                "\"original\":${quote(result.directory.relativize(it.original).toString())}," +
                "\"signature\":${quote(it.signature?.let { file -> result.directory.relativize(file).toString() })},\"error\":${quote(it.error)}}"
        }
        val json = "{\"schemaVersion\":1,\"jobId\":${quote(result.jobId)},\"backend\":${quote(result.backend.name)}," +
            "\"complete\":${result.complete},\"sessionError\":${quote(result.sessionError)},\"documents\":[$documents]}\n"
        val temp = result.directory.resolve("result.json.part")
        try {
            Files.writeString(temp, json)
            Files.move(temp, result.directory.resolve("result.json"))
        } finally { Files.deleteIfExists(temp) }
    }
}
