package pe.gob.reniec.dnie.ui.vault

/** UI snapshot. Credentials and decrypted metadata must never enter saved-state or preferences. */
data class VaultItemUi(val id: String, val encryptedBytes: Long, val displayName: String? = null,
    val createdAt: Long = 0, val folder: String = "", val tags: List<String> = emptyList(), val trashed: Boolean = false,
    val canPreview: Boolean = false)

data class VaultUiState(
    val can: String = "",
    val pin: String = "",
    val rememberCan: Boolean = false,
    val awaitingCard: Boolean = false,
    val running: Boolean = false,
    val message: String? = null,
    val certificateFingerprint: String? = null,
    val verified: Boolean = false,
    val pairedFingerprint: String? = null,
    val items: List<VaultItemUi> = emptyList(),
    val selectedItemId: String? = null,
    val riskAcknowledged: Boolean = false,
    val restoredName: String? = null,
    val restoredNameApplied: Boolean? = null,
    val query: String = "",
    val folder: String? = null,
    val folders: List<String> = emptyList(),
    val sort: pe.gob.reniec.dnie.vault.VaultSort = pe.gob.reniec.dnie.vault.VaultSort.NEWEST,
    val showTrash: Boolean = false,
    val totalItems: Int = 0,
    val previewMode: Boolean = false,
    val preview: pe.gob.reniec.dnie.data.vault.VaultPreviewTicket? = null,
    val incomingFiles: Int = 0,
    val details: pe.gob.reniec.dnie.domain.vault.VaultFileDetails? = null
) {
    override fun toString(): String = "VaultUiState(credentials=redacted, awaitingCard=$awaitingCard, running=$running, verified=$verified)"
}
