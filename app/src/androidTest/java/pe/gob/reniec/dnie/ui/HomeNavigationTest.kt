package pe.gob.reniec.dnie.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import pe.gob.reniec.dnie.MainActivity

/** Exercises the new entry points without authorizing NFC or modifying user files. */
class HomeNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun homeDoesNotPretendToHaveAnActiveCardAndReadUsesExistingCredentials() {
        rule.onNodeWithText("Tu DNIe en tu teléfono").assertExists()
        rule.onNodeWithText("Leer mi DNIe").performClick()
        rule.onNodeWithText("Continuar a lectura NFC").assertExists()
        rule.onNodeWithText("Cancelar").performClick()
        rule.onNodeWithTag("nav_certificates").assertIsSelected()
    }
    @Test fun pcHubDistinguishesReaderFromOwnSuite() {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Conectar con PC"))
        rule.onNodeWithText("Conectar con PC").performClick()
        rule.onNodeWithText("Conexión PC").assertExists()
        rule.onNodeWithText("Lector para otras aplicaciones").assertExists()
        rule.onNodeWithText("Suite propia de firma").assertExists()
        rule.onNodeWithContentDescription("Volver").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Leer mi DNIe"))
        rule.onNodeWithText("Leer mi DNIe").assertExists()
    }
}
