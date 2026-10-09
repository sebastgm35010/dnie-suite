package pe.gob.reniec.dnie.data.diagnostics

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pe.gob.reniec.dnie.diagnostics.AppDiagnostics

/** Read-only streaming endpoint for authorized ADB shell. Absent from the release source set. */
class DiagnosticExportProvider : ContentProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/x-ndjson"
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r" && uri.path == "/events" && uri.query == null) { "Read-only events endpoint" }
        val pipe = ParcelFileDescriptor.createReliablePipe()
        scope.launch {
            val output = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
            try {
                val until = kotlinx.coroutines.withTimeout(15000) { AppDiagnostics.awaitStore().export(output) }
                output.write((org.json.JSONObject().put("type", "export_complete").put("schema", 2)
                    .put("until", until).toString() + "\n").toByteArray(Charsets.UTF_8))
                output.close()
            }
            catch (failure: Exception) {
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.failure("diagnostics", "direct_export", failure)
                try { pipe[1].closeWithError("Diagnostic export failed") } catch (_: Exception) {}
            }
        }
        return pipe[0]
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read only")
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
}
