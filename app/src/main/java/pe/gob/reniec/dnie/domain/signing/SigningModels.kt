package pe.gob.reniec.dnie.domain.signing

import pe.gob.reniec.dnie.pdf.PdfSigningOptions
import java.io.File
import java.util.UUID

data class PdfPageSize(val width: Float, val height: Float)
data class SigningDocument(
    val id: String = UUID.randomUUID().toString(), val name: String,
    val file: File, val size: Long, val pages: List<PdfPageSize> = emptyList(),
    val options: PdfSigningOptions = PdfSigningOptions(), val detached: Boolean = false
) { val isPdf: Boolean get() = pages.isNotEmpty() && !detached }

enum class DocumentSignStatus { SIGNED, FAILED, INTERRUPTED }
data class SignedArtifact(
    val document: SigningDocument, val status: DocumentSignStatus,
    val output: File? = null, val certificate: ByteArray? = null,
    val signerName: String = "", val signerDni: String = "",
    val timestamp: Long = 0, val signatureSize: Int = 0, val message: String? = null
)
data class BatchSignResult(val artifacts: List<SignedArtifact>, val sessionError: String? = null)

data class HandwrittenMark(val png: ByteArray, val sourceName: String, val personalNumber: String? = null)
data class SigningWorkspace(
    val documents: List<SigningDocument> = emptyList(), val selectedId: String? = null,
    val artifacts: List<SignedArtifact> = emptyList(), val importing: Boolean = false,
    val importErrors: List<String> = emptyList(), val mark: HandwrittenMark? = null,
    val markError: String? = null, val previewName: String = "Nombre del titular", val previewDni: String = "",
    val certificate: ByteArray? = null, val exporting: Boolean = false, val exportMessage: String? = null,
    val markConfirmed: Boolean = false
) {
    val selected: SigningDocument? get() = documents.firstOrNull { it.id == selectedId }
    val pending: List<SigningDocument> get() = documents.filter { doc -> artifacts.none { it.document.id == doc.id && it.status == DocumentSignStatus.SIGNED } }
}
