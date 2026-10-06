package pe.gob.reniec.dnie.domain

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.domain.vault.VaultPreviewPolicy

class VaultPreviewPolicyTest {
    @Test fun unsupportedFormatsAndUnknownNamesDoNotOfferPreview() {
        listOf("audio.aac", "musica.mp3", "video.mp4", "hoja.xlsx", "archivo.zip", "programa.exe").forEach {
            assertFalse(VaultPreviewPolicy.canOffer(it, null, 1000))
        }
        assertFalse(VaultPreviewPolicy.canOffer(null, null, 1000))
        assertNull(VaultPreviewPolicy.resolve("falso.pdf", "audio/aac"))
    }
    @Test fun supportedTypesUseOnePolicyForHintAndAuthenticatedMetadata() {
        listOf("documento.PDF", "foto.PNG", "foto.jpeg", "foto.webp", "nota.txt", "tabla.csv", "datos.json").forEach {
            assertTrue(VaultPreviewPolicy.canOffer(it, null, 1000))
            assertNotNull(VaultPreviewPolicy.resolve(it, "application/octet-stream"))
        }
        assertEquals("text/plain", VaultPreviewPolicy.resolve("sin_extension", "Text/Plain; charset=utf-8"))
        assertTrue(VaultPreviewPolicy.canOffer("sin_extension", "text/plain", 1000))
        assertFalse(VaultPreviewPolicy.canOffer("grande.pdf", null, VaultPreviewPolicy.MAX_BYTES + 1048577))
    }
}
