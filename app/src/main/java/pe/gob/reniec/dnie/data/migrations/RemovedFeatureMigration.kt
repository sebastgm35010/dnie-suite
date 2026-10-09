package pe.gob.reniec.dnie.data.migrations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.KeyStore

/** Runs after an upgrade and retries on startup if the OS interrupted the cleanup. */
object RemovedFeatureMigration {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun schedule(context: Context, completed: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch {
            try { cleanup(app) }
            catch (_: Exception) {
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("migration", "retired_credentials_cleanup",
                    "state" to "retry_on_startup")
            } finally { completed() }
        }
    }
    @Synchronized fun cleanup(context: Context): Boolean {
        val preferences = context.getSharedPreferences("feature_removals", Context.MODE_PRIVATE)
        if (preferences.getBoolean("credentials_retired_71", false) &&
            !RetiredCredentialData.hasFiles(context.filesDir, context.noBackupFilesDir)) return true
        val success = try {
            val keys by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }
            RetiredCredentialData.remove(context.filesDir, context.noBackupFilesDir) { alias ->
                keys.deleteEntry(alias)
                check(!keys.containsAlias(alias))
            }
        } catch (_: Exception) { false }
        if (success) preferences.edit().putBoolean("credentials_retired_71", true).commit()
        pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("migration", "retired_credentials_cleanup",
            "state" to if (success) "completed" else "retry_on_startup")
        return success
    }
}

class PackageUpgradeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        RemovedFeatureMigration.schedule(context) { pending.finish() }
    }
}
