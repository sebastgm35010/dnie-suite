package pe.gob.reniec.dnie.ui.signer

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.domain.signing.*
import pe.gob.reniec.dnie.ui.components.DnieOperationPhase
import java.io.File

class SigningPresentationR8Test {
    private fun document(id: String) = SigningDocument(id = id, name = "$id.pdf", file = File("fixture/$id"), size = 0, sha256 = ByteArray(32))
    private val success = SignerUiState.SignedSuccess(null, null, "Synthetic", "", 0, 1)
    @Test fun batchWithOneSuccessAndOneFailureIsNotCompleted() {
        val a = document("a"); val b = document("b")
        val w = SigningWorkspace(documents = listOf(a, b), artifacts = listOf(SignedArtifact(a, DocumentSignStatus.SIGNED), SignedArtifact(b, DocumentSignStatus.FAILED)))
        assertFalse(w.allSigned()); assertEquals(DnieOperationPhase.ERROR, signingSheetPhase(success, w))
        assertEquals(listOf(b), w.pending)
    }
    @Test fun aDuplicateResultCannotHideAnUnsignedDocument() {
        val a = document("a"); val b = document("b"); val artifact = SignedArtifact(a, DocumentSignStatus.SIGNED)
        val w = SigningWorkspace(documents = listOf(a, b), artifacts = listOf(artifact, artifact))
        assertFalse(w.allSigned()); assertFalse(SigningWorkspace().allSigned())
    }
    @Test fun completedBatchMayOfferSafeRemoval() {
        val a = document("a"); val b = document("b")
        val w = SigningWorkspace(documents = listOf(a, b), artifacts = listOf(SignedArtifact(a, DocumentSignStatus.SIGNED), SignedArtifact(b, DocumentSignStatus.SIGNED)))
        assertEquals(DnieOperationPhase.COMPLETED, signingSheetPhase(success, w)); assertTrue(w.pending.isEmpty())
    }
    @Test fun signingNeverClosesSheetAndOnlyRealCountsProduceProgress() {
        assertEquals(DnieOperationPhase.TAG_ENGAGED, signingSheetPhase(SignerUiState.Signing(), SigningWorkspace()))
        assertNull(SignerUiState.Signing(total = 3).sheetProgress()) // PACE/TSA do not invent percent
        assertNull(SignerUiState.Signing(total = 1, indeterminate = false).sheetProgress())
        assertEquals(.5f, SignerUiState.Signing(completed = 1, total = 2, indeterminate = false).sheetProgress())
        assertEquals(1f, SignerUiState.Signing(completed = 4, total = 2, indeterminate = false).sheetProgress())
    }
}
