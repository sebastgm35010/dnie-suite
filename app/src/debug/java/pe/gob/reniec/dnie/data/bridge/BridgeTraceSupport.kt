package pe.gob.reniec.dnie.data.bridge
import android.content.Context
import android.net.Uri
import pe.gob.reniec.dnie.bridge.BridgeCard
class BridgeTraceSupport(private val context: Context) {
    private val file get() = java.io.File(context.filesDir, "bridge-apdu-trace.jsonl")
    private val preferences get() = context.getSharedPreferences("bridge-development", Context.MODE_PRIVATE)
    private val enabled get() = preferences.getBoolean("trace", false)
    fun hasTrace() = file.exists() && file.length() > 0
    fun decorate(card: BridgeCard, profile: String): BridgeCard = if (enabled) BridgeApduTrace(card, file, profile) else card
    fun openingFailure(failure: Throwable, profile: String) { if (enabled) BridgeApduTrace.openingFailure(file, failure, profile) }
    fun clear() { if (file.exists() && !file.delete()) error("Cannot clear trace") }
    fun export(uri: Uri) { context.contentResolver.openOutputStream(uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Cannot export trace") }
}
