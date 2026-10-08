package pe.gob.reniec.dnie.ui.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Credentials only; no IO, cryptography or file management controls. */
@Composable
internal fun VaultAccessPanel(state: VaultUiState, onCanChange: (String) -> Unit,
    onPinChange: (String) -> Unit, onRememberCan: (Boolean) -> Unit,
    onRun: () -> Unit, onStartOpen: () -> Unit, onClearSelection: () -> Unit,
    onCancel: () -> Unit) {
    val busy = state.awaitingCard || state.running
    val credentialsValid = state.can.length == 6 && state.pin.length in 4..8
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(if (state.selectedItemId == null) "VINCULAR O USAR DNI" else "RECUPERAR TU ARCHIVO")
        Text(if (state.selectedItemId == null)
            "Vincula el certificado de cifrado y comprueba su clave privada por NFC. Después podrás guardar archivos sin acercar el DNI."
        else if (state.previewMode) "Introduce CAN y PIN para ver el archivo dentro de la app. La vista se cerrará al salir."
        else "Destino elegido. Introduce CAN y PIN; después acerca el DNI para descifrar la copia.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.selectedItemId?.let { id ->
            Text("Archivo ${id.take(12)}…", fontFamily = FontFamily.Monospace,
                color = VaultAccent, style = MaterialTheme.typography.bodySmall)
            if (!state.previewMode) Text("La copia de destino quedará sin cifrar y fuera de la protección de la bóveda.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VaultCredentialFields(state, onCanChange, onPinChange, onRememberCan)
        if (!busy) {
            Button(onClick = if (state.selectedItemId == null) onRun else onStartOpen,
                enabled = credentialsValid && (state.selectedItemId == null || state.riskAcknowledged),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Nfc, null)
                Spacer(Modifier.width(10.dp))
                Text(when {
                    state.selectedItemId != null -> "Descifrar con DNI"
                    state.pairedFingerprint != null -> "Volver a vincular DNI"
                    else -> "Vincular DNI"
                })
            }
            if (state.selectedItemId != null) {
                TextButton(onClick = onClearSelection, modifier = Modifier.fillMaxWidth()) {
                    Text("Descartar destino elegido")
                }
            }
        }
    }
}

/** Reusable credential form; does not start a probe or any operation. */
@Composable
internal fun VaultCredentialFields(state: VaultUiState, onCanChange: (String) -> Unit,
    onPinChange: (String) -> Unit, onRememberCan: (Boolean) -> Unit) {
    val busy = state.running || state.awaitingCard
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = state.can, onValueChange = onCanChange,
            label = { Text("CAN") }, supportingText = { Text("6 dígitos impresos en el DNI") },
            singleLine = true, enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp))
        OutlinedTextField(value = state.pin, onValueChange = onPinChange,
            label = { Text("PIN del DNI") }, supportingText = { Text("4 a 8 dígitos. Sin reintentos automáticos.") },
            singleLine = true, enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp))
        Row {
            Checkbox(checked = state.rememberCan, onCheckedChange = onRememberCan, enabled = !busy)
            Text("Recordar CAN · compartido con la pantalla principal", modifier = Modifier.padding(top = 12.dp))
        }
    }
}
