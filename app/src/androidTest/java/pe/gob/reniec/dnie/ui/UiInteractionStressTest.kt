package pe.gob.reniec.dnie.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import pe.gob.reniec.dnie.MainActivity

/** Navigation/scrolling/typing only: never confirms credentials or sends an NFC command. */
class UiInteractionStressTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun repeatedTabsAndScrollingRemainResponsive() {
        repeat(4) {
            for (tab in listOf("Firmar PDF", "Lotes", "Identidad", "Certificados")) {
                if (tab == "Lotes") rule.onNodeWithText(tab).performClick()
                else {
                    if (tab == "Identidad") rule.onNodeWithTag("nav_certificates").performClick()
                    rule.onNodeWithTag(if (tab == "Firmar PDF") "nav_signer" else if (tab == "Identidad") "nav_identity" else "nav_certificates").performClick()
                }
                rule.waitForIdle()
                val scrollers = rule.onAllNodes(hasScrollAction())
                if (scrollers.fetchSemanticsNodes().isNotEmpty()) {
                    scrollers.onFirst().performTouchInput { swipeUp(); swipeDown() }
                }
            }
        }
        rule.onNodeWithTag("nav_certificates").assertIsSelected()
    }
    @Test fun typingAndCancellingCredentialsNeverStartsNfc() {
        rule.onNodeWithTag("nav_certificates").performClick()
        rule.onNodeWithContentDescription("Leer DNIe con NFC").performClick()
        if (rule.onAllNodesWithText("Cambiar").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("Cambiar").performClick()
        repeat(8) {
            rule.onNodeWithText("CAN (6 dígitos)").performTextReplacement("123456")
            rule.onNodeWithText("CAN (6 dígitos)").performTextReplacement("")
        }
        rule.onNodeWithText("Cancelar").performClick()
        rule.onNodeWithText("CAN (6 dígitos)").assertDoesNotExist()
        rule.onNodeWithTag("nav_certificates").assertIsSelected()
    }
}
