package pe.gob.reniec.dnie.ui.vault

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Simulates SAF's activity pause clearing PIN; no real files/card or production state. */
class VaultArchivePickerInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private fun click(text: String) = compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) { it() }

    @Test fun exportWaitsForCredentialsAfterPickerAndExplicitAuthorization() = flow(false)
    @Test fun importWaitsForCredentialsAfterPickerAndExplicitAuthorization() = flow(true)

    private fun flow(importing: Boolean) {
        var state by mutableStateOf(VaultUiState(riskAcknowledged = true, allItems = 2, can = "123456", pin = "1234"))
        var pendingCode = -1
        var submitted = 0
        val expected = Uri.parse("content://isolated-test/vault")
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                pendingCode = requestCode
                // Same effect as MainActivity.onPause -> vaultViewModel.cancel().
                state = state.copy(pin = "")
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        val submit: (Uri, CharArray) -> Unit = { uri, password ->
            assertEquals(expected, uri); assertEquals("contraseña-sintética-2026", String(password))
            password.fill('\u0000'); submitted++
            state = state.copy(awaitingCard = true, verified = false)
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                MaterialTheme { VaultRecoveryControls(state, VaultRecoveryActions(submit, submit),
                    { state = state.copy(can = it) }, { state = state.copy(pin = it) }, {}, importing) }
            }
        }
        compose.onNodeWithText("Contraseña del respaldo (mínimo 16 caracteres)").assertDoesNotExist()
        click(if (importing) "Elegir respaldo" else "Elegir destino")
        compose.runOnIdle { assertTrue(pendingCode != -1); registry.dispatchResult(pendingCode, expected) }
        compose.runOnIdle { assertEquals(0, submitted); assertFalse(state.awaitingCard); assertEquals("", state.pin) }
        compose.onNodeWithText(if (importing) "Respaldo elegido. Falta autorizar la importación." else "Destino elegido. El respaldo aún no se ha escrito.").assertExists()
        compose.onNodeWithText("PIN del DNI").performTextInput("1234")
        compose.onNodeWithText("Contraseña del respaldo (mínimo 16 caracteres)").performTextInput("contraseña-sintética-2026")
        if (!importing) compose.onNodeWithText("Repetir contraseña").performTextInput("contraseña-sintética-2026")
        click(if (importing) "Autorizar importación con DNI" else "Autorizar exportación con DNI")
        compose.runOnIdle { assertEquals(1, submitted); assertTrue(state.awaitingCard) }
        compose.onNodeWithText(if (importing) "Acerca el DNI de destino" else "Acerca el DNI para exportar").assertExists()
        compose.onNodeWithText("Contraseña del respaldo (mínimo 16 caracteres)").assertDoesNotExist()
    }
}
