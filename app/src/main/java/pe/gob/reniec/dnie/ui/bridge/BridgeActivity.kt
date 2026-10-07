package pe.gob.reniec.dnie.ui.bridge

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
import pe.gob.reniec.dnie.ui.theme.DnieTheme
import pe.gob.reniec.dnie.data.bridge.BridgeQrScanner

/** A separate foreground NFC owner. MainActivity releases reader mode before we resume. */
class BridgeActivity : ComponentActivity() {
    private val model: BridgeViewModel by viewModels()
    private lateinit var reader: NfcReaderManager
    private val nfcEnabled = mutableStateOf(false)
    private lateinit var usb: pe.gob.reniec.dnie.data.bridge.BridgeUsbPermission
    private lateinit var scanner: BridgeQrScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        reader = NfcReaderManager(NfcAdapter.getDefaultAdapter(this))
        scanner = BridgeQrScanner(this)
        usb = pe.gob.reniec.dnie.data.bridge.BridgeUsbPermission(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { reader.discoveredTags.collect(model::tag) }
        }
        setContent { DnieTheme {
            val state by model.state.collectAsStateWithLifecycle()
            val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::import) }
            val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(model::exportTrace) }
            BridgeScreen(state, nfcEnabled.value,
                onImport = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onScan = { scanner.scan(model::importQr, model::scanStatus) },
                onCan = model::can, onAuthorize = { if(state.method == pe.gob.reniec.dnie.bridge.BridgeConnectionMethod.USB_NETWORK) usb.authorize(model::authorize,model::scanStatus) else model.authorize() }, onStop = model::stop, onBack = ::finish,
                onExportTrace = { exporter.launch("dnie-apdu-pace-android.jsonl") }, onClearTrace = model::clearTrace, onWindowsPace = model::windowsPace, onTranslatePace = model::translatePace,
                onTailnetHost = model::tailnetHost, onConnectTailnet = model::connectTailnet,
                onMethod = model::method, onSettings = model::settings,
                onForgetPc = model::forgetPc,
                development = pe.gob.reniec.dnie.BuildConfig.DEBUG && intent.getBooleanExtra("development", false))
        } }
    }

    override fun onDestroy() { usb.close(); super.onDestroy() }

    override fun onResume() {
        super.onResume()
        nfcEnabled.value = reader.isNfcEnabled
        reader.enableReaderMode(this)
    }

    override fun onPause() {
        model.stop()
        reader.disableReaderMode(this)
        super.onPause()
    }
}
