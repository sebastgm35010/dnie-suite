package pe.gob.reniec.dnie.ui.bridge
import pe.gob.reniec.dnie.bridge.BridgeMode
data class BridgeUiState(
    val localWifiAddress: String = "",
    val peer: String = "", val fingerprint: String = "", val can: String = "",
    val armed: Boolean = false, val busy: Boolean = false,
    val sessionStage: BridgeSessionStage = BridgeSessionStage.IDLE,
    val traceAvailable: Boolean = false, val mode: BridgeMode = BridgeMode.TRANSLATED_ANDROID_PACE,
    val method: pe.gob.reniec.dnie.bridge.BridgeConnectionMethod = pe.gob.reniec.dnie.bridge.BridgeConnectionMethod.TAILSCALE,
    val settings: pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings = pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings(),
    val tailscale: Boolean = false,
    val tailnetHost: String = pe.gob.reniec.dnie.development.defaultTailnetHost(),
    val message: String = "Escanea el QR mostrado en la PC o importa el emparejamiento."
) {
    val windowsPace get() = mode.windowsPace
    val translatePace get() = mode.translatePace
}
