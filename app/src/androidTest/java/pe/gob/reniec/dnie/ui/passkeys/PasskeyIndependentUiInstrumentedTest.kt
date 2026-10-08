package pe.gob.reniec.dnie.ui.passkeys

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import pe.gob.reniec.dnie.passkeys.android.PasskeyAccount

/** Fixture presentation only: never reads user credentials or invokes biometric/NFC operations. */
class PasskeyIndependentUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun independentAuthorizationHidesDniAndDeletionRequestsFreshAuthorization() {
        var authorized = 0
        var deleted: String? = null
        compose.setContent {
            PasskeyCeremonyScreen(PasskeyUiState(ready = true, independent = true,
                biometricAvailable = true, managing = true,
                accounts = listOf(PasskeyAccount("fixture", "example.com", "Test"))),
                onCan = {}, onPin = {}, onAuthorize = {}, onDelete = { deleted = it },
                onCancel = {}, nfcEnabled = false, onBiometric = { authorized++ })
        }
        compose.onNodeWithText("CAN").assertDoesNotExist()
        compose.onNodeWithText("PIN del DNI").assertDoesNotExist()
        compose.onNodeWithText("Autorizar con DNI").assertDoesNotExist()
        compose.onNodeWithText("Autorizar con biometría").performClick()
        compose.onNodeWithText("Eliminar passkey").performScrollTo().performClick()
        compose.onNodeWithText("Autorizar eliminación").performClick()
        compose.runOnIdle { assertEquals(1, authorized); assertEquals("fixture", deleted) }
    }
    @Test fun legacyStillShowsCardAuthorization() {
        compose.setContent {
            PasskeyCeremonyScreen(PasskeyUiState(ready = true), onCan = {}, onPin = {},
                onAuthorize = {}, onDelete = {}, onCancel = {}, nfcEnabled = true)
        }
        compose.onNodeWithText("CAN").assertExists()
        compose.onNodeWithText("PIN del DNI").assertExists()
        compose.onNodeWithText("Autorizar con biometría").assertDoesNotExist()
    }
}
