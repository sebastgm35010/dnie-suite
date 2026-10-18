package pe.gob.reniec.dnie.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import pe.gob.reniec.dnie.signing.link.SuiteCardState
import pe.gob.reniec.dnie.ui.suite.*
import pe.gob.reniec.dnie.ui.theme.DnieTheme

class SuiteSessionPresentationTest {
    @get:Rule val rule = createComposeRule()
    @Test fun connectingFromSetupReplacesFormWithDedicatedSessionUntilStop() {
        val state = mutableStateOf(RemoteSigningState(pc = "192.168.1.5", can = "123456", continuous = true, remembered = true))
        rule.setContent { DnieTheme(darkTheme = false) {
            RemoteSigningScreen(state.value, true, "192.168.1.11", {}, {}, {}, {},
                onConnect = { state.value = state.value.copy(sessionVisible = true, busy = true) },
                onForget = {}, onStop = { state.value = state.value.copy(sessionVisible = false, busy = false) }, onBack = {})
        } }
        rule.onNodeWithText("Conectar PC guardada").performClick()
        rule.onNodeWithTag("suite_session").assertExists()
        rule.onNodeWithText("CAN del DNI").assertDoesNotExist()
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
        rule.onNodeWithText("Esperando la PC").assertExists()
        rule.runOnIdle { state.value = state.value.copy(busy = false, connected = true, pc = "192.168.20.8") }
        rule.onNodeWithText("PC 192.168.20.8 · conexión privada").assertExists()
        rule.onNodeWithText("Detener sesión").performClick()
        rule.onNodeWithTag("suite_session").assertDoesNotExist()
        rule.onNodeWithText("••••56", substring = true).assertExists()
    }
    @Test fun suiteShowsPcBeforeDniAndRetainsOneStopActionOnFailure() {
        val state = mutableStateOf(RemoteSigningState(sessionVisible = true, busy = true))
        var stops = 0
        rule.setContent { DnieTheme(darkTheme = false) { SuiteSessionScreen(state.value) { stops++ } } }
        rule.onNodeWithText("Esperando la PC").assertExists()
        rule.onAllNodesWithContentDescription("Completado").assertCountEquals(0)
        rule.runOnIdle { state.value = state.value.copy(busy = false, connected = true) }
        rule.onAllNodesWithContentDescription("Completado").assertCountEquals(1)
        rule.runOnIdle { state.value = state.value.copy(cardState = SuiteCardState.DETECTED) }
        rule.onAllNodesWithContentDescription("Completado").assertCountEquals(2)
        rule.runOnIdle { state.value = state.value.copy(connected = false, cardState = SuiteCardState.ABSENT, failureCode = "SocketException") }
        rule.onNodeWithText("Conexión interrumpida").assertExists()
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
        rule.onNodeWithText("Detener sesión").performClick()
        rule.runOnIdle { assertEquals(1, stops) }
    }
}
