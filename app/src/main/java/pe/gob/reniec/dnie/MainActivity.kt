package pe.gob.reniec.dnie

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import pe.gob.reniec.dnie.ui.console.ConsoleViewModel
import pe.gob.reniec.dnie.ui.navigation.NavGraph
import pe.gob.reniec.dnie.ui.signer.SignerUiState
import pe.gob.reniec.dnie.ui.signer.SignerViewModel
import pe.gob.reniec.dnie.ui.theme.DnieTheme

/**
 * Actividad principal de la aplicación DNIe Signer Perú.
 * Configurada en modo singleTop para el ciclo de vida seguro de NFC Reader Mode.
 */
class MainActivity : ComponentActivity() {

    private val certificatesViewModel: CertificatesViewModel by viewModels()
    private val signerViewModel: SignerViewModel by viewModels()
    private val consoleViewModel: ConsoleViewModel by viewModels()

    private val nfcReaderManager by lazy {
        NfcReaderManager(NfcAdapter.getDefaultAdapter(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        consoleViewModel.canProvider = {
            pe.gob.reniec.dnie.ui.components.CanStorage.getSavedCan(this).ifBlank { null }
        }

        // Escucha reactiva de tags NFC descubiertos
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                nfcReaderManager.discoveredTags.collect { tag ->
                    dispatchNfcTag(tag)
                }
            }
        }

        setContent {
            DnieTheme {
                NavGraph(
                    certificatesViewModel = certificatesViewModel,
                    signerViewModel = signerViewModel,
                    consoleViewModel = consoleViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcReaderManager.enableReaderMode(this)
    }

    override fun onPause() {
        super.onPause()
        nfcReaderManager.disableReaderMode(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(70)
            }
        } catch (_: Exception) {}
    }

    /**
     * Despacha el tag NFC descubierto al ViewModel activo según la operación en curso,
     * propagando las credenciales (CAN y/o PIN) recolectadas previamente en pantalla.
     */
    private fun dispatchNfcTag(tag: Tag) {
        triggerHapticFeedback()
        val channel = IsoDepCardChannel.fromTag(tag) ?: return

        // Si el usuario activó el sondeo diagnóstico de AIDs
        if (consoleViewModel.isProbeActive) {
            consoleViewModel.onCardDetected(channel)
        } else if (signerViewModel.uiState.value is SignerUiState.AwaitingCard) {
            val awaiting = signerViewModel.uiState.value as SignerUiState.AwaitingCard
            val transceiver = DnieTransceiver(
                channel = channel,
                can = awaiting.can ?: signerViewModel.enteredCan,
                pin = awaiting.pin
            )
            signerViewModel.onCardDetected(transceiver, this)
        } else {
            // Por defecto, procesar lectura en el inspector de certificados con el CAN recolectado
            val transceiver = DnieTransceiver(
                channel = channel,
                can = certificatesViewModel.enteredCan
            )
            certificatesViewModel.onCardDetected(transceiver)
        }
    }
}
