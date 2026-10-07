package pe.gob.reniec.dnie.desktop.orchestration

enum class BridgeAvailability { OPTIONAL_MODULE_ABSENT, NOT_INSTALLED, SERVICE_STOPPED, AVAILABLE, UNAVAILABLE }
enum class BridgeHostState { STOPPED, STARTING, RUNNING, STOPPING, FAILED, UNKNOWN }
data class BridgeSnapshot(val availability: BridgeAvailability, val host: BridgeHostState = BridgeHostState.UNKNOWN,
    val cardPresent: Boolean = false) {
    val permitsPrivateSuite: Boolean get() = availability in setOf(BridgeAvailability.OPTIONAL_MODULE_ABSENT,
        BridgeAvailability.NOT_INSTALLED, BridgeAvailability.SERVICE_STOPPED) ||
        availability == BridgeAvailability.AVAILABLE && host == BridgeHostState.STOPPED
}
interface BridgeGateway {
    fun status(): BridgeSnapshot
    fun openManager()
    fun stop(): BridgeSnapshot
}
class ProductModeConflict(message: String) : IllegalStateException(message)

/** Owns local mode selection only. Never installs a driver, stops a service or owns a DNI session. */
class DesktopProductCoordinator(private val bridge: BridgeGateway) {
    private var permit: Any? = null
    fun status(): BridgeSnapshot = bridge.status()
    @Synchronized fun acquirePrivateSuite(): AutoCloseable {
        if (permit != null) throw ProductModeConflict("La suite ya está conectando o conectada.")
        if (!bridge.status().permitsPrivateSuite) throw ProductModeConflict(
            "Detén el puente compatible antes de conectar la suite. Si no se puede consultar su estado, abre el gestor.")
        val token=Any();permit=token
        return AutoCloseable { synchronized(this) { if (permit === token) permit=null } }
    }
    @Synchronized fun openBridgeManager() {
        requireBridgeMode();bridge.openManager()
    }
    @Synchronized fun stopBridge(): BridgeSnapshot {
        requireBridgeMode();return bridge.stop()
    }
    private fun requireBridgeMode() {
        if (permit != null) throw ProductModeConflict("Desconecta la suite antes de operar el puente compatible.")
    }
}
