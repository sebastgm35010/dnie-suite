package pe.gob.reniec.dnie

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
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
     * Despacha el tag NFC descubierto al ViewModel activo según la operación en curso.
     */
    private fun dispatchNfcTag(tag: Tag) {
        val channel = IsoDepCardChannel.fromTag(tag) ?: return
        val transceiver = DnieTransceiver(channel)

        // Si el usuario activó el sondeo diagnóstico de AIDs
        if (consoleViewModel.isProbeActive) {
            consoleViewModel.onCardDetected(channel)
        } else if (signerViewModel.uiState.value is SignerUiState.AwaitingCard) {
            signerViewModel.onCardDetected(transceiver, this)
        } else {
            // Por defecto, procesar lectura en el inspector de certificados
            certificatesViewModel.onCardDetected(transceiver)
        }
    }
}
