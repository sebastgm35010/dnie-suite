package pe.gob.reniec.dnie.ui.components

import pe.gob.reniec.dnie.data.preferences.CanStorage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Diálogo unificado de solicitud previa de credenciales (CAN y/o PIN) antes del escaneo NFC.
 *
 * Solicita en pantalla:
 * 1. CAN (Card Access Number): 6 dígitos numéricos impresos en el frontal del DNIe 3.0
 *    con opción para recordar en el dispositivo.
 * 2. PIN: 6 dígitos secretos del titular (para operaciones de firma o lectura protegida),
 *    con advertencia crítica de límite de 3 intentos antes del bloqueo de chip.
 */
@Composable
fun CredentialsDialog(
    show: Boolean,
    title: String = "Credenciales del DNIe",
    subtitle: String? = null,
    requireCan: Boolean = true,
    requirePin: Boolean = false,
    initialCan: String = "",
    onConfirm: (can: String, pin: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    var canText by remember(show) { mutableStateOf(initialCan) }
    var rememberCan by remember(show) { mutableStateOf(initialCan.isNotBlank()) }
    var loaded by remember(show) { mutableStateOf(false) }
    var canEdited by remember(show) { mutableStateOf(false) }
    var rememberEdited by remember(show) { mutableStateOf(false) }
    LaunchedEffect(show) {
        val savedCan = CanStorage.loadSavedCan(context)
        if (!canEdited && initialCan.isBlank()) canText = savedCan
        if (!rememberEdited) rememberCan = savedCan.isNotBlank() || initialCan.isNotBlank()
        loaded = true
    }

    var pinText by remember(show) { mutableStateOf("") }
    var passwordVisible by remember(show) { mutableStateOf(false) }

    val isCanValid = !requireCan || (canText.length == 6 && canText.all { it.isDigit() })
    val isPinValid = !requirePin || (pinText.length in 4..8 && pinText.all { it.isDigit() })
    val isValid = loaded && isCanValid && isPinValid

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (requirePin) Icons.Default.Lock else Icons.Default.CreditCard,
                contentDescription = "Credenciales DNIe",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Campo CAN (6 dígitos)
                if (requireCan) {
                    OutlinedTextField(
                        value = canText,
                        onValueChange = { input ->
                            if (input.length <= 6 && input.all { it.isDigit() }) {
                                canText = input
                                canEdited = true
                            }
                        },
                        label = { Text("CAN (6 dígitos)") },
                        placeholder = { Text("Ej. 123456") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CreditCard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            Text(
                                text = if (canText.isEmpty()) {
                                    "6 dígitos en el frente inferior de su DNIe"
                                } else {
                                    "${canText.length}/6 dígitos"
                                }
                            )
                        },
                        isError = canText.isNotEmpty() && canText.length != 6,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Casilla para recordar CAN en este dispositivo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rememberEdited = true; rememberCan = !rememberCan }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberCan,
                            onCheckedChange = { rememberEdited = true; rememberCan = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recordar CAN en este dispositivo",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Sección PIN si es requerida (operaciones de firma o autenticación)
                if (requirePin) {
                    // Banner crítico de advertencia de bloqueo
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alerta de bloqueo",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "¡ATENCIÓN! MÁXIMO 3 INTENTOS",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Si ingresa un PIN incorrecto 3 veces consecutivas, el chip del DNIe se bloqueará (error 69 83). Desbloquearlo requiere acudir presencialmente a una oficina de RENIEC con el código PUK.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Campo numérico de ingreso de PIN
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { input ->
                            if (input.length <= 8 && input.all { it.isDigit() }) {
                                pinText = input
                            }
                        },
                        label = { Text("PIN de Firma (6 dígitos)") },
                        placeholder = { Text("••••••") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Ocultar PIN" else "Mostrar PIN"
                                )
                            }
                        },
                        supportingText = {
                            Text(
                                text = if (pinText.isEmpty()) {
                                    "PIN de 6 dígitos del titular"
                                } else {
                                    "${pinText.length} dígitos ingresados"
                                }
                            )
                        },
                        isError = pinText.isNotEmpty() && pinText.length < 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        if (requireCan) {
                            if (rememberCan) {
                                CanStorage.saveCan(context, canText)
                            } else {
                                CanStorage.clearCan(context)
                            }
                        }
                        onConfirm(canText, pinText)
                    }
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Continuar a lectura NFC")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
