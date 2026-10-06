package pe.gob.reniec.dnie.vault

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher

class VaultLibraryTest {
    private fun directory() = Files.createTempDirectory("vault-library-test").toFile()
    private fun store(root: File) = VaultFileStore(File(root, "objects"), File(root, "staging"))
    private fun fails(action: () -> Unit) {
        try { action(); fail("Failure expected") } catch (_: Exception) {}
    }
    private val key by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
    private fun add(files: VaultFileStore, name: String = "contrato.pdf") = files.seal(
        ByteArrayInputStream("contenido privado".toByteArray()), key.public as RSAPublicKey, VaultMetadata(name, "application/pdf"))

    @Test fun labelsSurviveRestartAndMalformedLabelsFallBack() {
        val root = directory()
        try {
            val id = UUID.randomUUID().toString()
            val labels = VaultLabels("Trabajo", listOf("contrato", "2026"), true)
            VaultCatalog(root).write(id, labels)
            assertEquals(labels, VaultCatalog(root).read(id))
            File(root, "$id.labels").writeBytes(ByteArray(9000))
            assertEquals(VaultLabels(), VaultCatalog(root).read(id))
            fails { VaultCatalog(root).write("../outside", labels) }
            fails { VaultCatalog.encode(VaultLabels(tags = List(17) { "x" })) }
        } finally { root.deleteRecursively() }
    }
    @Test fun searchCombinesTermsAndSeparatesFoldersTrashAndSort() {
        val files = listOf(
            VaultCatalogItem(VaultStoredItem("a", 3, "Contrato.pdf", 1), VaultLabels("Trabajo", listOf("legal"))),
            VaultCatalogItem(VaultStoredItem("b", 5, "Foto.jpg", 2), VaultLabels("Personal")),
            VaultCatalogItem(VaultStoredItem("c", 8, "Contrato viejo.pdf", 3), VaultLabels("Trabajo", trashed = true)))
        assertEquals(listOf("a"), VaultInventory.select(files, "CONTRATO legal", "Trabajo", false, VaultSort.NAME).map { it.file.id })
        assertEquals(listOf("b", "a"), VaultInventory.select(files, "", null, false, VaultSort.SIZE).map { it.file.id })
        assertEquals(listOf("c"), VaultInventory.select(files, "", null, true, VaultSort.NEWEST).map { it.file.id })
    }
    @Test fun completeBackupRestoresBytesLabelsTrashAndCanStillDecrypt() {
        val source = directory(); val destination = directory()
        try {
            val files = store(source); val item = add(files)
            val catalog = VaultCatalog(File(source, "catalog"))
            val labels = VaultLabels("Trabajo", listOf("legal"), true)
            catalog.write(item.id, labels)
            val backup = ByteArrayOutputStream()
            VaultBackup(files, catalog).export(backup)
            assertFalse(backup.toString("ISO-8859-1").contains("contenido privado"))
            val restored = store(destination); val restoredCatalog = VaultCatalog(File(destination, "catalog"))
            assertEquals(1, VaultBackup(restored, restoredCatalog).import(ByteArrayInputStream(backup.toByteArray())))
            val newItem = restored.list().single()
            assertNotEquals(item.id, newItem.id)
            assertEquals(item.displayName, newItem.displayName)
            assertEquals(labels, restoredCatalog.read(newItem.id))
            val originalBytes = ByteArrayOutputStream(); files.exportEncrypted(item.id) { it.copyTo(originalBytes) }
            val importedBytes = ByteArrayOutputStream(); restored.exportEncrypted(newItem.id) { it.copyTo(importedBytes) }
            assertArrayEquals(originalBytes.toByteArray(), importedBytes.toByteArray())
            val header = restored.readHeader(newItem.id)
            val secret = Cipher.getInstance("RSA/ECB/PKCS1Padding").run { init(Cipher.DECRYPT_MODE, key.private); doFinal(header.envelope) }
            val plain = ByteArrayOutputStream()
            restored.restore(newItem.id, secret, header.recipient, { input, _ -> input.copyTo(plain) })
            assertEquals("contenido privado", plain.toString("UTF-8"))
            secret.fill(0)
        } finally { source.deleteRecursively(); destination.deleteRecursively() }
    }
    @Test fun failedBackupRollsBackNewObjectsWithoutTouchingExisting() {
        val source = directory(); val destination = directory()
        try {
            val files = store(source); add(files)
            val catalog = VaultCatalog(File(source, "catalog")); val backup = ByteArrayOutputStream()
            VaultBackup(files, catalog).export(backup)
            val restored = store(destination); val existing = add(restored, "original.txt")
            val target = VaultBackup(restored, VaultCatalog(File(destination, "catalog")))
            fails { target.import(ByteArrayInputStream(backup.toByteArray()), progress = { throw IOException("interrupted") }) }
            assertEquals(listOf(existing.id), restored.list().map { it.id })
            assertTrue(File(destination, "staging").listFiles().orEmpty().isEmpty())
        } finally { source.deleteRecursively(); destination.deleteRecursively() }
    }
    @Test fun unknownZipEntriesAndPathTraversalAreRejected() {
        val root = directory()
        try {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use {
                it.putNextEntry(ZipEntry("DNIE-BACKUP-1")); DataOutputStream(it).writeInt(1); it.closeEntry()
                it.putNextEntry(ZipEntry("../outside.info")); it.write(byteArrayOf(1)); it.closeEntry()
            }
            val files = store(root)
            fails { VaultBackup(files, VaultCatalog(File(root, "catalog"))).import(ByteArrayInputStream(out.toByteArray())) }
            assertTrue(files.list().isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun emptyBackupAndRepeatedImportNeverOverwrite() {
        val source = directory(); val destination = directory()
        try {
            val files = store(source); val catalog = VaultCatalog(File(source, "catalog"))
            val backup = ByteArrayOutputStream(); VaultBackup(files, catalog).export(backup)
            val targetFiles = store(destination); val target = VaultBackup(targetFiles, VaultCatalog(File(destination, "catalog")))
            assertEquals(0, target.import(ByteArrayInputStream(backup.toByteArray())))
            add(files); backup.reset(); VaultBackup(files, catalog).export(backup)
            repeat(2) { assertEquals(1, target.import(ByteArrayInputStream(backup.toByteArray()))) }
            assertEquals(2, targetFiles.list().size)
        } finally { source.deleteRecursively(); destination.deleteRecursively() }
    }
    @Test fun missingFinalMarkerRollsBackEvenAfterAllContainersWereCopied() {
        val source = directory(); val destination = directory()
        try {
            val files = store(source); add(files)
            val backup = ByteArrayOutputStream(); VaultBackup(files, VaultCatalog(File(source, "catalog"))).export(backup)
            val truncated = ByteArrayOutputStream()
            java.util.zip.ZipInputStream(ByteArrayInputStream(backup.toByteArray())).use { input ->
                ZipOutputStream(truncated).use { output ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        if (entry.name != "DNIE-BACKUP-END") {
                            output.putNextEntry(ZipEntry(entry.name)); input.copyTo(output); output.closeEntry()
                        }
                        input.closeEntry()
                    }
                }
            }
            val targetFiles = store(destination)
            fails { VaultBackup(targetFiles, VaultCatalog(File(destination, "catalog"))).import(ByteArrayInputStream(truncated.toByteArray())) }
            assertTrue(targetFiles.list().isEmpty())
        } finally { source.deleteRecursively(); destination.deleteRecursively() }
    }
    @Test fun importingDoesNotTrustSourceVerificationOrBackupStatus() {
        val source = directory(); val destination = directory()
        try {
            val files = store(source); val item = add(files)
            val catalog = VaultCatalog(File(source, "catalog"))
            catalog.write(item.id, VaultLabels("Personal", verifiedAt = 100, backedUpAt = 200))
            val backup = ByteArrayOutputStream(); VaultBackup(files, catalog).export(backup)
            val restored = store(destination); val restoredCatalog = VaultCatalog(File(destination, "catalog"))
            VaultBackup(restored, restoredCatalog).import(ByteArrayInputStream(backup.toByteArray()))
            assertEquals(VaultLabels("Personal"), restoredCatalog.read(restored.list().single().id))
        } finally { source.deleteRecursively(); destination.deleteRecursively() }
    }
    @Test fun labelDecoderRetainsVersionOneCompatibilityAndRejectsTrailingData() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply { writeInt(1); writeUTF("Trabajo"); writeBoolean(false); writeInt(1); writeUTF("legal") }
        assertEquals(VaultLabels("Trabajo", listOf("legal")), VaultCatalog.decode(bytes.toByteArray()))
        fails { VaultCatalog.decode(bytes.toByteArray() + byteArrayOf(1)) }
    }
    @Test fun mimeHintsSurviveCatalogEncodingAndVersionTwoStillDecodes() {
        val labels = VaultLabels("Trabajo", mimeHint = "application/pdf")
        assertEquals(labels, VaultCatalog.decode(VaultCatalog.encode(labels)))
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply { writeInt(2); writeUTF(""); writeBoolean(false); writeInt(0); writeLong(1); writeLong(2) }
        assertEquals(VaultLabels(verifiedAt = 1, backedUpAt = 2), VaultCatalog.decode(bytes.toByteArray()))
    }
}
