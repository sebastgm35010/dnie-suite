package pe.gob.reniec.dnie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import pe.gob.reniec.dnie.ui.components.*
import pe.gob.reniec.dnie.ui.design.*
import pe.gob.reniec.dnie.ui.theme.DnieTheme

class FinishR10Test {
    @get:Rule val rule=createComposeRule()
    @Test fun savedCanLoadsOnceButNeverOverwritesAnEditThatPrecedesDiskCompletion() {
        val disk=CompletableDeferred<String>();val value=mutableStateOf("");var calls=0
        rule.setContent {DnieTheme(false) {ConnectionCanField(value.value,{value.value=it;calls++},true,readSavedCan={disk.await()})}}
        rule.onNodeWithTag("connection_can").performTextInput("777")
        rule.runOnIdle {disk.complete("123456")}
        rule.waitForIdle()
        rule.runOnIdle {assertEquals("777",value.value);assertEquals(1,calls)}
    }
    @Test fun loadedCanUsesCompactChipAndOnlyExplicitChangeOpensTheField() {
        val value=mutableStateOf("")
        rule.setContent {DnieTheme(false) {ConnectionCanField(value.value,{value.value=it},true,readSavedCan={"123456"})}}
        rule.waitUntil {rule.onAllNodesWithText("CAN guardado: ••••56").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("connection_can").assertDoesNotExist()
        rule.onNodeWithText("Cambiar").performClick()
        rule.onNodeWithTag("connection_can").assertExists()
    }
    @Test fun segmentedTabsRespectSelectionAndReducedMotionWithoutDuplicatingActions() {
        val selected=mutableStateOf(0);var changes=0
        rule.setContent {CompositionLocalProvider(LocalReducedMotionOverride provides true) {DnieTheme(true) {
            AetherSegmentedTabs(listOf("Apariencia","Datos y TSA","Páginas y Titular"),selected.value,{selected.value=it;changes++})
        }}}
        rule.onNodeWithText("Apariencia").assertIsSelected()
        rule.onNodeWithText("Datos y TSA").performClick().assertIsSelected()
        rule.onNodeWithText("Datos y TSA").performClick()
        rule.runOnIdle {assertEquals(1,changes)}
        rule.onNodeWithText("Páginas y Titular").performClick().assertIsSelected()
    }
    @Test fun linkedPcDescribesStoredTrustAndDoesNotClaimAnActiveSession() {
        rule.setContent {DnieTheme(false) {LinkedPcCard("pc.fixture","AA:BB:CC")}}
        rule.onNodeWithText("PC vinculada").assertExists()
        rule.onNodeWithText("TLS con huella fijada · al conectar").assertExists()
        rule.onNodeWithText("AA:BB:CC").assertDoesNotExist()
        rule.onNodeWithText("Comparar huella con la PC").performClick()
        rule.onNodeWithText("AA:BB:CC").assertExists()
    }
}
