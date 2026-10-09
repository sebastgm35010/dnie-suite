package pe.gob.reniec.dnie.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pe.gob.reniec.dnie.bridge.BridgeConnectionMethod
import pe.gob.reniec.dnie.ui.bridge.*
import pe.gob.reniec.dnie.ui.theme.DnieTheme

/** Fixture only: never arms a transport or opens a physical card. */
class BridgeSessionPresentationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyTransportHasOneActionAndTruthfulProgress() {
        val state = mutableStateOf(BridgeUiState(sessionStage = BridgeSessionStage.WAITING_CARD))
        var stops = 0
        rule.setContent { DnieTheme(darkTheme = false) { BridgeSessionScreen(state.value) { stops++ } } }
        for (method in BridgeConnectionMethod.entries) {
            rule.runOnIdle { state.value = state.value.copy(method = method, sessionStage = BridgeSessionStage.WAITING_CARD) }
            rule.onNodeWithText(method.title).assertExists()
            rule.onAllNodes(hasClickAction()).assertCountEquals(1)
            rule.onAllNodesWithContentDescription("Completado").assertCountEquals(0)
            rule.onNodeWithText("Acerca tu DNI").assertExists()
            rule.runOnIdle { state.value = state.value.copy(sessionStage = BridgeSessionStage.OPENING_CARD) }
            rule.onAllNodesWithContentDescription("Completado").assertCountEquals(1)
            rule.runOnIdle { state.value = state.value.copy(sessionStage = BridgeSessionStage.ACTIVE) }
            rule.onAllNodesWithContentDescription("Completado").assertCountEquals(2)
            rule.onNodeWithText("Puente activo").assertExists()
            if (method == BridgeConnectionMethod.LOCAL_NETWORK) {
                // Isolated UI fixture only; production FLAG_SECURE is never relaxed.
                val instrument = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                val bitmap = instrument.uiAutomation.takeScreenshot()
                requireNotNull(bitmap)
                java.io.File(instrument.targetContext.cacheDir, "bridge-session-fixture.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                pe.gob.reniec.dnie.data.feedback.ReadingFeedback.start(instrument.targetContext)
            }
            rule.onNodeWithTag("bridge_stop").performClick()
        }
        rule.runOnIdle { assertEquals(3, stops); state.value = state.value.copy(sessionStage = BridgeSessionStage.STOPPING) }
        rule.onNodeWithTag("bridge_stop").assertIsNotEnabled()
        rule.onNodeWithText("Deteniendo sesión").assertExists()
    }
}
