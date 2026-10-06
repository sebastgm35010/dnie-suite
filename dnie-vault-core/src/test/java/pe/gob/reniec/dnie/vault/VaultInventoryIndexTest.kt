package pe.gob.reniec.dnie.vault

import org.junit.Assert.*
import org.junit.Test

class VaultInventoryIndexTest {
    private fun item(id: String, name: String?, folder: String, tags: List<String> = emptyList(),
                     trash: Boolean = false, size: Long = 3, date: Long = 1) =
        VaultCatalogItem(VaultStoredItem(id, size, name, date), VaultLabels(folder, tags, trash))

    @Test fun cachedOrderPreservesSearchSemanticsAndStableTiesAcrossAllFilters() {
        val items = listOf(item("a", "Contrato.pdf", "Trabajo", listOf("LEGAL")),
            item("b", "foto.jpg", "Personal", size = 8, date = 3),
            item("c", "Contrato.pdf", "Trabajo", listOf("legal")),
            item("d", null, "Trabajo", listOf("legal"), trash = true))
        val index = VaultInventoryIndex(items)
        for (query in listOf("", "  \t", "CONTRATO legal", "Trabajo legal", "no existe"))
            for (folder in listOf(null, "", "Trabajo", "Personal"))
                for (trash in listOf(false, true)) for (sort in VaultSort.entries)
                    assertEquals(VaultInventory.select(items, query, folder, trash, sort), index.select(query, folder, trash, sort))
        assertEquals(listOf("a", "c"), index.select("contrato legal", null, false, VaultSort.NAME).map { it.file.id })
        assertEquals(3, index.activeCount)
        assertEquals(listOf("Personal", "Trabajo"), index.folders)
    }

    @Test fun snapshotDoesNotObserveLaterMutationOfInputCollectionOrTags() {
        val tags = mutableListOf("legal")
        val items = mutableListOf(item("a", "Contrato.pdf", "Trabajo", tags))
        val index = VaultInventoryIndex(items)
        tags.clear(); items.clear()
        assertEquals(listOf("a"), index.select("legal", null, false, VaultSort.NAME).map { it.file.id })
        assertEquals(1, index.activeCount)
        assertTrue(VaultInventoryIndex(emptyList()).select("", null, false, VaultSort.NAME).isEmpty())
    }
}
