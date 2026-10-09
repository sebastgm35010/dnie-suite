package pe.gob.reniec.dnie

import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.data.migrations.RetiredCredentialData
import java.security.KeyStore

/** Verifies the authorized upgrade and an isolated filesystem fixture; never writes credentials. */
class RetiredCredentialUpgradeInstrumentedTest {
    @Test fun upgradeRemovesProviderFilesAndKeystoreAliases() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("feature_removals", Context.MODE_PRIVATE)
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!preferences.getBoolean("credentials_retired_71", false) && System.nanoTime() < deadline) Thread.sleep(50)
        assertTrue("Upgrade cleanup must complete", preferences.getBoolean("credentials_retired_71", false))
        assertFalse(RetiredCredentialData.hasFiles(context.filesDir, context.noBackupFilesDir))
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        for (alias in RetiredCredentialData.aliases) assertFalse(keys.containsAlias(alias))
        val providers = context.packageManager.queryIntentServices(
            Intent("android.service.credentials.CredentialProviderService").setPackage(context.packageName), 0)
        assertTrue("Provider must not be registered", providers.isEmpty())
    }
    @Test fun cleanupDeletesDirectoryLinkWithoutFollowingIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "retirement-test").toFile()
        try {
            val files = java.io.File(root, "files").apply { mkdirs() }
            val vault = java.io.File(root, "vault").apply { mkdirs() }
            val keep = java.io.File(vault, "keep.bin").apply { writeText("synthetic-preserved") }
            val link = java.io.File(files, "passkeys")
            java.nio.file.Files.createSymbolicLink(link.toPath(), vault.toPath())
            assertTrue(RetiredCredentialData.remove(files, root) {})
            assertFalse(java.nio.file.Files.exists(link.toPath()))
            assertEquals("synthetic-preserved", keep.readText())
        } finally { root.deleteRecursively() }
    }
}
