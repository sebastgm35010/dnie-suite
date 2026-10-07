package pe.gob.reniec.dnie.ui.diagnostics

import android.os.Bundle
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import pe.gob.reniec.dnie.core.nfc.IsoDepCardChannel
import pe.gob.reniec.dnie.core.nfc.NfcReaderManager
import pe.gob.reniec.dnie.data.preferences.CanStorage
import pe.gob.reniec.dnie.development.DevelopmentController
import pe.gob.reniec.dnie.development.DevelopmentScreen
import pe.gob.reniec.dnie.ui.theme.DnieTheme

class DiagnosticsActivity : ComponentActivity() {
    private val model: DevelopmentController by viewModels()
    private lateinit var reader: NfcReaderManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        reader = NfcReaderManager(NfcAdapter.getDefaultAdapter(this))
        model.canProvider = { CanStorage.getSavedCan(this) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                reader.discoveredTags.collect { tag ->
                    if (model.isProbeActive) IsoDep.get(tag)?.let { model.onCardDetected(IsoDepCardChannel(it)) }
                }
            }
        }
        setContent { DnieTheme { DevelopmentScreen(model) } }
    }
    override fun onResume() { super.onResume(); reader.enableReaderMode(this) }
    override fun onPause() { model.cancelCardProbe(); reader.disableReaderMode(this); super.onPause() }
}
