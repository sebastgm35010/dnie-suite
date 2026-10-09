package pe.gob.reniec.dnie.data.migrations

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RetiredCredentialDataTest {
    @Test fun removesAllRetiredFilesAndExactAliasesWhilePreservingOtherFeatures() {
        val root = Files.createTempDirectory("retired-credential-test").toFile()
        try {
            val files = File(root, "files").apply { mkdirs() }
            val noBackup = File(root, "noBackup").apply { mkdirs() }
            File(files, "passkeys/nested").mkdirs()
            File(files, "passkeys/nested/catalog.bin").writeText("synthetic")
            for (suffix in listOf("", ".bak", ".new")) File(noBackup, "passkeys-biometric.bin$suffix").writeText("synthetic")
            val preserved = File(files, "vault/keep.bin").apply { parentFile.mkdirs(); writeText("keep") }
            val pairing = File(noBackup, "trusted-pc.bin").apply { writeText("pairing") }
            val deleted = mutableListOf<String>()
            assertTrue(RetiredCredentialData.remove(files, noBackup, deleted::add))
            assertEquals(RetiredCredentialData.aliases, deleted)
            assertFalse(RetiredCredentialData.hasFiles(files, noBackup))
            assertEquals("keep", preserved.readText()); assertEquals("pairing", pairing.readText())
            assertTrue(RetiredCredentialData.remove(files, noBackup) {})
        } finally { root.deleteRecursively() }
    }
    @Test fun failedKeyDeletionDoesNotMarkCleanupSuccessfulAndCanRetry() {
        val root = Files.createTempDirectory("retired-credential-retry").toFile()
        try {
            assertFalse(RetiredCredentialData.remove(root, root) { throw IllegalStateException("synthetic failure") })
            assertTrue(RetiredCredentialData.remove(root, root) {})
        } finally { root.deleteRecursively() }
    }
    @Test fun directoryLinkCannotDeleteVaultContents() {
        val root = Files.createTempDirectory("retired-credential-link").toFile()
        try {
            val files = File(root, "files").apply { mkdirs() }
            val vault = File(files, "vault").apply { mkdirs() }
            val preserved = File(vault, "keep.bin").apply { writeText("keep") }
            val link = File(files, "passkeys")
            // Windows developer mode or Unix permits this test; unsupported hosts skip explicitly.
            try { Files.createSymbolicLink(link.toPath(), vault.toPath()) }
            catch (e: Exception) { org.junit.Assume.assumeNoException(e) }
            assertTrue(RetiredCredentialData.remove(files, root) {})
            assertEquals("keep", preserved.readText()); assertFalse(Files.exists(link.toPath()))
        } finally { root.deleteRecursively() }
    }
}
