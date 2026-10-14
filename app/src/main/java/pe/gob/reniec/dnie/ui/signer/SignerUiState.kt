package pe.gob.reniec.dnie.ui.signer

import android.net.Uri
import pe.gob.reniec.dnie.pdf.SignaturePreset

/**
 * Estados de la máquina de estados del Firmador PAdES en 2 pasos.
 */
sealed interface SignerUiState {
    data object NoDocument : SignerUiState

    data class DocumentLoaded(
        val documentUri: Uri? = null,
        val documentName: String,
        val documentSizeBytes: Long,
        val pageCount: Int,
        val preset: SignaturePreset = SignaturePreset.BOTTOM_RIGHT,
        val reason: String = "Soy el autor del documento",
        val location: String = "Lima, Perú"
    ) : SignerUiState

    data class EnteringPin(
        val documentUri: Uri? = null,
        val documentName: String,
        val documentSizeBytes: Long,
        val pageCount: Int,
        val preset: SignaturePreset,
        val reason: String,
        val location: String,
        val initialCan: String = ""
    ) : SignerUiState

    data class AwaitingCard(
        val documentUri: Uri? = null,
        val documentName: String,
        val documentSizeBytes: Long,
        val pageCount: Int,
        val preset: SignaturePreset,
        val reason: String,
        val location: String,
        val pin: String,
        val can: String? = null
    ) : SignerUiState

    data class Signing(
        val message: String = "Verificando PIN y generando firma PAdES...",
        val completed: Int = 0, val total: Int = 1, val indeterminate: Boolean = true
    ) : SignerUiState

    data class SignedSuccess(
        val signedFileUri: Uri?,
        val signedFilePath: String?,
        val signerName: String,
        val signerDni: String,
        val timestamp: Long,
        val signatureSize: Int
    ) : SignerUiState

    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : SignerUiState
}
