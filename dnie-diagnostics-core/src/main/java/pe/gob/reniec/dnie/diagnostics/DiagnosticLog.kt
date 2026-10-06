package pe.gob.reniec.dnie.diagnostics

data class DiagnosticEvent(val time: Long, val category: String, val name: String, val fields: Map<String, String>)
fun interface DiagnosticSink { fun append(event: DiagnosticEvent) }

/** Structured diagnostics only. Arbitrary messages, requests, secrets and document data are never accepted. */
object DiagnosticLog {
    @Volatile private var sink: DiagnosticSink? = null
    private val lineControls = Regex("[\\r\\n\\u0000]")
    private val allowed = setOf("stage", "state", "code", "rule", "exception", "frames", "flow", "durationMs", "status",
        "header", "activity", "version", "sdk", "mode", "package", "rp", "algorithm", "attachment", "residentKey",
        "verification", "attestation", "challengeBytes", "userIdBytes", "nameBytes", "displayNameBytes", "options",
        "credentials", "extensions", "unknownExtensions", "count", "busy", "awaiting", "error", "verified", "dropped",
        "layoutMs", "drawMs", "syncMs", "unknownMs", "inputMs", "firstDraw", "vsync")
    fun install(target: DiagnosticSink?) { sink = target }
    fun event(category: String, name: String, vararg fields: Pair<String, Any?>) {
        val target = sink ?: return
        val safe = fields.filter { it.first in allowed }.associate { (key, value) -> key to value.toString().take(2048).replace(lineControls, " ") }
        try { target.append(DiagnosticEvent(System.currentTimeMillis(), category.take(48), name.take(80), safe)) } catch (_: Exception) {}
    }
    fun failure(category: String, stage: String, failure: Throwable, flow: String = "") {
        val chain = generateSequence(failure) { it.cause }.take(4).toList()
        event(category, "failure", "stage" to stage, "flow" to flow,
            "exception" to chain.joinToString(" > ") { it.javaClass.simpleName },
            "frames" to chain.flatMap { cause -> cause.stackTrace.filter { it.className.startsWith("pe.gob.reniec.dnie.") }.take(6) }
                .joinToString(" | ") { "${it.className}.${it.methodName}:${it.lineNumber}" })
    }
}
