package pe.gob.reniec.dnie.ui.passkeys

import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import pe.gob.reniec.dnie.core.nfc.NfcReaderManager
import pe.gob.reniec.dnie.ui.theme.DnieTheme

class PasskeyToolsActivity : FragmentActivity() {
    private val model: PasskeyToolsViewModel by viewModels()
    private lateinit var reader: NfcReaderManager
    private var prompt: BiometricPrompt? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        reader = NfcReaderManager(NfcAdapter.getDefaultAdapter(this))
        lifecycleScope.launch { reader.discoveredTags.collect(model::tag) }
        setContent { DnieTheme {
            val s by model.state.collectAsStateWithLifecycle()
            val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(model::export) }
            val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::import) }
            LaunchedEffect(s.cipher) { s.cipher?.let { cipher ->
                prompt = BiometricPrompt(this@PasskeyToolsActivity, ContextCompat.getMainExecutor(this@PasskeyToolsActivity), object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { result.cryptoObject?.cipher?.let(model::biometric) ?: model.biometricCancelled() }
                    override fun onAuthenticationError(code: Int, message: CharSequence) { model.biometricCancelled() }
                })
                prompt?.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Habilitar passkeys en este teléfono")
                    .setSubtitle("Confirma tu huella. Los archivos de la bóveda seguirán requiriendo DNI.")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("Cancelar").build(), BiometricPrompt.CryptoObject(cipher))
            } }
            Scaffold { padding -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Configuración de passkeys", style = MaterialTheme.typography.headlineSmall)
                Text("El vencimiento del DNI y de sus certificados son independientes. Un nuevo certificado puede usar una clave diferente.")
                if (!s.independent) {
                OutlinedButton(onClick = model::independentEnrollment, enabled = !s.busy && s.action == null && BiometricManager.from(this@PasskeyToolsActivity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) { Text("Activar almacén independiente") }
                Text("Requiere autorización inicial con DNI y huella. No dispone aún de recuperación portable; mantén otro método de acceso a tus sitios.")
                Button(onClick = { export.launch("passkeys-dni.dnievault") }, enabled = !s.busy && s.action == null) { Text("Exportar respaldo cifrado") }
                OutlinedButton(onClick = { restore.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = !s.busy && s.action == null) { Text("Restaurar respaldo con DNI") }
                OutlinedButton(model::migration, enabled = !s.busy && s.action == null) { Text("Migrar a otro DNI / certificado") }
                if (s.previous) OutlinedButton(model::previous, enabled = !s.busy && s.action == null) { Text("Recuperar catálogo anterior en este teléfono") }
                Text("El respaldo portable incluye las passkeys creadas desde 1.7. Las anteriores conservan su política original y pueden migrarse a otro DNI en este teléfono, pero requieren un registro nuevo en el sitio para hacerse exportables.")
                Text("Migración: primero el DNI anterior y después el nuevo. Conserva un respaldo externo antes de comenzar.")
                HorizontalDivider()
                Row { Text("Usar huella en este teléfono", Modifier.weight(1f)); Switch(checked = s.biometric, onCheckedChange = { enable ->
                    if (!enable) model.disable()
                    else if (BiometricManager.from(this@PasskeyToolsActivity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) model.enrollment()
                }, enabled = !s.busy && s.action == null && (s.biometric || BiometricManager.from(this@PasskeyToolsActivity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS)) }
                Text("Opcional: DNI para habilitar; huella para iniciar sesión. Cambiar las huellas, restaurar, migrar o modificar el catálogo requiere volver a habilitarlo. La bóveda no usa esta opción.")
                } else {
                    Text("Almacén independiente activo", style = MaterialTheme.typography.titleMedium)
                    Text("Huella por operación. El DNI ya no cifra este catálogo. Respaldo portable y recuperación pendientes; las herramientas del catálogo antiguo están deshabilitadas.")
                }
                Text(s.message)
                if (s.action != null) {
                    OutlinedTextField(s.can, model::can, Modifier.fillMaxWidth(), label = { Text(if (s.newCard) "CAN del nuevo DNI" else "CAN") }, enabled = !s.busy && !s.awaiting, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    OutlinedTextField(s.pin, model::pin, Modifier.fillMaxWidth(), label = { Text("PIN del DNI") }, enabled = !s.busy && !s.awaiting, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    Button(model::request, enabled = !s.busy && !s.awaiting && s.cipher == null && s.can.length == 6 && s.pin.length in 4..8) { Text("Autorizar con DNI") }
                    TextButton(model::cancel) { Text("Cancelar operación") }
                }
                if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = { finish() }) { Text("Volver") }
            } }
        } }
    }
    override fun onResume() { super.onResume(); reader.enableReaderMode(this) }
    override fun onPause() { reader.disableReaderMode(this); prompt?.cancelAuthentication(); model.cancel(); super.onPause() }
}
