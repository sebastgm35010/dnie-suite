package pe.gob.reniec.dnie.ui.vault

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun VaultToolsPanel(enabled: Boolean, onBackup: () -> Unit, onImport: () -> Unit,
    onNote: () -> Unit, canCreate: Boolean, onPasskeys: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Respaldo", style = MaterialTheme.typography.titleMedium)
        Text("Copia completa de archivos cifrados, etiquetas y papelera. El paquete de recuperación se guarda por separado.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onBackup, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Guardar respaldo completo") }
        OutlinedButton(onClick = onImport, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Importar respaldo") }
        HorizontalDivider()
        OutlinedButton(onClick = onNote, enabled = enabled && canCreate, modifier = Modifier.fillMaxWidth()) { Text("Nueva nota cifrada") }
        HorizontalDivider()
        Text("Passkeys", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onPasskeys, enabled = enabled) { Text("Abrir configuración de passkeys") }
    }
}
