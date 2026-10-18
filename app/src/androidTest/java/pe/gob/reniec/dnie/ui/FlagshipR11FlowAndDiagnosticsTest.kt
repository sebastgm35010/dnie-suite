package pe.gob.reniec.dnie.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.gob.reniec.dnie.MainActivity
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.diagnostics.AppDiagnostics
import pe.gob.reniec.dnie.diagnostics.DiagnosticEvent
import pe.gob.reniec.dnie.diagnostics.DiagnosticLog
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult
import pe.gob.reniec.dnie.domain.signing.PdfPageSize
import pe.gob.reniec.dnie.domain.signing.SigningDocument
import pe.gob.reniec.dnie.pdf.NormalizedStampPlacement
import pe.gob.reniec.dnie.pdf.PdfSigningOptions
import pe.gob.reniec.dnie.pdf.SignaturePreset
import pe.gob.reniec.dnie.pdf.StampPlacementGeometry
import pe.gob.reniec.dnie.ui.certificates.CertificatesScreen
import pe.gob.reniec.dnie.ui.certificates.CertificatesUiState
import pe.gob.reniec.dnie.ui.certificates.CertificatesViewModel
import pe.gob.reniec.dnie.ui.components.CredentialsForm
import pe.gob.reniec.dnie.ui.design.LocalReducedMotionOverride
import pe.gob.reniec.dnie.ui.signer.PdfPlacementEditor
import pe.gob.reniec.dnie.ui.theme.DnieTheme
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class FlagshipR11FlowAndDiagnosticsTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun credentialsFormClearsPinOnPauseAndSupportsVisibilityToggle() {
        var dismissed = false
        var confirmed = false
        val lifecycleOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle get() = registry
        }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(
                        LocalReducedMotionOverride provides true,
                        LocalLifecycleOwner provides lifecycleOwner
                    ) {
                        DnieTheme(darkTheme = false) {
                            CredentialsForm(
                                requireCan = true,
                                requirePin = true,
                                initialCan = "123456",
                                onConfirm = { _, _ -> confirmed = true },
                                onDismiss = { dismissed = true }
                            )
                        }
                    }
                }
            }
            rule.waitUntil(5_000) {
                rule.onAllNodesWithText("Continuar a lectura NFC").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Continuar a lectura NFC").assertIsNotEnabled()
            rule.onNodeWithTag("operation_pin").performTextInput("1234")
            rule.onNodeWithText("Continuar a lectura NFC").assertIsEnabled()

            rule.onNodeWithContentDescription("Mostrar PIN").performClick()
            rule.onNodeWithContentDescription("Ocultar PIN").assertExists()
            rule.onNodeWithTag("operation_pin").assertTextContains("1234")
            rule.onNodeWithContentDescription("Ocultar PIN").performClick()
            rule.onNodeWithContentDescription("Mostrar PIN").assertExists()

            rule.onNodeWithContentDescription("Mostrar PIN").performClick()
            rule.runOnIdle {
                lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
            rule.onNodeWithText("Continuar a lectura NFC").assertIsNotEnabled()
            rule.onNodeWithContentDescription("Mostrar PIN").assertExists()

            rule.onNodeWithTag("operation_pin").performTextInput("5678")
            rule.onNodeWithText("Continuar a lectura NFC").assertIsEnabled()
            rule.onNodeWithText("Cancelar").performClick()
            rule.runOnIdle {
                assertTrue(dismissed)
                assertFalse(confirmed)
            }
            rule.onNodeWithText("Continuar a lectura NFC").assertIsNotEnabled()
        }
    }

    @Test fun certificateCardSwitchesTabsExpandsTechnicalDetailsAndCopiesFingerprint() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        fun buildDer(serial: Long, usage: Int): ByteArray {
            val subject = X500Name("CN=QUISPE MAMANI ANA MARIA, SERIALNUMBER=IDPER12345678, C=PE")
            val issuer = X500Name("CN=RENIEC CA, O=RENIEC, C=PE")
            val now = System.currentTimeMillis()
            return JcaX509v3CertificateBuilder(
                issuer, BigInteger.valueOf(serial),
                Date(now - 86_400_000L), Date(now + 365L * 86_400_000L),
                subject, pair.public
            ).addExtension(Extension.keyUsage, true, KeyUsage(usage))
                .build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)).encoded
        }
        val sigDer = buildDer(101L, KeyUsage.nonRepudiation)
        val authDer = buildDer(102L, KeyUsage.digitalSignature)
        val syntheticTransceiver = object : DnieTransceiverInterface {
            override suspend fun selectPkiApplet(): Result<Unit> = Result.success(Unit)
            override suspend fun readCertificate(type: CertificateType): Result<ByteArray> = when (type) {
                CertificateType.SIGNATURE -> Result.success(sigDer)
                CertificateType.AUTHENTICATION -> Result.success(authDer)
                CertificateType.ENCRYPTION -> Result.failure(IllegalStateException("Certificado no emitido"))
            }
            override suspend fun verifyPin(pin: String): Result<PinVerificationResult> =
                Result.failure(UnsupportedOperationException())
            override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> =
                Result.failure(UnsupportedOperationException())
        }

        val vm = CertificatesViewModel()
        vm.onCardDetected(syntheticTransceiver)
        val deadline = System.currentTimeMillis() + 10_000L
        while (vm.uiState.value !is CertificatesUiState.Loaded && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        assertTrue(vm.uiState.value is CertificatesUiState.Loaded)

        var signerOpened = false
        var vaultOpened = false
        var bridgeOpened = false

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                        DnieTheme(darkTheme = false) {
                            CertificatesScreen(
                                viewModel = vm,
                                onOpenSigner = { signerOpened = true },
                                onOpenVault = { vaultOpened = true },
                                onOpenBridge = { bridgeOpened = true }
                            )
                        }
                    }
                }
            }
            rule.onNodeWithText("Firmar PDF").performScrollTo().performClick()
            rule.onNodeWithText("Bóveda").performScrollTo().performClick()
            rule.onNodeWithText("Puente PC").performScrollTo().performClick()
            rule.runOnIdle {
                assertTrue(signerOpened)
                assertTrue(vaultOpened)
                assertTrue(bridgeOpened)
            }

            rule.onNodeWithText("Ver detalles técnicos").performScrollTo().performClick()
            rule.onNodeWithText("Ocultar detalles").assertExists()
            rule.onNodeWithText("Copiar huella").performScrollTo().performClick()
            rule.onNodeWithText("✓ Copiada").assertExists()

            rule.onNodeWithText("● Autenticación").performScrollTo().performClick()
            rule.onNodeWithText("Ver detalles técnicos").assertExists()

            rule.onNodeWithText("○ Cifrado (N/D)").performScrollTo().performClick()
            rule.onNodeWithText(
                "El certificado de Cifrado no se encuentra emitido en este DNIe."
            ).assertExists()
        }
    }

    @Test fun pdfPlacementEditorAppliesMagneticSnapCornersAndCancelPreservesOriginal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        val fixtureDir = File(context.cacheDir, "r11_placement_test").apply { mkdirs() }
        val pdfFile = File.createTempFile("r11-placement-", ".pdf", fixtureDir)
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(pdfFile)
        }
        val initialOptions = PdfSigningOptions(visible = true)
        val doc = SigningDocument(
            name = "r11-fixture.pdf",
            file = pdfFile,
            size = pdfFile.length(),
            pages = listOf(PdfPageSize(595f, 842f)),
            options = initialOptions,
            pdfInput = true
        )

        val snappedNearMargin = StampPlacementGeometry.snap(
            NormalizedStampPlacement(left = 0.054f, top = 0.048f, width = 0.40f, height = 0.12f)
        )
        assertEquals(0.05f, snappedNearMargin.placement.left, 0.0001f)
        assertEquals(0.05f, snappedNearMargin.placement.top, 0.0001f)
        assertEquals(0.05f, snappedNearMargin.vertical ?: -1f, 0.0001f)
        assertEquals(0.05f, snappedNearMargin.horizontal ?: -1f, 0.0001f)

        var dismissed = 0
        var applied: PdfSigningOptions? = null
        val reopenKey = mutableStateOf(0)

        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                            DnieTheme(darkTheme = false) {
                                androidx.compose.runtime.key(reopenKey.value) {
                                    PdfPlacementEditor(
                                        document = doc,
                                        name = "ANA MARÍA QUISPE MAMANI",
                                        dni = "12345678",
                                        options = initialOptions,
                                        onDismiss = { dismissed++ },
                                        onApply = { applied = it }
                                    )
                                }
                            }
                        }
                    }
                }
                rule.onNodeWithText("↖").performClick()
                rule.onNodeWithContentDescription("Volver").performClick()
                rule.runOnIdle {
                    assertEquals(1, dismissed)
                    assertNull(applied)
                }

                rule.runOnIdle { reopenKey.value++ }
                rule.waitUntil(15_000) {
                    runCatching { rule.onNodeWithText("Aplicar").assertIsEnabled(); true }.getOrDefault(false)
                }
                rule.onNodeWithText("↖").performClick()
                rule.onNodeWithText("↘").performClick()
                rule.onNodeWithText("Aplicar").performClick()
                rule.runOnIdle {
                    val expectedPlacement = StampPlacementGeometry.preset(
                        StampPlacementGeometry.preset(initialOptions.placement, SignaturePreset.TOP_LEFT),
                        SignaturePreset.BOTTOM_RIGHT
                    )
                    assertNotNull(applied)
                    assertEquals(expectedPlacement, applied!!.placement)
                }
            }
        } finally {
            pdfFile.delete()
        }
    }

    @Test fun fullNavigationEmitsStructuredDiagnosticEventsAndTogglesVaultFlagSecure() {
        val captured = CopyOnWriteArrayList<DiagnosticEvent>()
        val previousSink = runCatching { AppDiagnostics.store }.getOrNull()
        DiagnosticLog.install { event ->
            previousSink?.append(event)
            captured.add(event)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                rule.waitForIdle()
                scenario.onActivity { activity ->
                    assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
                }

                rule.onNodeWithTag("nav_signer").performClick()
                rule.waitForIdle()
                scenario.onActivity { activity ->
                    assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
                }

                rule.onNodeWithTag("nav_vault").performClick()
                rule.waitForIdle()
                scenario.onActivity { activity ->
                    assertTrue((activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0)
                }

                rule.onNodeWithTag("nav_certificates").performClick()
                rule.waitForIdle()
                scenario.onActivity { activity ->
                    assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
                }

                rule.onNodeWithTag("nav_pc").performClick()
                rule.waitForIdle()
                scenario.onActivity { activity ->
                    assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
                }

                rule.onNodeWithTag("nav_home").performClick()
                rule.waitForIdle()
                scenario.onActivity { activity ->
                    assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
                }

                val routes = captured
                    .filter { it.category == "navigation" && it.name == "route" && it.fields["layer"] == "ui" }
                    .mapNotNull { it.fields["state"] }
                assertTrue("Expected route transitions in DiagnosticLog, got: $routes",
                    routes.containsAll(listOf("home", "signer", "vault", "certificates", "pc")))
            }
        } finally {
            DiagnosticLog.install(previousSink)
        }
    }
}
