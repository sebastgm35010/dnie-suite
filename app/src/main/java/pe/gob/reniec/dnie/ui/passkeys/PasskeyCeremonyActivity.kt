package pe.gob.reniec.dnie.ui.passkeys

import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import pe.gob.reniec.dnie.core.nfc.NfcReaderManager
import pe.gob.reniec.dnie.ui.theme.DnieTheme

@RequiresApi(34)
class PasskeyCeremonyActivity : androidx.fragment.app.FragmentActivity() {
    private val model: PasskeyCeremonyViewModel by viewModels()
    private lateinit var reader: NfcReaderManager
    private var biometricPrompt: androidx.biometric.BiometricPrompt? = null
    private var frameDiagnostics: pe.gob.reniec.dnie.diagnostics.UiFrameDiagnostics? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT < 34) { finish(); return }
        if (pe.gob.reniec.dnie.BuildConfig.DEBUG) frameDiagnostics = pe.gob.reniec.dnie.diagnostics.UiFrameDiagnostics(window).also { it.onRoute("passkeys") }
        reader = NfcReaderManager(NfcAdapter.getDefaultAdapter(this))
        lifecycleScope.launch { reader.discoveredTags.collect { model.tag(it) } }
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.biometricCipher) { state.biometricCipher?.let { cipher ->
                biometricPrompt = androidx.biometric.BiometricPrompt(this@PasskeyCeremonyActivity,
                    androidx.core.content.ContextCompat.getMainExecutor(this@PasskeyCeremonyActivity),
                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                            result.cryptoObject?.cipher?.let(model::biometric) ?: model.biometricCancelled()
                        }
                        override fun onAuthenticationError(code: Int, message: CharSequence) { model.biometricCancelled() }
                    }).also { prompt -> prompt.authenticate(androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Autorizar passkey").setSubtitle("Verifica tu identidad en este teléfono")
                        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .setNegativeButtonText(if (state.independent) "Cancelar" else "Usar DNI").build(), androidx.biometric.BiometricPrompt.CryptoObject(cipher)) }
            } }
            LaunchedEffect(state.result) { state.result?.let { setResult(RESULT_OK, it); finish() } }
            DnieTheme { PasskeyCeremonyScreen(state, model::can, model::pin,
                onAuthorize = { if (reader.isNfcEnabled) { model.request(); reader.enableReaderMode(this) } },
                onDelete = { if (state.independent || reader.isNfcEnabled) model.request(it) }, onCancel = { finish() }, nfcEnabled = reader.isNfcEnabled,
                onBiometric = model::requestBiometric) }
        }
    }
    override fun onResume() { super.onResume(); if (::reader.isInitialized) {
        model.initialize(intent)
        // A selected account normally completes through its existing grant, without NFC.
        if (intent.getStringExtra(pe.gob.reniec.dnie.passkeys.android.PasskeyProviderIntents.MODE) != "get" ||
            intent.getStringExtra(pe.gob.reniec.dnie.passkeys.android.PasskeyProviderIntents.AUTHORIZATION) == null) reader.enableReaderMode(this)
    } }
    override fun onDestroy() { frameDiagnostics?.close(); frameDiagnostics = null; super.onDestroy() }
    override fun onPause() { biometricPrompt?.cancelAuthentication(); if (::reader.isInitialized) { reader.disableReaderMode(this); model.background() }; super.onPause() }
}
