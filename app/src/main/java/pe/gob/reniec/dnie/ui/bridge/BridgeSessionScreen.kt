package pe.gob.reniec.dnie.ui.bridge

import androidx.compose.runtime.Composable
import pe.gob.reniec.dnie.ui.design.ConnectionSessionScreen

@Composable fun BridgeSessionScreen(state: BridgeUiState, onStop: () -> Unit) {
    val stage = state.sessionStage
    ConnectionSessionScreen("CONEXIÓN PC", state.method.title, when (stage) {
        BridgeSessionStage.PREPARING -> "Preparando conexión"
        BridgeSessionStage.WAITING_CARD -> "Acerca tu DNI"
        BridgeSessionStage.CONNECTING_PC -> "Conectando con la PC"
        BridgeSessionStage.OPENING_CARD -> "Abriendo tu DNI"
        BridgeSessionStage.ACTIVE -> "Puente activo"
        BridgeSessionStage.STOPPING -> "Deteniendo sesión"
        BridgeSessionStage.IDLE -> "Sesión finalizada"
    }, stage.pcConnected, stage != BridgeSessionStage.PREPARING && stage != BridgeSessionStage.STOPPING,
        stage.cardReady, if (state.windowsPace) "Tarjeta disponible para Windows" else "DNI listo para usar",
        stage != BridgeSessionStage.ACTIVE && stage != BridgeSessionStage.WAITING_CARD,
        stage == BridgeSessionStage.STOPPING, when (stage) {
            BridgeSessionStage.WAITING_CARD -> "PC conectada. Acerca tu DNI al sensor NFC."
            BridgeSessionStage.ACTIVE -> "Usa tu programa de la PC. Mantén el DNI inmóvil junto al teléfono."
            BridgeSessionStage.STOPPING -> "Cerrando la conexión con el DNI y la PC."
            else -> "Inicia el puente en la PC. No necesitas acercar el DNI para conectar ambos equipos."
        }, onStop)
}
