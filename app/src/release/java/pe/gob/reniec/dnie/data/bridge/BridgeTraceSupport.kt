package pe.gob.reniec.dnie.data.bridge
import android.content.Context
import android.net.Uri
import pe.gob.reniec.dnie.bridge.BridgeCard
class BridgeTraceSupport(context: Context) {
    fun hasTrace() = false
    fun decorate(card: BridgeCard, profile: String): BridgeCard = card
    fun openingFailure(failure: Throwable, profile: String) {}
    fun clear() {}
    fun export(uri: Uri) { error("Development trace not included in release") }
}
