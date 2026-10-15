package pe.gob.reniec.dnie.ui.components

import pe.gob.reniec.dnie.ui.design.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class DnieOperationPhase { CREDENTIALS, AWAITING_TAG, TAG_ENGAGED, COMPLETED, ERROR }

/** Presentation only: transitions follow the owner's state, never timers or inferred APDU text. */
@Composable
fun DnieOperationSheet(
    show: Boolean, phase: DnieOperationPhase, title: String,
    message: String? = null, progress: Float? = null,
    onDismiss: () -> Unit, onRetry: (() -> Unit)? = null, credentials: @Composable () -> Unit = {},
    terminalActions: @Composable () -> Unit = {}
) {
    if (!show) return
    val reducedMotion = rememberReducedMotion()
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.testTag("dnie_operation_sheet")) {
        AnimatedContent(targetState = phase, transitionSpec = { aetherContentTransition(reducedMotion, targetState == DnieOperationPhase.COMPLETED) }, label = "dnie-operation-phase") { current ->
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                if (current == DnieOperationPhase.CREDENTIALS) credentials()
                else {
                    val terminal = current == DnieOperationPhase.COMPLETED || current == DnieOperationPhase.ERROR
                    NfcOperationVisual(current, reducedMotion)
                    Text(when (current) {
                        DnieOperationPhase.AWAITING_TAG -> "Acerca tu DNIe al reverso del teléfono"
                        DnieOperationPhase.TAG_ENGAGED -> "DNIe detectado · mantenlo inmóvil"
                        DnieOperationPhase.COMPLETED -> "¡Listo! Ya puedes retirar tu DNIe."
                        else -> "La operación terminó con incidencias"
                    }, Modifier.testTag("nfc_phase_" + current.name.lowercase()), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    message?.let { Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium) }
                    if (!terminal) {
                        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("No retires el DNI hasta que finalice la operación.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                    if (terminal) terminalActions()
                    if (current == DnieOperationPhase.ERROR && onRetry != null) Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Reintentar con nueva autorización") }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(if (terminal) "Volver" else "Cancelar") }
                }
            }
        }
    }
}
