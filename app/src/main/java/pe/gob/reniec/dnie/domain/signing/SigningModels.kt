package pe.gob.reniec.dnie.domain.signing

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
