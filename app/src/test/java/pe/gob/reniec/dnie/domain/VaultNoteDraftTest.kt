package pe.gob.reniec.dnie.domain

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.domain.vault.VaultNoteDraft

class VaultNoteDraftTest {
    @Test fun noteUsesTextMimeAndKeepsExistingExtension() {
        assertEquals("text/plain", VaultNoteDraft.metadata("Recuperación", "privado").mime)
        assertEquals("Recuperación.txt", VaultNoteDraft.metadata("Recuperación", "privado").name)
        assertEquals("secreto.txt", VaultNoteDraft.metadata("secreto.txt", "privado").name)
    }
    @Test fun rejectsPathsEmptyNotesAndOversizedUtf8Body() {
        listOf({ VaultNoteDraft.metadata("../nota", "contenido") },
            { VaultNoteDraft.metadata("nota", " ") },
            { VaultNoteDraft.metadata("nota", "á".repeat(600000)) }).forEach { action ->
            try { action(); fail("Invalid note accepted") } catch (_: IllegalArgumentException) {}
        }
    }
}
