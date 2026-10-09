package pe.gob.reniec.dnie.diagnostics

data class DiagnosticEvent(val time: Long, val category: String, val name: String, val fields: Map<String, String>,
    val monotonicNs: Long = System.nanoTime(), val thread: String = Thread.currentThread().name,
    val process: String = DiagnosticLog.process)
fun interface DiagnosticSink { fun append(event: DiagnosticEvent) }

/** Structured diagnostics only. Arbitrary messages, requests, secrets and document data are never accepted. */
object DiagnosticLog {
    val process: String = java.util.UUID.randomUUID().toString()
    @Volatile private var sink: DiagnosticSink? = null
    private val lineControls = Regex("[\\r\\n\\u0000]")
    private val allowed = setOf("stage", "state", "code", "rule", "exception", "frames", "flow", "durationMs", "status",
        "header", "activity", "version", "sdk", "mode", "package", "rp", "algorithm", "attachment", "residentKey",
        "verification", "attestation", "challengeBytes", "userIdBytes", "nameBytes", "displayNameBytes", "options",
        "credentials", "extensions", "unknownExtensions", "count", "busy", "awaiting", "error", "verified", "dropped",
        "layoutMs", "drawMs", "syncMs", "unknownMs", "inputMs", "firstDraw", "vsync",
        "layer", "operation", "samples", "slow", "maxMs", "budgetMs", "pending", "bytes")
    fun install(target: DiagnosticSink?) { sink = target }
    fun event(category: String, name: String, vararg fields: Pair<String, Any?>) {
        val target = sink ?: return
        try {
            val safe = fields.filter { it.first in allowed }.associate { (key, value) -> key to value.toString().take(if (key == "frames") 8192 else 2048).replace(lineControls, " ") }
            target.append(DiagnosticEvent(System.currentTimeMillis(), category.take(48), name.take(80), safe))
        } catch (_: Exception) {}
    }
    fun failure(category: String, stage: String, failure: Throwable, flow: String = "") {
        val chain = generateSequence(failure) { it.cause }.take(4).toList()
        event(category, "failure", "stage" to stage, "flow" to flow,
            "exception" to chain.joinToString(" > ") { it.javaClass.simpleName },
            "frames" to chain.flatMap { cause -> cause.stackTrace.take(16) }
                .joinToString(" | ") { "${it.className}.${it.methodName}:${it.lineNumber}" })
    }

    /** Operation names/layers are static identifiers. No arguments, values or exception messages. */
    fun <T> measure(category: String, operation: String, layer: String, block: () -> T): T {
        if (sink == null) return block()
        val id = java.util.UUID.randomUUID().toString()
        val start = System.nanoTime()
        event(category, "operation_started", "operation" to operation, "layer" to layer, "flow" to id)
        var state = "failed"
        try { return block().also { state = "completed" } }
        finally { event(category, "operation_finished", "operation" to operation, "layer" to layer,
            "flow" to id, "state" to state, "durationMs" to (System.nanoTime() - start) / 1_000_000.0) }
    }
    suspend fun <T> measureSuspend(category: String, operation: String, layer: String, block: suspend () -> T): T {
        if (sink == null) return block()
        val id = java.util.UUID.randomUUID().toString()
        val start = System.nanoTime()
        event(category, "operation_started", "operation" to operation, "layer" to layer, "flow" to id)
        var state = "failed"
        try { return block().also { state = "completed" } }
        finally { event(category, "operation_finished", "operation" to operation, "layer" to layer,
            "flow" to id, "state" to state, "durationMs" to (System.nanoTime() - start) / 1_000_000.0) }
    }
}
