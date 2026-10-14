package pe.gob.reniec.dnie.ui.signer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Rule
import org.junit.Test
import pe.gob.reniec.dnie.ui.theme.DnieTheme
import java.io.File

/** A local synthetic PDF only: no PIN, card, public SAF or user documents. */
class SignerStickyBarR8Test {
    @get:Rule val rule = createComposeRule()
    @Test fun placementAndCompactTabsKeepTheSigningActionAccessible() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        val source = File.createTempFile("r8-ui-", ".pdf", context.cacheDir)
        PDDocument().use { it.addPage(PDPage()); it.save(source) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", source)
        val vm = SignerViewModel()
        try {
            rule.setContent { DnieTheme(darkTheme = false) { SignerScreen(vm) } }
            rule.runOnIdle { vm.importDocuments(context, listOf(uri), singlePdf = true) }
            rule.waitUntil(15_000) { !vm.workspace.value.importing && vm.workspace.value.documents.size == 1 }
            rule.onNodeWithTag("signer_action_bar").assertIsDisplayed()
            rule.onNodeWithText("Firmar PDF").assertIsDisplayed()
            rule.onNodeWithText("Colocar sello · pantalla completa").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Datos y TSA").performScrollTo().performClick()
            rule.onNodeWithText("Sello de tiempo RENIEC · PAdES-T").performScrollTo().assertExists()
            rule.onNodeWithText("Firmar PDF").assertIsDisplayed()
            rule.onNodeWithText("Páginas y Titular").performScrollTo().performClick()
            rule.onNodeWithText("Certificado del firmante").performScrollTo().assertExists()
            rule.onNodeWithTag("signer_action_bar").assertIsDisplayed()
        } finally { rule.runOnIdle { vm.reset() }; source.delete() }
    }
}
