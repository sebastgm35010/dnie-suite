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
