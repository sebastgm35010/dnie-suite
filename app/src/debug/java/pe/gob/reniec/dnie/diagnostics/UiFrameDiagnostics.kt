package pe.gob.reniec.dnie.diagnostics

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window

/** Debug-only frame measurements on a dedicated thread; no user content or credentials. */
class UiFrameDiagnostics(private val window: Window) : AutoCloseable {
    private val worker = HandlerThread("DnieFrameMetrics").apply { start() }
    @Volatile private var route = "startup"
    private var lastVsync = Long.MIN_VALUE
    private var samples = 0
    private var slow = 0
    private var maximum = 0L
    private var summaryAt = System.nanoTime()
    private val budgetMs = 1000.0 / (window.windowManager.defaultDisplay.refreshRate.takeIf { it > 0 } ?: 60f)
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        samples++
        maximum = maxOf(maximum, total)
        if (total > budgetMs * 1_000_000) slow++
        if (System.nanoTime() - summaryAt >= 5_000_000_000L) {
            DiagnosticLog.event("performance", "frame_window", "layer" to "ui", "state" to route,
                "samples" to samples, "slow" to slow, "maxMs" to maximum / 1_000_000.0, "budgetMs" to budgetMs)
            samples = 0; slow = 0; maximum = 0; summaryAt = System.nanoTime()
        }
        if (total >= maxOf(32_000_000.0, budgetMs * 2_000_000)) {
            val vsync = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
            if (vsync > 0 && vsync == lastVsync) return@OnFrameMetricsAvailableListener
            lastVsync = vsync
            val layout = metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) / 1_000_000
            val draw = metrics.getMetric(FrameMetrics.DRAW_DURATION) / 1_000_000
            val sync = metrics.getMetric(FrameMetrics.SYNC_DURATION) / 1_000_000
            DiagnosticLog.event("performance", "slow_frame", "layer" to "ui", "state" to route, "durationMs" to total / 1_000_000.0, "dropped" to dropped,
                "layoutMs" to layout, "drawMs" to draw, "syncMs" to sync,
                "unknownMs" to metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION) / 1_000_000,
                "inputMs" to metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) / 1_000_000,
                "firstDraw" to metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME), "vsync" to vsync)
            Log.i("DNIE_PERF", "stage=ui.frame route=$route totalMs=${total / 1_000_000} layoutMs=$layout drawMs=$draw syncMs=$sync dropped=$dropped")
        }
    }
    init { window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper)) }
    fun onRoute(value: String?) {
        route = value?.takeIf { it in setOf("identity", "certificates", "signer", "batchSigner", "console", "vault") } ?: "startup"
    }
    override fun close() {
        window.removeOnFrameMetricsAvailableListener(listener)
        worker.quitSafely()
    }
}
