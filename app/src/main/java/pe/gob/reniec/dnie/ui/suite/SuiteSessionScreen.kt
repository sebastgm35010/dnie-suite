package pe.gob.reniec.dnie.ui.suite

import androidx.compose.runtime.Composable
import pe.gob.reniec.dnie.signing.link.SuiteCardState
import pe.gob.reniec.dnie.ui.design.ConnectionSessionScreen

@Composable fun SuiteSessionScreen(state: RemoteSigningState, onStop: () -> Unit) {
    val cardPresent = state.cardState in setOf(SuiteCardState.DETECTED, SuiteCardState.PREPARING,
        SuiteCardState.READY, SuiteCardState.IN_USE)
    ConnectionSessionScreen("SUITE DE FIRMA", "PC · conexión privada", when {
        state.failureCode.isNotEmpty() -> "Conexión interrumpida"
        !state.connected && state.busy -> "Esperando la PC"
        !state.connected -> "Sesión finalizada"
        state.busy -> "Procesando solicitud"
        cardPresent -> "Suite conectada"
        else -> "Acerca tu DNI"
    }, state.connected, true, cardPresent, "DNI conectado",
        state.busy, false, when {
            !state.connected && state.busy -> "Inicia la conexión en la suite de Windows. El DNI puede acercarse después."
            !state.connected -> state.message
            state.cardState == SuiteCardState.PREPARING -> "Abriendo el DNI. Mantén la tarjeta inmóvil."
            state.busy -> state.message
            cardPresent -> "Envía documentos desde Windows. El PIN se solicita allí para cada lote."
            else -> "La PC ya está conectada. Acerca el DNI para firmar; puedes retirarlo entre lotes."
        }, onStop)
}
