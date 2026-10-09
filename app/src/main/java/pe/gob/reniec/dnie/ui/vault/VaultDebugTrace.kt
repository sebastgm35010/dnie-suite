package pe.gob.reniec.dnie.ui.vault

import android.util.Log
import kotlinx.coroutines.CancellationException
import pe.gob.reniec.dnie.BuildConfig
import pe.gob.reniec.dnie.diagnostics.DiagnosticLog

/** Debug-only metrics. Never include names, URIs, credentials, certificates or keys. */
internal object VaultDebugTrace {
    const val TAG = "DNIE_PERF"
    fun metric(stage: String, value: Long) {
        DiagnosticLog.event("vault", "metric", "layer" to "adapter", "stage" to stage,
            (if (stage == "file.restore_name") "verified" else "durationMs") to value)
        if (BuildConfig.DEBUG) {
            val detail = if (stage == "file.restore_name") "supported=${value == 1L}" else "elapsedMs=$value"
            Log.i(TAG, "stage=$stage event=end $detail")
        }
    }
    suspend fun <T> measure(stage: String, action: suspend () -> T): T {
        if (!BuildConfig.DEBUG) return action()
        val started = System.nanoTime()
        val flow = java.util.UUID.randomUUID().toString()
        DiagnosticLog.event("vault", "operation_started", "layer" to "coordination", "flow" to flow, "stage" to stage)
        if (BuildConfig.DEBUG) Log.i(TAG, "stage=$stage event=start thread=${Thread.currentThread().name}")
        var outcome = "ok"
        try { return action() }
        catch (e: CancellationException) { outcome = "cancelled"; throw e }
        catch (e: Exception) { DiagnosticLog.failure("vault", stage, e); outcome = "error:${e.javaClass.simpleName}"; throw e }
        catch (e: LinkageError) { DiagnosticLog.failure("vault", stage, e); outcome = "linkage:${e.javaClass.simpleName}"; throw e }
        finally {
            DiagnosticLog.event("vault", "operation_finished", "layer" to "coordination", "flow" to flow, "stage" to stage, "state" to outcome, "durationMs" to (System.nanoTime() - started) / 1000000)
            if (BuildConfig.DEBUG) Log.i(TAG, "stage=$stage event=end outcome=$outcome elapsedMs=${(System.nanoTime() - started) / 1000000} thread=${Thread.currentThread().name}")
        }
    }
}
