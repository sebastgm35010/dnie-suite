package pe.gob.reniec.dnie.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import pe.gob.reniec.dnie.ui.components.*
import pe.gob.reniec.dnie.ui.design.LocalReducedMotionOverride
import pe.gob.reniec.dnie.ui.home.*
import pe.gob.reniec.dnie.ui.onboarding.*
import pe.gob.reniec.dnie.ui.theme.DnieTheme

/** Isolated presentation fixtures: no real credentials, no system animation changes, no card operations. */
class DesignR9Test {
    @get:Rule val rule = createComposeRule()
    @Test fun guideIsOptInAndWelcomeDoesNotObstructHome() {
        val dismissed = mutableStateOf(false)
        var opened = false
        rule.setContent { DnieTheme(false) { HomeScreen(HomeSummary(), {}, {}, {}, {}, {}, {}, {},
            onboardingDismissed = dismissed.value, onOpenGuide = { opened = true }, onDismissOnboarding = { dismissed.value = true }) } }
        rule.onNodeWithText("Leer mi DNIe").assertIsDisplayed()
        rule.onNodeWithTag("onboarding_guide").assertDoesNotExist()
        rule.onNodeWithText("Ver guía rápida (3 pasos)").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(opened) }
        rule.onNodeWithText("Ocultar").performClick()
        rule.onNodeWithText("Ver guía rápida (3 pasos)").assertDoesNotExist()
    }
    @Test fun guideTraversesAllStepsWithoutCredentialsOrAuthorization() {
        var finished = false
        val model = OnboardingViewModel { _, _ -> error("No save requested") }
        rule.setContent { CompositionLocalProvider(LocalReducedMotionOverride provides true) { DnieTheme(false) {
            OnboardingGuideSheet({}, { finished = true }, model = model)
        } } }
        rule.onNodeWithText("Tu DNIe y el código CAN").assertExists()
        capture("guide-step-1.png")
        rule.onNodeWithText("Guardar mi CAN").assertIsNotEnabled()
        rule.onNodeWithText("Siguiente").performScrollTo().performClick()
        rule.onNodeWithText("Encuentra el sensor NFC").assertExists()
        capture("guide-step-2.png")
        rule.onNodeWithText("Siguiente").performScrollTo().performClick()
        rule.onNodeWithText("Firma en móvil o en Windows").assertExists()
        capture("guide-step-3.png")
        rule.onNodeWithText("Empezar a usar").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(finished) }
    }
    @Test fun canSaveIsExplicitAndUsesInjectedStoreWithoutChangingUserData() {
        var calls = 0
        val model = OnboardingViewModel { _, value ->
            withContext(Dispatchers.IO) { assertEquals("123456", value) }
            calls++
        }
        rule.setContent { CompositionLocalProvider(LocalReducedMotionOverride provides true) { DnieTheme(false) {
            OnboardingGuideSheet({}, {}, model = model)
        } } }
        rule.onNodeWithText("CAN opcional (6 dígitos)").performTextInput("12345")
        rule.onNodeWithText("Guardar mi CAN").assertIsNotEnabled()
        rule.onNodeWithText("CAN opcional (6 dígitos)").performTextInput("6")
        rule.runOnIdle { assertEquals(0, calls) }
        rule.onNodeWithText("Guardar mi CAN").performScrollTo().performClick()
        rule.waitUntil { model.state.value.saved }
        rule.onNodeWithText("CAN guardado en este teléfono").assertExists()
        rule.runOnIdle { assertEquals(1, calls); assertEquals("", model.state.value.can) }
    }
    @Test fun reducedMotionRadarRemainsStaticThroughAllReadingPhasesAndDarkContrastPasses() {
        val phase = mutableStateOf(DnieOperationPhase.AWAITING_TAG)
        rule.setContent { CompositionLocalProvider(LocalReducedMotionOverride provides true) { DnieTheme(true) {
            val colors = MaterialTheme.colorScheme
            assertEquals(Color(0xFF0B1326), colors.background)
            assertEquals(Color(0xFF6B9EFF), colors.primary)
            assertTrue((colors.primary.luminance() + .05f) / (colors.onPrimary.luminance() + .05f) >= 4.5f)
            DnieOperationSheet(true, phase.value, "Prueba R9", onDismiss = {})
        } } }
        rule.waitUntil(5000) { runCatching { rule.onAllNodesWithTag("nfc_visual_static").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        rule.onNodeWithTag("nfc_visual_static").assertExists()
        rule.onNodeWithTag("nfc_visual_animated").assertDoesNotExist()
        rule.runOnIdle { phase.value = DnieOperationPhase.TAG_ENGAGED }
        rule.onNodeWithTag("nfc_visual_static").assertExists()
        rule.runOnIdle { phase.value = DnieOperationPhase.COMPLETED }
        rule.onNodeWithText("¡Listo! Ya puedes retirar tu DNIe.").assertExists()
        rule.onNodeWithTag("nfc_visual_animated").assertDoesNotExist()
    }
    private fun capture(name: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = java.io.File(context.cacheDir, "design-r9/$name")
        output.parentFile!!.mkdirs()
        val bitmap = rule.onNodeWithTag("onboarding_guide").captureToImage().asAndroidBitmap()
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
