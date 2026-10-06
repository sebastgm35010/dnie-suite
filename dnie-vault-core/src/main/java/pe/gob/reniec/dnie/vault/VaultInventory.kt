package pe.gob.reniec.dnie.vault

/** Immutable public-metadata snapshot. Build once per inventory change, reuse during typing. */
class VaultInventoryIndex(items: List<VaultCatalogItem>) {
    private val source = items.map { it.copy(labels = it.labels.copy(tags = it.labels.tags.toList())) }
    private val ordered = VaultSort.entries.associateWith { sort -> VaultInventory.order(source, sort) }
    val activeCount = source.count { !it.labels.trashed }
    val folders = source.map { it.labels.folder }.filter(String::isNotBlank).distinct().sorted()

    fun select(query: String, folder: String?, trash: Boolean, sort: VaultSort): List<VaultCatalogItem> =
        VaultInventory.filter(ordered.getValue(sort), query, folder, trash)
}

/** Search/sort semantics shared by one-shot callers and cached snapshots; no Android dependency. */
object VaultInventory {
    private val whitespace = Regex("\\s+")
    fun select(items: List<VaultCatalogItem>, query: String, folder: String?, trash: Boolean,
               sort: VaultSort): List<VaultCatalogItem> = order(filter(items, query, folder, trash), sort)

    internal fun filter(items: List<VaultCatalogItem>, query: String, folder: String?, trash: Boolean): List<VaultCatalogItem> {
        val terms = query.trim().split(whitespace).filter(String::isNotEmpty)
        return items.filter { item ->
            item.labels.trashed == trash && (folder == null || item.labels.folder == folder) && terms.all { term ->
                item.file.displayName.orEmpty().contains(term, ignoreCase = true) ||
                    item.labels.folder.contains(term, ignoreCase = true) ||
                    item.labels.tags.any { it.contains(term, ignoreCase = true) }
            }
        }
    }

    internal fun order(items: List<VaultCatalogItem>, sort: VaultSort): List<VaultCatalogItem> = when (sort) {
        VaultSort.NEWEST -> items.sortedByDescending { it.file.createdAt }
        VaultSort.NAME -> items.sortedBy { it.file.displayName.orEmpty().lowercase(java.util.Locale.ROOT) }
        VaultSort.SIZE -> items.sortedByDescending { it.file.encryptedBytes }
    }
}
