package pe.gob.reniec.dnie.domain.signing

import pe.gob.reniec.dnie.pdf.PdfSigningOptions
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class PdfPageSize(val width: Float, val height: Float)
data class SigningDocument(
    val id: String = UUID.randomUUID().toString(), val name: String,
    val file: File, val size: Long, val pages: List<PdfPageSize> = emptyList(),
    val options: PdfSigningOptions = PdfSigningOptions(), val detached: Boolean = false,
    val pdfInput: Boolean = false, val sha256: ByteArray = sha256Of(file)
) {
    init { require(sha256.size == 32) { "El documento requiere un resumen SHA-256 de 32 bytes" } }
    val isPdf: Boolean get() = (pages.isNotEmpty() || pdfInput) && !detached
    companion object {
        fun sha256Of(file: File): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            if (file.isFile) {
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
            }
            return digest.digest()
        }
    }
}

enum class DocumentSignStatus { SIGNED, FAILED, INTERRUPTED }
data class SignedArtifact(
    val document: SigningDocument, val status: DocumentSignStatus,
    val output: File? = null, val certificate: ByteArray? = null,
    val signerName: String = "", val signerDni: String = "",
    val timestamp: Long = 0, val signatureSize: Int = 0, val message: String? = null,
    val outputSize: Long = output?.takeIf { it.isFile }?.length() ?: 0L,
    val outputSha256: ByteArray? = output?.takeIf { it.isFile }?.let(SigningDocument::sha256Of)
) {
    init {
        require(output == null || !output.isFile || (outputSha256 != null && outputSha256.size == 32)) {
            "El resultado firmado requiere un resumen SHA-256 de 32 bytes"
        }
    }
}
data class BatchSignResult(val artifacts: List<SignedArtifact>, val sessionError: String? = null)

data class HandwrittenMark(val png: ByteArray, val sourceName: String, val personalNumber: String? = null)
