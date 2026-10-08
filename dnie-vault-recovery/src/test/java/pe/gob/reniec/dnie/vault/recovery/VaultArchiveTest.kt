package pe.gob.reniec.dnie.vault.recovery

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.vault.*
import java.io.*
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher

class VaultArchiveTest {
    private val password get() = "respaldo-completo-contraseña-2026".toCharArray()
    private fun pair() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private fun secret(store: VaultFileStore, id: String, key: KeyPair) = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
        init(Cipher.DECRYPT_MODE, key.private); doFinal(store.readHeader(id).envelope)
    }
    private fun rejected(action: () -> Unit) { var rejected = false; try { action() } catch (_: Exception) { rejected = true }; assertTrue(rejected) }
    private fun fixture(action: (File, VaultFileStore, VaultCatalog, File, KeyPair) -> Unit) {
        val root = Files.createTempDirectory("archive-test").toFile()
        val stage = File(root, "stage")
        val store = VaultFileStore(File(root, "source"), stage)
        val catalog = VaultCatalog(File(root, "labels"))
        try { action(root, store, catalog, stage, pair()) } finally { root.deleteRecursively() }
    }
    private fun export(store: VaultFileStore, catalog: VaultCatalog, stage: File, key: KeyPair): ByteArray {
        val headers = store.list().associate { it.id to store.readHeader(it.id) }
        val secrets = headers.mapValues { secret(store, it.key, key) }
        try { return ByteArrayOutputStream().also {
            VaultArchiveService().export(store, catalog, headers, secrets, password, it, stage)
        }.toByteArray() } finally { secrets.values.forEach { it.fill(0) } }
    }

    @Test fun oneArchiveRestoresAllWithNewKeyNamesLabelsAndTrash() = fixture { root, source, catalog, stage, old ->
        val original = ByteArray(1048576 + 31) { (it * 7).toByte() }
        val a = source.seal(original.inputStream(), old.public as RSAPublicKey, VaultMetadata("á.txt", "text/plain"))
        val b = source.seal(byteArrayOf(4).inputStream(), old.public as RSAPublicKey, VaultMetadata("á.txt", "text/plain"))
        catalog.write(a.id, VaultLabels("Carpeta", listOf("test"), true))
        val backup = export(source, catalog, stage, old)
        val new = pair(); val target = VaultFileStore(File(root, "target"), File(root, "target-stage"))
        val labels = VaultCatalog(File(root, "target-labels"))
        assertEquals(2, VaultArchiveService().import(backup.inputStream(), password, new.public as RSAPublicKey, target, labels, stage))
        assertEquals(2, source.list().size); assertEquals(2, target.list().size)
        val restored = mutableListOf<ByteArray>()
        target.list().forEach { item ->
            val key = secret(target, item.id, new)
            try { target.restore(item.id, key, target.readHeader(item.id).recipient, { input, metadata ->
                assertEquals("á.txt", metadata.name); restored += input.readBytes()
            }) } finally { key.fill(0) }
            rejected { secret(target, item.id, old) }
        }
        assertTrue(restored.any { it.contentEquals(original) }); assertTrue(restored.any { it.contentEquals(byteArrayOf(4)) })
        assertEquals(1, target.list().count { labels.read(it.id).trashed })
        assertTrue(target.list().any { labels.read(it.id).folder == "Carpeta" })
        assertEquals(0, stage.listFiles()!!.size)
    }
    @Test fun wrongPasswordAndTamperAndTruncationNeverImport() = fixture { root, source, catalog, stage, old ->
        source.seal("content".byteInputStream(), old.public as RSAPublicKey, VaultMetadata("a.txt", "text/plain"))
        val backup = export(source, catalog, stage, old)
        val target = VaultFileStore(File(root, "target"), File(root, "target-stage"))
        val labels = VaultCatalog(File(root, "target-labels"))
        val new = pair()
        val mutated = backup.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        listOf(backup to "contraseña-incorrecta-2026".toCharArray(), mutated to password, backup.copyOf(backup.size - 1) to password).forEach { (bytes, pw) ->
            rejected { VaultArchiveService().import(bytes.inputStream(), pw, new.public as RSAPublicKey, target, labels, stage) }
            assertTrue(target.list().isEmpty()); assertTrue(stage.listFiles()!!.isEmpty())
        }
    }
    @Test fun restoringSameVaultMigratesMatchingFilesWithoutDuplicates() = fixture { root, store, catalog, stage, old ->
        val original = store.seal("migration".byteInputStream(), old.public as RSAPublicKey, VaultMetadata("m.txt", "text/plain"))
        val logicalId = store.readHeader(original.id).id
        val backup = export(store, catalog, stage, old)
        val new = pair(); val service = VaultArchiveService()
        repeat(2) {
            assertEquals(1, service.import(backup.inputStream(), password, new.public as RSAPublicKey, store, catalog, stage))
            assertEquals(1, store.list().size)
            val item = store.list().single()
            assertEquals(logicalId, store.readHeader(item.id).id)
            val key = secret(store, item.id, new)
            try { store.restore(item.id, key, store.readHeader(item.id).recipient, { input, _ -> assertEquals("migration", input.reader().readText()) }) }
            finally { key.fill(0) }
        }
    }
    @Test fun committedJournalFinishesReplacingOldFilesAfterRestart() = fixture { root, store, labels, stage, key ->
        val old = store.seal("old".byteInputStream(), key.public as RSAPublicKey, VaultMetadata("old.txt", "text/plain"))
        val new = store.seal("new".byteInputStream(), key.public as RSAPublicKey, VaultMetadata("new.txt", "text/plain"))
        DataOutputStream(File(stage, "vault-import.journal").outputStream()).use {
            it.writeInt(2); it.writeBoolean(true); it.writeInt(1); it.writeUTF(new.id); it.writeInt(1); it.writeUTF(old.id)
        }
        VaultArchiveService().recoverInterruptedImport(store, labels, stage)
        assertEquals(listOf(new.id), store.list().map { it.id })
    }
    @Test fun failureDuringCommitKeepsOriginalKeysAndRollsBackNewObjects() = fixture { root, store, catalog, stage, old ->
        repeat(2) { store.seal("old-$it".byteInputStream(), old.public as RSAPublicKey, VaultMetadata("$it.txt", "text/plain")) }
        val originalIds = store.list().map { it.id }.toSet()
        val backup = export(store, catalog, stage, old)
        val new = pair()
        rejected { VaultArchiveService().import(backup.inputStream(), password, new.public as RSAPublicKey, store, catalog, stage,
            check = { if (File(stage, "vault-import.journal").exists() && store.list().size > 2) throw IOException("commit interrupted") }) }
        assertEquals(originalIds, store.list().map { it.id }.toSet())
        store.list().forEach { secret(store, it.id, old).fill(0) }
        assertTrue(stage.listFiles()!!.isEmpty())
    }
    @Test fun corruptSourceNeverExportsPartialArchive() = fixture { root, store, catalog, stage, old ->
        val item = store.seal("original".byteInputStream(), old.public as RSAPublicKey, VaultMetadata("a.txt", "text/plain"))
        val header = store.readHeader(item.id); val key = secret(store, item.id, old)
        val file = File(root, "source/${item.id}.dnievault")
        file.writeBytes(file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        val output = ByteArrayOutputStream().apply { write("sentinel".toByteArray()) }
        try { rejected { VaultArchiveService().export(store, catalog, mapOf(item.id to header), mapOf(item.id to key), password, output, stage) } }
        finally { key.fill(0) }
        assertEquals("sentinel", output.toString("UTF-8")); assertTrue(stage.listFiles()!!.isEmpty())
    }
    @Test fun cancellationBeforeCommitPreservesExistingFiles() = fixture { root, source, catalog, stage, old ->
        source.seal("new".byteInputStream(), old.public as RSAPublicKey, VaultMetadata("a.txt", "text/plain"))
        val backup = export(source, catalog, stage, old)
        val target = VaultFileStore(File(root, "target"), File(root, "target-stage"))
        val labels = VaultCatalog(File(root, "target-labels")); val new = pair()
        val existing = target.seal("existing".byteInputStream(), new.public as RSAPublicKey, VaultMetadata("b.txt", "text/plain"))
        rejected { VaultArchiveService().import(backup.inputStream(), password, new.public as RSAPublicKey, target, labels, stage,
            progress = { _, _ -> throw IOException("cancelled") }) }
        assertEquals(listOf(existing.id), target.list().map { it.id })
        assertTrue(stage.listFiles()!!.isEmpty())
    }
    @Test fun restartJournalRollsBackOnlyRecordedNewObjects() = fixture { root, target, labels, stage, key ->
        val existing = target.seal("old".byteInputStream(), key.public as RSAPublicKey, VaultMetadata("old.txt", "text/plain"))
        val added = target.seal("new".byteInputStream(), key.public as RSAPublicKey, VaultMetadata("new.txt", "text/plain"))
        DataOutputStream(File(stage, "vault-import.journal").outputStream()).use {
            it.writeInt(1); it.writeInt(1); it.writeUTF(added.id)
        }
        VaultArchiveService().recoverInterruptedImport(target, labels, stage)
        assertEquals(listOf(existing.id), target.list().map { it.id }); assertTrue(stage.listFiles()!!.isEmpty())
    }
    @Test fun malformedCostsRejectedBeforeKdfAndCancellationChecked() {
        val codec = VaultArchiveCodec(); val output = ByteArrayOutputStream()
        codec.seal("payload".byteInputStream(), output, password)
        val bad = output.toByteArray().also { it[12] = 127 }
        rejected { codec.open(bad.inputStream(), ByteArrayOutputStream(), password) }
        rejected { codec.seal("payload".byteInputStream(), ByteArrayOutputStream(), password) { throw IOException("cancel") } }
    }
}
