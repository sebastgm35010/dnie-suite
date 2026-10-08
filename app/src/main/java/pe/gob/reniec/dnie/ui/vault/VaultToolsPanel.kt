package pe.gob.reniec.dnie.ui.vault

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun VaultToolsPanel(enabled: Boolean, onBackup: () -> Unit, onImport: () -> Unit,
    onNote: () -> Unit, canCreate: Boolean, onPasskeys: () -> Unit, onDni: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Copias de tus documentos", style = MaterialTheme.typography.titleMedium)
        Text("El ZIP incluye todos los archivos cifrados, etiquetas y papelera. Sirve para guardarlos fuera del teléfono o llevarlos a otro equipo. Para abrirlos necesitarás el DNI original o la protección con contraseña.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onBackup, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Guardar respaldo ZIP") }
        OutlinedButton(onClick = onImport, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Importar respaldo ZIP") }
        HorizontalDivider()
        TextButton(onClick = onDni, enabled = enabled) { Text("Vincular o cambiar DNI") }
        Text("Vincular permite cifrar nuevos archivos para ese DNI. No cambia la clave de los archivos anteriores; recupéralos primero desde Protección.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        OutlinedButton(onClick = onNote, enabled = enabled && canCreate, modifier = Modifier.fillMaxWidth()) { Text("Nueva nota cifrada") }
        HorizontalDivider()
        Text("Passkeys", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onPasskeys, enabled = enabled) { Text("Abrir configuración de passkeys") }
    }
}
