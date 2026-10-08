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
    private var independent by mutableStateOf(false)
    private val qrLauncher by lazy { pe.gob.reniec.dnie.data.passkeys.FidoQrLauncher(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DnieTheme {
            var error by remember { mutableStateOf<String?>(null) }
            var qr by remember { mutableStateOf<pe.gob.reniec.dnie.data.passkeys.QrScannerFeedback?>(null) }
            Scaffold { padding -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Passkeys con DNIe", style = MaterialTheme.typography.headlineSmall)
                Text(if (independent) "Almacén independiente activo: una clave propia por sitio y huella para cada operación. Renovar el certificado del DNI no cambia estas passkeys." else "Una clave propia por sitio. Tu catálogo actual está cifrado para el DNI; puedes cambiar a un almacén independiente desde Protección y administración.")
                Text(if (independent) "Esta primera versión no tiene respaldo portable del almacén independiente. Mantén otro método de acceso en cada sitio; perder el teléfono o cambiar las huellas puede impedir abrirlo." else "El respaldo actual necesita la clave original del DNI. No recupera el catálogo si la renovación elimina esa clave. Mantén otro método de acceso en cada sitio.")
                if (Build.VERSION.SDK_INT >= 34) {
                    Button(onClick = {
                        try { CredentialManager.create(this@PasskeySettingsActivity).createSettingsPendingIntent().send() }
                        catch (_: Exception) { error = "No se pudo abrir el selector. Busca el proveedor DNIe en Contraseñas y passkeys de los ajustes de Android." }
                    }) { Text("Activar proveedor en Android") }
                    OutlinedButton(onClick = { startActivity(PasskeyProviderIntents.intent(this@PasskeySettingsActivity, "manage")) }) { Text("Mis passkeys") }
                    OutlinedButton(onClick = { startActivity(android.content.Intent(this@PasskeySettingsActivity, PasskeyToolsActivity::class.java)) }) {
                        Text("Protección y administración")
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
                    Text("Crea la passkey desde el sitio o app y elige DNIe. Para iniciar sesión, autoriza y elige la cuenta antes de 60 segundos.")
                } else Text("El proveedor necesita Android 14 o superior. La bóveda de archivos sigue disponible.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { finish() }) { Text("Volver") }
                pe.gob.reniec.dnie.development.DevelopmentEntry()
            } }
        } }
    }
    override fun onResume() {
        super.onResume()
        independent = pe.gob.reniec.dnie.passkeys.android.IndependentPasskeyVault(this).exists()
    }
}
