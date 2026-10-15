package pe.gob.reniec.dnie.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import pe.gob.reniec.dnie.ui.components.*
import pe.gob.reniec.dnie.ui.theme.DnieTheme

/** Isolated UI fixture: never authorizes NFC, signs or modifies stored CAN. */
class OperationSheetR8Test {
    @get:Rule val rule = createComposeRule()
    @Test fun sameSheetRemainsVisibleWhileCardIsEngagedAndUntilCompletion() {
        val phase = mutableStateOf(DnieOperationPhase.AWAITING_TAG)
        rule.setContent { DnieTheme(darkTheme = false) {
            DnieOperationSheet(true, phase.value, "Prueba sintética", message = "Estado de prueba", onDismiss = {})
        } }
        rule.onNodeWithTag("nfc_phase_awaiting_tag").assertIsDisplayed()
        rule.runOnIdle { phase.value = DnieOperationPhase.TAG_ENGAGED }
        rule.onNodeWithTag("dnie_operation_sheet").assertIsDisplayed()
        rule.onNodeWithTag("nfc_phase_tag_engaged").assertIsDisplayed()
        rule.runOnIdle { phase.value = DnieOperationPhase.COMPLETED }
        rule.onNodeWithText("¡Listo! Ya puedes retirar tu DNIe.").assertIsDisplayed()
        rule.onNodeWithText("Volver").assertIsDisplayed()
    }
    @Test fun savedCanIsMaskedAndCanBeEditedWithoutSendingCredentials() {
        rule.setContent { DnieTheme(darkTheme = false) { CredentialsDialog(true, requirePin = true,
            initialCan = "123456", onConfirm = { _, _ -> error("Test must not authorize") }, onDismiss = {}) } }
        rule.waitUntil { rule.onAllNodesWithText("••••56", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("CAN (6 dígitos)").assertDoesNotExist()
        rule.onNodeWithTag("operation_pin").performTextInput("1234")
        rule.onNodeWithText("Continuar a lectura NFC").assertIsEnabled()
        rule.onNodeWithText("Cambiar").performClick()
        // IME/insets and sheet layout settle asynchronously on physical Android.
        rule.waitUntil(5000) { rule.onAllNodesWithText("CAN (6 dígitos)").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("CAN (6 dígitos)").assertExists()
    }
    @Test fun anErrorRequiresAnExplicitNewAuthorization() {
        var retries = 0
        rule.setContent { DnieTheme(darkTheme = false) {
            DnieOperationSheet(true, DnieOperationPhase.ERROR, "Prueba sintética", onDismiss = {}, onRetry = { retries++ })
        } }
        rule.runOnIdle { org.junit.Assert.assertEquals(0, retries) }
        rule.onNodeWithText("Reintentar con nueva autorización").performClick()
        rule.runOnIdle { org.junit.Assert.assertEquals(1, retries) }
    }

}
