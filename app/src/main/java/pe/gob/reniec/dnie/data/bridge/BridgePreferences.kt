package pe.gob.reniec.dnie.data.bridge
import android.content.Context
import pe.gob.reniec.dnie.bridge.BridgeConnectionMethod
import pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings
/** Only public connection preferences; pairing credentials and CAN are never stored here. */
class BridgePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("bridge-settings", Context.MODE_PRIVATE)
    fun method(): BridgeConnectionMethod = runCatching { BridgeConnectionMethod.valueOf(prefs.getString("method", "TAILSCALE")!!) }.getOrDefault(BridgeConnectionMethod.TAILSCALE)
    fun settings(): BridgeRuntimeSettings = runCatching {
        BridgeRuntimeSettings(prefs.getInt("connect", 15_000), prefs.getInt("read", 120_000), prefs.getInt("nfc", 10_000))
    }.getOrDefault(BridgeRuntimeSettings())
    fun host() = prefs.getString("host", "") ?: ""
    fun save(method: BridgeConnectionMethod, host: String, settings: BridgeRuntimeSettings) {
        prefs.edit().putString("method", method.name).putString("host", host)
            .putInt("connect", settings.connectTimeoutMs).putInt("read", settings.readTimeoutMs).putInt("nfc", settings.nfcTimeoutMs).apply()
    }
}
