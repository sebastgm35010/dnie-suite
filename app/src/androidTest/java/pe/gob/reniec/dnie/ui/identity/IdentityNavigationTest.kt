package pe.gob.reniec.dnie.ui.identity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.gob.reniec.dnie.MainActivity

@RunWith(AndroidJUnit4::class)
class IdentityNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun identityTabOpensAndRequestsCanBeforeReading() {
        rule.onNodeWithText("Identidad").performClick()
        rule.onNodeWithText("Identidad del DNI").assertIsDisplayed()
        rule.onNodeWithText("Leer DNI").performClick()
        rule.onNodeWithText("Leer identidad del DNI").assertIsDisplayed()
        rule.onNodeWithText("Ingrese el CAN de 6 dígitos impreso en su DNI.").assertIsDisplayed()
    }
}
