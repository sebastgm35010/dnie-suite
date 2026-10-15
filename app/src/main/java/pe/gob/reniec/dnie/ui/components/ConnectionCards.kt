package pe.gob.reniec.dnie.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.data.preferences.CanStorage
import pe.gob.reniec.dnie.ui.design.AetherCard

/** Loads only the public CAN; a late disk read must not overwrite an edit or start a connection. */
@Composable
fun ConnectionCanField(can: String, onCan: (String) -> Unit, enabled: Boolean,
    readSavedCan: suspend (android.content.Context) -> String = CanStorage::loadSavedCan) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(can)
    val available by rememberUpdatedState(enabled)
    val update by rememberUpdatedState(onCan)
    var stored by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        stored = readSavedCan(context)
        if (available && stored.length == 6 && stored.all { it in '0'..'9' } && latest.isBlank() && !editing) update(stored)
    }
    if (can.length == 6 && !editing) SavedCanChip(can, saved = can == stored, enabled = enabled) { editing = true }
    else OutlinedTextField(can, { if (it.length <= 6 && it.all { c -> c in '0'..'9' }) { editing = true; onCan(it) } },
        label = { Text("CAN del DNI") }, singleLine = true, enabled = enabled,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().testTag("connection_can"))
}

/** Stored identity describes configuration; it never claims a live TLS or NFC session. */
@Composable
fun LinkedPcCard(address: String, fingerprint: String, tls: Boolean = true, actions: @Composable ColumnScope.() -> Unit = {}) {
    var details by remember(address, fingerprint) { mutableStateOf(false) }
    AetherCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PC vinculada", style = MaterialTheme.typography.titleMedium)
            if (tls && fingerprint.isNotBlank()) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                Text("TLS con huella fijada · al conectar", Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
                Text(address, Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            if (fingerprint.isNotBlank()) {
                TextButton(onClick = { details = !details }) { Text(if (details) "Ocultar huella" else "Comparar huella con la PC") }
                if (details) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
                        Text(fingerprint, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Compara esta huella con tu PC antes de autorizar.", style = MaterialTheme.typography.bodySmall)
                }
            }
            actions()
        }
    }
}
