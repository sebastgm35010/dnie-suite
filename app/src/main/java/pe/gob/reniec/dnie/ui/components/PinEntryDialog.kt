package pe.gob.reniec.dnie.ui.components

import androidx.compose.runtime.Composable

/**
 * Diálogo para la captura del PIN del DNIe.
 * Delega a [CredentialsDialog] para soportar validación unificada y advertencias de seguridad.
 */
@Composable
fun PinEntryDialog(
    show: Boolean,
    title: String = "Ingrese PIN de su DNIe",
    requireCan: Boolean = false,
    requirePin: Boolean = true,
    initialCan: String = "",
    onConfirm: (pin: String) -> Unit,
    onConfirmWithCan: ((can: String, pin: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    CredentialsDialog(
        show = show,
        title = title,
        requireCan = requireCan,
        requirePin = requirePin,
        initialCan = initialCan,
        onConfirm = { can, pin ->
            if (onConfirmWithCan != null) {
                onConfirmWithCan(can, pin)
            } else {
                onConfirm(pin)
            }
        },
        onDismiss = onDismiss
    )
}
