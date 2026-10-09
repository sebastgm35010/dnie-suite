package pe.gob.reniec.dnie.diagnostics

import android.app.Activity
import android.app.Application
import android.os.Bundle
import pe.gob.reniec.dnie.BuildConfig
import pe.gob.reniec.dnie.data.diagnostics.DiagnosticStore

object AppDiagnostics {
    private val ready = kotlinx.coroutines.CompletableDeferred<DiagnosticStore>()
    lateinit var store: DiagnosticStore
        private set
    suspend fun awaitStore(): DiagnosticStore = ready.await()
    fun initialize(application: Application) {
        store = DiagnosticStore(application)
        DiagnosticLog.install(store)
        ready.complete(store)
        DiagnosticLog.event("app", "process_started", "version" to BuildConfig.VERSION_NAME, "sdk" to android.os.Build.VERSION.SDK_INT)
        RuntimeDiagnostics.start()
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            private fun record(activity: Activity, stage: String) = DiagnosticLog.event("lifecycle", stage, "activity" to activity.javaClass.simpleName)
            override fun onActivityCreated(a: Activity, b: Bundle?) { record(a, "created") }
            override fun onActivityStarted(a: Activity) { started++; RuntimeDiagnostics.foreground = started > 0; record(a, "started") }
            override fun onActivityResumed(a: Activity) { record(a, "resumed") }
            override fun onActivityPaused(a: Activity) { record(a, "paused") }
            override fun onActivityStopped(a: Activity) { started--; RuntimeDiagnostics.foreground = started > 0; record(a, "stopped") }
            override fun onActivityDestroyed(a: Activity) { record(a, "destroyed") }
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        })
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            try { DiagnosticLog.failure("crash", "uncaught", failure); store.flush(1500) }
            finally {
                if (previous != null) previous.uncaughtException(thread, failure)
                else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(1) }
            }
        }
    }
}
