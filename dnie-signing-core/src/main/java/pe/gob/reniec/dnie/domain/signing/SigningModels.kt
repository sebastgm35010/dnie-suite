package pe.gob.reniec.dnie.domain.signing

import pe.gob.reniec.dnie.pdf.PdfSigningOptions
import java.io.File
import java.util.UUID

data class PdfPageSize(val width: Float, val height: Float)
data class SigningDocument(
    val id: String = UUID.randomUUID().toString(), val name: String,
    val file: File, val size: Long, val pages: List<PdfPageSize> = emptyList(),
    val options: PdfSigningOptions = PdfSigningOptions(), val detached: Boolean = false,
    val pdfInput: Boolean = false
) { val isPdf: Boolean get() = (pages.isNotEmpty() || pdfInput) && !detached }

enum class DocumentSignStatus { SIGNED, FAILED, INTERRUPTED }
data class SignedArtifact(
    val document: SigningDocument, val status: DocumentSignStatus,
    val output: File? = null, val certificate: ByteArray? = null,
    val signerName: String = "", val signerDni: String = "",
    val timestamp: Long = 0, val signatureSize: Int = 0, val message: String? = null
)
data class BatchSignResult(val artifacts: List<SignedArtifact>, val sessionError: String? = null)

data class HandwrittenMark(val png: ByteArray, val sourceName: String, val personalNumber: String? = null)
