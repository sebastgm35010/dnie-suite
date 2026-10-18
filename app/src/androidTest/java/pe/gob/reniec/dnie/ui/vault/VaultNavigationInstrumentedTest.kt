package pe.gob.reniec.dnie.ui.vault

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises navigation and bulk intent with fixtures, never user storage or NFC. */
class VaultNavigationInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private fun click(text: String) = compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) { it() }

    @Test fun filesHideCredentialsAndSelectionSurvivesNavigationSubmittingOneBatch() {
        val batches = mutableListOf<Pair<List<String>, Boolean>>()
        val actions = VaultLibraryActions(
            importFiles = {}, exportBackup = {}, importBackup = {}, query = {}, folder = {}, sort = {}, trash = {},
            organize = { _, _, _ -> }, recover = {}, purge = {}, preview = {}, createNote = { _, _ -> },
            encryptIncoming = {}, dismissIncoming = {}, inspect = {}, closeDetails = {},
            trashMany = { ids, value -> batches += ids to value }, organizeMany = { _, _, _ -> })
        compose.setContent {
            VaultScreen(VaultUiState(riskAcknowledged = true, pairedFingerprint = "fixture", allItems = 2, items = listOf(
                VaultItemUi("a", 20, "uno.txt"), VaultItemUi("b", 40, "dos.pdf"))),
                onCanChange = {}, onPinChange = {}, onRememberCan = {}, onRun = {}, onBack = {}, onCancel = {},
                onImportArchive = {}, onExportEncrypted = { _, _ -> }, onRequestOpen = { _, _ -> }, onStartOpen = {},
                onClearSelection = {}, onDelete = {}, onAcknowledgeRisk = {}, library = actions,
                recovery = VaultRecoveryActions({ _, _ -> }, { _, _ -> }))
        }
        compose.onNodeWithText("CAN").assertDoesNotExist()
        compose.onNodeWithText("Guardar respaldo ZIP").assertDoesNotExist()
        click("Seleccionar varios"); click("Todos los visibles")
        compose.onNodeWithText("2 seleccionado(s)").assertExists()
        click("Exportar bóveda")
        compose.onNodeWithText("Elegir destino").assertExists()
        compose.onNodeWithText("PIN del DNI").assertDoesNotExist()
        compose.onNodeWithText("Guardar respaldo ZIP").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").performClick()
        click("Importar cifrados"); click("Bóveda completa (.dniebackup)")
        compose.onNodeWithText("Elegir respaldo").assertExists()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithContentDescription("Opciones de bóveda").performClick(); click("Vincular o cambiar DNI"); compose.onNodeWithText("PIN del DNI").assertExists()
        compose.onNodeWithContentDescription("Volver").performClick(); compose.onNodeWithText("2 seleccionado(s)").assertExists()
        click("A papelera"); click("Continuar")
        compose.runOnIdle {
            assertEquals(1, batches.size)
            assertEquals(setOf("a", "b"), batches.single().first.toSet())
            assertTrue(batches.single().second)
        }
        compose.onNodeWithText("2 seleccionado(s)").assertDoesNotExist()
    }


    @Test fun fileCardMenuShowsExportOptionAndInvokesCallback() {
        val actions = VaultLibraryActions(
            importFiles = {}, exportBackup = {}, importBackup = {}, query = {}, folder = {}, sort = {}, trash = {},
            organize = { _, _, _ -> }, recover = {}, purge = {}, preview = {}, createNote = { _, _ -> },
            encryptIncoming = {}, dismissIncoming = {}, inspect = {}, closeDetails = {},
            trashMany = { _, _ -> }, organizeMany = { _, _, _ -> })
        compose.setContent {
            VaultScreen(
                VaultUiState(riskAcknowledged = true, pairedFingerprint = "fixture", allItems = 1,
                    items = listOf(VaultItemUi("test-1", 100, "documento.pdf"))),
                onCanChange = {}, onPinChange = {}, onRememberCan = {}, onRun = {}, onBack = {}, onCancel = {},
                onImportArchive = {}, onExportEncrypted = { _, _ -> },
                onRequestOpen = { _, _ -> }, onStartOpen = {}, onClearSelection = {},
                onDelete = {}, onAcknowledgeRisk = {}, library = actions,
                recovery = VaultRecoveryActions({ _, _ -> }, { _, _ -> }))
        }
        compose.onNodeWithContentDescription("Opciones del archivo").performClick()
        compose.onNodeWithText("Exportar copia cifrada (.dnievault)").assertExists()
        compose.onNodeWithText("Exportar copia cifrada (.dnievault)").performClick()
    }

    @Test fun backFromSubpageReturnsToFilesWithoutLeavingVault() {
        var leftVault = false
        val actions = VaultLibraryActions(
            importFiles = {}, exportBackup = {}, importBackup = {}, query = {}, folder = {}, sort = {}, trash = {},
            organize = { _, _, _ -> }, recover = {}, purge = {}, preview = {}, createNote = { _, _ -> },
            encryptIncoming = {}, dismissIncoming = {}, inspect = {}, closeDetails = {},
            trashMany = { _, _ -> }, organizeMany = { _, _, _ -> })
        compose.setContent {
            VaultScreen(
                VaultUiState(riskAcknowledged = true, pairedFingerprint = "fixture", allItems = 1,
                    items = listOf(VaultItemUi("test-1", 100, "documento.pdf"))),
                onCanChange = {}, onPinChange = {}, onRememberCan = {}, onRun = {},
                onBack = { leftVault = true }, onCancel = {},
                onImportArchive = {}, onExportEncrypted = { _, _ -> },
                onRequestOpen = { _, _ -> }, onStartOpen = {}, onClearSelection = {},
                onDelete = {}, onAcknowledgeRisk = {}, library = actions,
                recovery = VaultRecoveryActions({ _, _ -> }, { _, _ -> }))
        }
        compose.onNodeWithText("Respaldo").performClick()
        compose.onNodeWithText("Exportar bóveda").assertExists()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Mis archivos").assertExists()
        assertFalse(leftVault)

        compose.onNodeWithContentDescription("Opciones de bóveda").performClick()
        compose.onNodeWithText("Vincular o cambiar DNI").assertExists()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Mis archivos").assertExists()
        assertFalse(leftVault)
    }
}
