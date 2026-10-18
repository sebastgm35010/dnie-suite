package pe.gob.reniec.dnie.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import pe.gob.reniec.dnie.data.preferences.CanStorage
import pe.gob.reniec.dnie.ui.design.*

/** Legacy call sites share the same sheet and form as continuous operations. */
@Composable
fun CredentialsDialog(show: Boolean, title: String = "Credenciales del DNIe", subtitle: String? = null,
    requireCan: Boolean = true, requirePin: Boolean = false, initialCan: String = "",
    onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    DnieOperationSheet(show, DnieOperationPhase.CREDENTIALS, title, onDismiss = onDismiss,
        credentials = { CredentialsForm(subtitle, requireCan, requirePin, initialCan, onConfirm, onDismiss) })
}

@Composable
fun SavedCanChip(can: String, saved: Boolean = true, enabled: Boolean = true, onChange: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text((if (saved) "CAN guardado: ••••" else "CAN de esta sesión: ••••") + can.takeLast(2), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onChange, enabled = enabled) { Text("Cambiar") }
        }
    }
}

/** Visual count only; the editable field remains the accessible source of truth. */
@Composable
fun CredentialDigitsIndicator(length: Int, minTarget: Int = 4, maxTarget: Int = 8, completeAt: Int = maxTarget) {
    val count = if (minTarget == maxTarget) maxTarget else maxTarget
    val reduced = pe.gob.reniec.dnie.ui.design.rememberReducedMotion()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(count) { index ->
            val filled = index < length
            val scaleState = androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (filled) 1f else .82f,
                animationSpec = pe.gob.reniec.dnie.ui.design.aetherSpring(reduced),
                label = "credential_digit_scale"
            )
            val targetColor = when {
                !filled -> MaterialTheme.colorScheme.surfaceContainerHigh
                length >= completeAt -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
            }
            val colorState = androidx.compose.animation.animateColorAsState(
                targetValue = targetColor,
                animationSpec = pe.gob.reniec.dnie.ui.design.aetherSpring(reduced),
                label = "credential_digit_color"
            )
            Box(
                Modifier
                    .size(width = 22.dp, height = 6.dp)
                    .graphicsLayer { scaleX = scaleState.value; scaleY = scaleState.value }
                    .drawBehind {
                        drawRoundRect(
                            color = colorState.value,
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                    }
            )
        }
    }
}

/** Credentials are transient, never saveable. Clear PIN on pause, confirm, dismissal and disposal. */
@Composable
fun CredentialsForm(subtitle: String? = null, requireCan: Boolean = true, requirePin: Boolean = false,
    initialCan: String = "", onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var can by remember { mutableStateOf(initialCan) }
    var pin by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var remembered by remember { mutableStateOf(false) }
    var editingCan by remember { mutableStateOf(true) }
    var visible by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val saved = CanStorage.loadSavedCan(context)
        if (can.isBlank()) can = saved
        remembered = saved.isNotBlank() && saved == can
        editingCan = can.length != 6
        loaded = true
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) { pin = ""; visible = false } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); pin = "" }
    }
    LaunchedEffect(loaded, editingCan, requirePin) { if (loaded && requirePin && (!requireCan || !editingCan)) focus.requestFocus() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (requireCan) {
            Box(Modifier.fillMaxWidth()) {
                if (!editingCan) SavedCanChip(can, saved = remembered) { editingCan = true }
                else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(can, { if (it.length <= 6 && it.all { c -> c in '0'..'9' }) can = it },
                            enabled = loaded, textStyle = MaterialTheme.typography.bodyLarge, label = { Text("CAN (6 dígitos)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        CredentialDigitsIndicator(can.length, minTarget = 6, maxTarget = 6, completeAt = 6)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(remembered, { remembered = it })
                            Text("Recordar CAN en este dispositivo", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (requirePin) {
            OutlinedTextField(pin, { if (it.length <= 8 && it.all { c -> c in '0'..'9' }) pin = it },
                textStyle = MaterialTheme.typography.bodyLarge, label = { Text("PIN de firma") }, singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                trailingIcon = { IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Ocultar PIN" else "Mostrar PIN")
                } }, modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("operation_pin"))
            CredentialDigitsIndicator(pin.length, minTarget = 4, maxTarget = 8)
            Text("Usa el PIN que configuraste. Los intentos incorrectos consumen los intentos del chip y pueden bloquearlo.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(enabled = loaded && (!requireCan || can.length == 6) && (!requirePin || pin.length in 4..8),
            modifier = Modifier.fillMaxWidth(), onClick = {
                if (requireCan) { if (remembered) CanStorage.saveCan(context, can) else CanStorage.clearCan(context) }
                val submitted = pin; pin = ""; visible = false
                onConfirm(can, submitted)
            }) { Text("Continuar a lectura NFC") }
        TextButton(onClick = { pin = ""; onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}
