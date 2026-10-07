package pe.gob.reniec.dnie.desktop.model

import java.nio.file.Path
import pe.gob.reniec.dnie.domain.signing.DocumentSignStatus

data class DesktopSigningRequest(val inputs: List<Path>, val workspaceRoot: Path)
enum class SigningBackend { SIMULATED, PRIVATE_ANDROID }
data class DesktopDocumentResult(val id: String, val name: String, val status: DocumentSignStatus,
    val original: Path, val signature: Path?, val error: String?)
data class DesktopSigningResult(val jobId: String, val backend: SigningBackend, val directory: Path,
    val documents: List<DesktopDocumentResult>, val sessionError: String?) {
    val complete: Boolean get() = documents.isNotEmpty() && documents.all { it.status == DocumentSignStatus.SIGNED }
}
