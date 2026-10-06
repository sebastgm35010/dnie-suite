package pe.gob.reniec.dnie.vault

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.data.vault.VaultShareIntake

class VaultShareIntakeTest {
    @Test fun acceptsContentGrantsAndDeduplicatesButRejectsFilePaths() {
        val first = Uri.parse("content://example/one")
        val second = Uri.parse("content://example/two")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM,
            arrayListOf(first, first, Uri.parse("file:///private/file"), second))
        assertEquals(listOf(first, second), VaultShareIntake.read(intent))
        assertEquals(listOf(first), VaultShareIntake.read(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, first)))
    }
    @Test fun unrelatedActionsDoNotImportAnySources() {
        assertTrue(VaultShareIntake.read(Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_STREAM, Uri.parse("content://example/one"))).isEmpty())
    }
}
