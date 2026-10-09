package pe.gob.reniec.dnie.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import pe.gob.reniec.dnie.MainActivity
import pe.gob.reniec.dnie.DnieApplication
import pe.gob.reniec.dnie.data.preferences.ThemeMode

/** Real navigation, no NFC authorization or modification of user documents. */
class RedesignContinuityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun vaultIsAPrimaryDestinationAndDoesNotHideOtherTabs() {
        rule.onNodeWithTag("nav_vault").performClick()
        rule.onNodeWithText("ARCHIVOS PROTEGIDOS").assertExists()
        rule.onNodeWithTag("nav_home").assertIsDisplayed()
        rule.onNodeWithTag("nav_certificates").performClick()
        rule.onNodeWithText("Mi DNIe").assertExists()
        rule.onNodeWithTag("nav_vault").performClick()
        rule.onNodeWithTag("nav_identity").performClick()
        rule.onNodeWithText("Identidad del DNI").assertExists()
    }

    @Test fun explicitThemesPersistAndCanBeChangedWithoutClosingVault() {
        val app = rule.activity.application as DnieApplication
        rule.waitUntil { app.appearance.mode.value != null }
        val original = app.appearance.mode.value!!
        try {
            rule.onNodeWithContentDescription("Configuración").performClick()
            rule.onNodeWithText("Apariencia").assertExists()
            for (mode in ThemeMode.entries) {
                rule.onNodeWithTag("theme_" + mode.name.lowercase()).performClick()
                rule.waitUntil { app.appearance.mode.value == mode }
                rule.onNodeWithTag("theme_" + mode.name.lowercase()).assertIsSelected()
                rule.activityRule.scenario.recreate()
                rule.waitForIdle()
                assertEquals(mode, app.appearance.mode.value)
            }
            rule.onNodeWithTag("nav_vault").performClick()
            rule.onNodeWithTag("nav_signer").performClick()
            rule.onNodeWithText("Firma PDF").assertExists()
        } finally {
            app.appearance.select(original)
            rule.waitUntil { app.appearance.mode.value == original }
        }
    }

    @Test fun pcReturnDestinationIsConsumedAndDoesNotReplayAfterRecreation() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.startActivity(android.content.Intent(activity.intent)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("destination", "identity"))
        }
        rule.onNodeWithText("Identidad del DNI").assertExists()
        rule.waitUntil { !rule.activity.intent.hasExtra("destination") }
        rule.onNodeWithTag("nav_home").performClick()
        rule.onNodeWithContentDescription("Configuración").performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Apariencia").assertExists()
    }

    @Test fun sharedIntakeOpensVaultOnceAndDoesNotTrapNavigation() {
        rule.activityRule.scenario.onActivity { activity ->
            androidx.lifecycle.ViewModelProvider(activity)[pe.gob.reniec.dnie.ui.vault.VaultViewModel::class.java]
                .receiveSharedFiles(listOf(android.net.Uri.parse("content://fixture/not-opened.txt")))
        }
        rule.onNodeWithTag("nav_vault").assertIsSelected()
        rule.onNodeWithTag("nav_home").performClick()
        rule.onNodeWithTag("nav_home").assertIsSelected()
        rule.activityRule.scenario.onActivity { activity ->
            androidx.lifecycle.ViewModelProvider(activity)[pe.gob.reniec.dnie.ui.vault.VaultViewModel::class.java]
                .dismissSharedFiles()
        }
    }
}
