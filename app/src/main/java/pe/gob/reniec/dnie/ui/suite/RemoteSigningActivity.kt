package pe.gob.reniec.dnie.ui.suite

import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.gob.reniec.dnie.core.nfc.NfcReaderManager
import pe.gob.reniec.dnie.data.bridge.BridgeQrScanner
import pe.gob.reniec.dnie.ui.theme.DnieTheme

class RemoteSigningActivity : ComponentActivity() {
    private val model: RemoteSigningViewModel by viewModels()
    private lateinit var reader: NfcReaderManager
    private val enabled = mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        reader = NfcReaderManager(NfcAdapter.getDefaultAdapter(this))
        val scanner = BridgeQrScanner(this)
        val addresses = runCatching { java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }
            .filterIsInstance<java.net.Inet4Address>().mapNotNull { it.hostAddress }
            .filter(pe.gob.reniec.dnie.signing.link.SigningProvision::privateAddress).joinToString(", ") }.getOrDefault("")
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) { reader.discoveredTags.collect { tag ->
            if (model.state.value.armed || model.state.value.connected) pe.gob.reniec.dnie.data.feedback.ReadingFeedback.start(this@RemoteSigningActivity)
            model.tag(tag)
        } } }
        setContent { DnieTheme {
            val state by model.state.collectAsStateWithLifecycle()
            val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::import) }
            RemoteSigningScreen(state, enabled.value, addresses,
                onImport = { importer.launch(arrayOf("text/plain", "application/octet-stream")) },
                onScan = { scanner.scan(model::import, model::status) }, onCan = model::can,
                onAuthorize = model::authorize, onConnect = model::connectRemembered, onForget = model::forgetPc, onStop = model::stop, onBack = ::finish)
        } }
    }
    override fun onResume() { super.onResume(); enabled.value = reader.isNfcEnabled; reader.enableReaderMode(this) }
    override fun onPause() { model.stop(); reader.disableReaderMode(this); super.onPause() }
}
