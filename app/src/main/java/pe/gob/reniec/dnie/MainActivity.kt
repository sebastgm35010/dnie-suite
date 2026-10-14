package pe.gob.reniec.dnie

import pe.gob.reniec.dnie.data.feedback.observeReadingFeedback
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import pe.gob.reniec.dnie.core.nfc.DnieTransceiver
import pe.gob.reniec.dnie.core.nfc.IsoDepCardChannel
import pe.gob.reniec.dnie.core.nfc.NfcReaderManager
import pe.gob.reniec.dnie.ui.certificates.CertificatesViewModel
import pe.gob.reniec.dnie.development.DevelopmentController
import pe.gob.reniec.dnie.ui.navigation.NavGraph
import pe.gob.reniec.dnie.ui.signer.SignerUiState
import pe.gob.reniec.dnie.ui.signer.SignerViewModel
import pe.gob.reniec.dnie.ui.theme.DnieTheme
import pe.gob.reniec.dnie.ui.identity.IdentityViewModel
import pe.gob.reniec.dnie.ui.navigation.Screen
import pe.gob.reniec.dnie.icao.IcaoDocumentReader
import pe.gob.reniec.dnie.ui.vault.VaultViewModel

/**
 * Actividad principal de la aplicación DNIe Signer Perú.
 * Configurada en modo singleTop para el ciclo de vida seguro de NFC Reader Mode.
 */
class MainActivity : ComponentActivity() {

    private val certificatesViewModel: CertificatesViewModel by viewModels()
    private val signerViewModel: SignerViewModel by viewModels()
    private val batchSignerViewModel: SignerViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this).get("batchSigner", SignerViewModel::class.java)
    }
    private val consoleViewModel: DevelopmentController by viewModels()
    private val identityViewModel: IdentityViewModel by viewModels()
    private val vaultViewModel: VaultViewModel by viewModels()
    private var currentRoute: String? = null
    private val requestedDestination = androidx.compose.runtime.mutableStateOf<String?>(null)
    private var frameDiagnostics: pe.gob.reniec.dnie.diagnostics.UiFrameDiagnostics? = null

    private val nfcReaderManager by lazy {
        NfcReaderManager(NfcAdapter.getDefaultAdapter(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeReadingFeedback(this,certificatesViewModel.feedback)
        observeReadingFeedback(this,signerViewModel.feedback)
        observeReadingFeedback(this,batchSignerViewModel.feedback)
        observeReadingFeedback(this,identityViewModel.feedback)
        observeReadingFeedback(this,vaultViewModel.feedback)

        pe.gob.reniec.dnie.diagnostics.AppFlowDiagnostics.observe(this, "certificates", certificatesViewModel.uiState) { it.javaClass.simpleName }
        pe.gob.reniec.dnie.diagnostics.AppFlowDiagnostics.observe(this, "signer", signerViewModel.uiState) { it.javaClass.simpleName }
        pe.gob.reniec.dnie.diagnostics.AppFlowDiagnostics.observe(this, "batch", batchSignerViewModel.uiState) { it.javaClass.simpleName }
        pe.gob.reniec.dnie.diagnostics.AppFlowDiagnostics.observe(this, "identity", identityViewModel.uiState) {
            when { it.reading -> "reading"; it.awaitingCard -> "awaiting_card"; it.enteringCan -> "credentials"; else -> "idle_or_finished" }
        }
        pe.gob.reniec.dnie.diagnostics.AppFlowDiagnostics.observe(this, "vault", vaultViewModel.uiState) {
            when { it.running -> "running"; it.awaitingCard -> "awaiting_card"; it.preview != null -> "preview_ready"; else -> "idle_or_finished" } + ":items=" + it.totalItems
        }
        if (BuildConfig.DEBUG) frameDiagnostics = pe.gob.reniec.dnie.diagnostics.UiFrameDiagnostics(window)
        vaultViewModel.receiveSharedFiles(pe.gob.reniec.dnie.data.vault.VaultShareIntake.read(intent))

        consoleViewModel.canProvider = {
            pe.gob.reniec.dnie.data.preferences.CanStorage.getSavedCan(this).ifBlank { null }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                certificatesViewModel.identitySnapshot.collect { snapshot ->
                    if(snapshot.generation>0) identityViewModel.acceptUnifiedReading(snapshot.document,snapshot.message,snapshot.reading)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var fingerprint="<not-observed>"
                certificatesViewModel.uiState.collect {state->
                    if(state is pe.gob.reniec.dnie.ui.certificates.CertificatesUiState.Loaded) {
                        val certificate=state.certificates[pe.gob.reniec.dnie.domain.model.CertificateType.SIGNATURE]
                        val next=certificate?.sha256FingerprintHex.orEmpty()
                        if(next!=fingerprint) {
                            fingerprint=next
                            signerViewModel.setPublicCertificate(certificate?.rawDerBytes)
                            batchSignerViewModel.setPublicCertificate(certificate?.rawDerBytes)
                        }
                    }
                }
            }
        }

        // Escucha reactiva de tags NFC descubiertos
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                nfcReaderManager.discoveredTags.collect { tag ->
                    dispatchNfcTag(tag)
                }
            }
        }

        requestedDestination.value = intent.getStringExtra("destination")
        setContent {
            DnieTheme {
                NavGraph(
                    requestedDestination = requestedDestination.value,
                    onDestinationConsumed = { requestedDestination.value = null; intent.removeExtra("destination") },
                    certificatesViewModel = certificatesViewModel,
                    signerViewModel = signerViewModel,
                    batchSignerViewModel = batchSignerViewModel,
                    consoleViewModel = consoleViewModel,
                    identityViewModel = identityViewModel,
                    vaultViewModel = vaultViewModel,
                    onOpenBridge = {
                        if (certificatesViewModel.busy || consoleViewModel.busy || consoleViewModel.isProbeActive ||
                            signerViewModel.busy || batchSignerViewModel.busy || identityViewModel.uiState.value.reading ||
                            vaultViewModel.uiState.value.running) {
                            android.widget.Toast.makeText(this, "Termina la operación NFC actual antes de abrir el puente.", android.widget.Toast.LENGTH_SHORT).show()
                        } else startActivity(Intent(this, pe.gob.reniec.dnie.ui.bridge.BridgeActivity::class.java))
                    },
                    onOpenSuite = {
                        if (certificatesViewModel.busy || consoleViewModel.busy || consoleViewModel.isProbeActive ||
                            signerViewModel.busy || batchSignerViewModel.busy || identityViewModel.uiState.value.reading ||
                            vaultViewModel.uiState.value.running) {
                            android.widget.Toast.makeText(this, "Termina la operación NFC actual antes de abrir Suite.", android.widget.Toast.LENGTH_SHORT).show()
                        } else startActivity(Intent(this, pe.gob.reniec.dnie.ui.suite.RemoteSigningActivity::class.java))
                    },
                    onRouteChanged = {
                        if (currentRoute != it) {
                            when (currentRoute) {
                                Screen.Vault.route -> { vaultViewModel.clearSelection(); vaultViewModel.cancel() }
                                Screen.Certificates.route -> { certificatesViewModel.cancelCanEntry(); certificatesViewModel.cancelReading() }
                                Screen.Signer.route -> if (signerViewModel.busy) signerViewModel.cancelSigning()
                                Screen.BatchSigner.route -> if (batchSignerViewModel.busy) batchSignerViewModel.cancelSigning()
                                Screen.Identity.route -> identityViewModel.cancelReading()
                                Screen.Console.route -> pe.gob.reniec.dnie.development.cancelDevelopment(consoleViewModel)
                            }
                        }
                        currentRoute = it
                        pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("navigation", "route", "state" to it, "layer" to "ui")
                        frameDiagnostics?.onRoute(it)
                        val shouldBeSecure = it == Screen.Vault.route
                        val isSecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
                        if (shouldBeSecure != isSecure) {
                            if (shouldBeSecure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcReaderManager.enableReaderMode(this)
    }

    override fun onDestroy() {
        frameDiagnostics?.close()
        frameDiagnostics = null
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        certificatesViewModel.cancelReading()
        identityViewModel.cancelReading()
        vaultViewModel.cancel()
        if (signerViewModel.busy) signerViewModel.cancelSigning()
        if (batchSignerViewModel.busy) batchSignerViewModel.cancelSigning()
        nfcReaderManager.disableReaderMode(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDestination.value = intent.getStringExtra("destination")
        vaultViewModel.receiveSharedFiles(pe.gob.reniec.dnie.data.vault.VaultShareIntake.read(intent))
        @Suppress("DEPRECATION")
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            dispatchNfcTag(tag)
        }
    }

    /**
     * Emite una vibración háptica de 70 ms inmediatamente cuando la tarjeta entra en el campo NFC.
     */
    private fun triggerHapticFeedback() {
        pe.gob.reniec.dnie.data.feedback.ReadingFeedback.start(this)
    }

    /**
     * Despacha el tag NFC descubierto al ViewModel activo según la operación en curso,
     * propagando las credenciales (CAN y/o PIN) recolectadas previamente en pantalla.
     */
    private fun dispatchNfcTag(tag: Tag) {
        pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("nfc", "tag_detected", "state" to currentRoute)
        // Landing and connection chooser are navigation surfaces, not card operations.
        if (currentRoute == Screen.Home.route || currentRoute == Screen.Pc.route || currentRoute == "settings") return
        if (certificatesViewModel.busy || identityViewModel.uiState.value.reading) return
        if (vaultViewModel.uiState.value.running) return
        if (signerViewModel.uiState.value is SignerUiState.Signing || batchSignerViewModel.uiState.value is SignerUiState.Signing) return
        if (currentRoute == Screen.Vault.route) {
            val can = vaultViewModel.requestedCan ?: return
            val channel = IsoDepCardChannel.fromTag(tag) ?: return
            triggerHapticFeedback()
            vaultViewModel.onCardDetected(DnieTransceiver(channel = channel, can = can))
            return
        }
        val signingRoute = currentRoute == Screen.Signer.route || currentRoute == Screen.BatchSigner.route
        val activeSigner = if (currentRoute == Screen.BatchSigner.route) batchSignerViewModel else signerViewModel
        if (signingRoute && activeSigner.uiState.value !is SignerUiState.AwaitingCard) return
        if (currentRoute == Screen.Identity.route) {
            val can = identityViewModel.requestedCan ?: return
            val channel = IsoDepCardChannel.fromTag(tag) ?: return
            triggerHapticFeedback()
            identityViewModel.onCardDetected(IcaoDocumentReader(channel, can))
            return
        }
        triggerHapticFeedback()
        val channel = IsoDepCardChannel.fromTag(tag) ?: return

        // Si el usuario activó el sondeo diagnóstico de AIDs
        if (currentRoute == Screen.Console.route && consoleViewModel.isProbeActive) {
            consoleViewModel.onCardDetected(channel)
        } else if (signingRoute && activeSigner.uiState.value is SignerUiState.AwaitingCard) {
            val awaiting = activeSigner.uiState.value as SignerUiState.AwaitingCard
            val transceiver = DnieTransceiver(
                channel = channel,
                can = awaiting.can ?: activeSigner.enteredCan,
                pin = awaiting.pin
            )
            activeSigner.onCardDetected(transceiver, this)
        } else {
            // Por defecto, procesar lectura en el inspector de certificados con el CAN recolectado
            val transceiver = DnieTransceiver(
                channel = channel,
                can = certificatesViewModel.enteredCan
            )
            val can=certificatesViewModel.enteredCan
            certificatesViewModel.onCardDetected(transceiver, if(can?.length==6) ({
                val fresh=IsoDepCardChannel.fromTag(tag) ?: error("No hay canal NFC ICAO disponible")
                IcaoDocumentReader(fresh,can)
            }) else null)
        }
    }
}
