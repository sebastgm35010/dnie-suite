package pe.gob.reniec.dnie.ui.signer

import pe.gob.reniec.dnie.domain.signing.DocumentSignStatus
import pe.gob.reniec.dnie.domain.signing.SigningWorkspace
import pe.gob.reniec.dnie.ui.components.DnieOperationPhase

/** A successful artifact is not evidence that every document in a batch succeeded. */
internal fun SigningWorkspace.allSigned(): Boolean = documents.isNotEmpty() && documents.all { doc ->
    artifacts.any { it.document.id == doc.id && it.status == DocumentSignStatus.SIGNED }
}

internal fun signingSheetPhase(state: SignerUiState, workspace: SigningWorkspace): DnieOperationPhase? = when (state) {
    is SignerUiState.EnteringPin -> DnieOperationPhase.CREDENTIALS
    is SignerUiState.AwaitingCard -> DnieOperationPhase.AWAITING_TAG
    is SignerUiState.Signing -> DnieOperationPhase.TAG_ENGAGED
    is SignerUiState.SignedSuccess -> if (workspace.allSigned()) DnieOperationPhase.COMPLETED else DnieOperationPhase.ERROR
    is SignerUiState.Error -> DnieOperationPhase.ERROR
    else -> null
}

internal fun SignerUiState.Signing.sheetProgress(): Float? =
    if (indeterminate || total <= 1) null else (completed.toFloat() / total).coerceIn(0f, 1f)
