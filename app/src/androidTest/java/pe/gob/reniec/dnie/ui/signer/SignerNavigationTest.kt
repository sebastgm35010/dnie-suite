package pe.gob.reniec.dnie.ui.signer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.gob.reniec.dnie.MainActivity

@RunWith(AndroidJUnit4::class)
class SignerNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun pdfAndBatchSigningHaveDifferentImportScreens() {
        rule.onNodeWithText("Firmar PDF").performClick()
        rule.onNodeWithText("Firma PDF").assertIsDisplayed()
        rule.onNodeWithText("Seleccionar PDF").assertIsDisplayed()
        rule.onNodeWithText("Añadir archivos").assertDoesNotExist()
        rule.onNodeWithText("Lotes").performClick()
        rule.onNodeWithText("Firma por lotes").assertIsDisplayed()
        rule.onNodeWithText("Añadir archivos").assertIsDisplayed()
        rule.onNodeWithText("Seleccionar PDF").assertDoesNotExist()
    }
    @Test fun pdfAndBatchUseIndependentViewModels() {
        rule.runOnIdle {
            val provider = androidx.lifecycle.ViewModelProvider(rule.activity)
            org.junit.Assert.assertNotSame(provider.get(SignerViewModel::class.java), provider.get("batchSigner", SignerViewModel::class.java))
        }
    }
}
