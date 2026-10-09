package pe.gob.reniec.dnie.diagnostics

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.StrictMode
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Foreground heartbeat and structural stack samples, without user text or heap dumps. */
internal object RuntimeDiagnostics {
    private val worker = HandlerThread("DnieRuntimeMetrics").apply { start() }
    private val background = Handler(worker.looper)
    private val main = Handler(Looper.getMainLooper())
    @Volatile var foreground = false
    private val pending = AtomicBoolean(false)
    private val sent = AtomicLong()
    private var reported = false
    private fun frames(stack: Array<StackTraceElement>) = stack.take(24)
        .joinToString(" | ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
    fun start() {
        if (Build.VERSION.SDK_INT >= 28) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork()
                .penaltyListener({ task -> background.post(task) }) { violation ->
                    DiagnosticLog.event("performance", "main_thread_io", "layer" to "adapter",
                        "exception" to violation.javaClass.simpleName, "frames" to frames(violation.stackTrace))
                }.build())
        }
        background.post(object : Runnable {
            override fun run() {
                if (foreground) {
                    if (pending.get()) {
                        val delay = SystemClock.uptimeMillis() - sent.get()
                        if (delay >= 250 && !reported) {
                            reported = true
                            DiagnosticLog.event("performance", "main_thread_stall", "layer" to "ui",
                                "durationMs" to delay, "frames" to frames(Looper.getMainLooper().thread.stackTrace))
                        }
                    } else {
                        reported = false
                        sent.set(SystemClock.uptimeMillis()); pending.set(true)
                        main.post {
                            val delay = SystemClock.uptimeMillis() - sent.get()
                            if (delay >= 250) DiagnosticLog.event("performance", "main_thread_recovered", "layer" to "ui", "durationMs" to delay)
                            pending.set(false)
                        }
                    }
                }
                background.postDelayed(this, 500)
            }
        })
    }
}
