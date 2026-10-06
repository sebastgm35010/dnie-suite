package pe.gob.reniec.dnie.ui.vault

import pe.gob.reniec.dnie.domain.vault.VaultPreviewPolicy
import pe.gob.reniec.dnie.vault.*

/** Presentation mapping prepared off Main, once per repository refresh. No disk or card access. */
internal class VaultLibrarySnapshot(items: List<VaultCatalogItem>) {
    private val index = VaultInventoryIndex(items)
    private val cards = items.associate { item ->
        val file = item.file
        val labels = item.labels
        file.id to VaultItemUi(file.id, file.encryptedBytes, file.displayName, file.createdAt,
            labels.folder, labels.tags.toList(), labels.trashed,
            VaultPreviewPolicy.canOffer(file.displayName, labels.mimeHint, file.encryptedBytes))
    }
    val totalItems get() = index.activeCount
    val folders get() = index.folders
    fun select(query: String, folder: String?, trash: Boolean, sort: VaultSort) =
        index.select(query, folder, trash, sort).map { cards.getValue(it.file.id) }
}
