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
        rule.onNodeWithText("Sin lectura").assertExists()
        rule.onNodeWithText("Tu DNI, en un solo lugar").assertExists()
        rule.onNodeWithText("Leer mi DNIe").performClick()
        rule.onNodeWithText("CAN (6 dígitos)").assertExists()
        rule.onNodeWithText("Cancelar").performClick()
        rule.onNodeWithText("Mi DNIe").assertExists()
    }
    @Test fun pcHubDistinguishesReaderFromOwnSuite() {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Conexión con PC"))
        rule.onNodeWithText("Conexión con PC").performClick()
        rule.onNodeWithText("Conexión PC").assertExists()
        rule.onNodeWithText("Lector para otras aplicaciones").assertExists()
        rule.onNodeWithText("Suite propia de firma").assertExists()
        rule.onNodeWithContentDescription("Volver").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Leer mi DNIe"))
        rule.onNodeWithText("Leer mi DNIe").assertExists()
    }
}
