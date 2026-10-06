package pe.gob.reniec.dnie.ui.passkeys

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import pe.gob.reniec.dnie.passkeys.android.PasskeyProviderIntents
import pe.gob.reniec.dnie.ui.theme.DnieTheme

class PasskeySettingsActivity : ComponentActivity() {
    private val qrLauncher by lazy { pe.gob.reniec.dnie.data.passkeys.FidoQrLauncher(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DnieTheme {
            var error by remember { mutableStateOf<String?>(null) }
            var qr by remember { mutableStateOf<pe.gob.reniec.dnie.data.passkeys.QrScannerFeedback?>(null) }
            Scaffold { padding -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Passkeys con DNIe", style = MaterialTheme.typography.headlineSmall)
                Text("Una clave diferente para cada sitio, cifrada para tu DNI. Puedes autorizar este teléfono con biometría desde las herramientas; es opcional.")
                Text("ES256, respaldo cifrado y migración a otro DNI. La vigencia de cada certificado es independiente de la fecha de vencimiento del documento. Conserva un respaldo y otro método de acceso en cada sitio.")
                if (Build.VERSION.SDK_INT >= 34) {
                    Button(onClick = {
                        try { CredentialManager.create(this@PasskeySettingsActivity).createSettingsPendingIntent().send() }
                        catch (_: Exception) { error = "No se pudo abrir el selector. Busca el proveedor DNIe en Contraseñas y passkeys de los ajustes de Android." }
                    }) { Text("Activar proveedor en Android") }
                    OutlinedButton(onClick = { startActivity(PasskeyProviderIntents.intent(this@PasskeySettingsActivity, "manage")) }) { Text("Ver y gestionar con DNI") }
                    OutlinedButton(onClick = { startActivity(android.content.Intent(this@PasskeySettingsActivity, PasskeyToolsActivity::class.java)) }) {
                        Text("Respaldo, migración y biometría")
                    }
                    OutlinedButton(onClick = { qrLauncher.scan { qr = it } }, enabled = qr?.busy != true) {
                        Text("Escanear QR FIDO del navegador")
                    }
                    TextButton(onClick = { qrLauncher.camera { qr = it } }, enabled = qr?.busy != true) { Text("Usar cámara del teléfono") }
                    qr?.let { feedback ->
                        Text(feedback.message, color = if (feedback.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        if (feedback.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Text("QR experimental: escanea el código de ‘usar otro dispositivo’ en tu computadora. Android gestiona la conexión; puede ofrecer solo sus propios proveedores. Este lector no implementa un transporte híbrido propio para DNIe.")
                    Text("Después, crea la passkey desde el sitio o app y elige DNIe. Para iniciar sesión, autoriza con DNI y PIN una vez y elige la cuenta antes de 60 segundos.")
                } else Text("El proveedor necesita Android 14 o superior. La bóveda de archivos sigue disponible.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { finish() }) { Text("Volver") }
                TextButton(onClick = { startActivity(android.content.Intent(this@PasskeySettingsActivity,
                    pe.gob.reniec.dnie.ui.diagnostics.DiagnosticsActivity::class.java)) }) { Text("Diagnóstico persistente") }
            } }
        } }
    }
}
