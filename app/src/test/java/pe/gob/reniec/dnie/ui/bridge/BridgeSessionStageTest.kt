package pe.gob.reniec.dnie.ui.bridge

import org.junit.Assert.*
import org.junit.Test

class BridgeSessionStageTest {
    @Test fun waitingAndPreparationNeverClaimPcOrCardConnected() {
        for (stage in listOf(BridgeSessionStage.IDLE, BridgeSessionStage.PREPARING,
            BridgeSessionStage.CONNECTING_PC, BridgeSessionStage.STOPPING)) {
            assertFalse(stage.pcConnected)
            assertFalse(stage.cardReady)
        }
        assertTrue(BridgeSessionStage.WAITING_CARD.pcConnected)
        assertFalse(BridgeSessionStage.WAITING_CARD.cardReady)
        assertTrue(BridgeSessionStage.OPENING_CARD.pcConnected)
        assertFalse(BridgeSessionStage.OPENING_CARD.cardReady)
        assertTrue(BridgeSessionStage.ACTIVE.pcConnected)
        assertTrue(BridgeSessionStage.ACTIVE.cardReady)
    }

    @Test fun nonSessionBusyWorkDoesNotOpenSessionPresentation() {
        assertFalse(BridgeUiState(busy = true, message = "Importando emparejamiento…").sessionStage.visible)
    }
}
