package pe.gob.reniec.dnie.ui.components

import androidx.compose.runtime.Composable

enum class NfcSheetPhase { AWAITING_TAG, TAG_ENGAGED, COMPLETED, ERROR }

/** Compatibility entry point for features with their own credential/authorization flow. */
@Composable
fun NfcTapPromptSheet(isShowing: Boolean,
    statusMessage: String = "Mantén el DNI sobre el sensor NFC.",
    phase: NfcSheetPhase = NfcSheetPhase.AWAITING_TAG, progress: Float? = null,
    onCancel: () -> Unit) {
    DnieOperationSheet(isShowing, DnieOperationPhase.valueOf(phase.name), "Lectura del DNIe",
        statusMessage, progress, onDismiss = onCancel)
}
