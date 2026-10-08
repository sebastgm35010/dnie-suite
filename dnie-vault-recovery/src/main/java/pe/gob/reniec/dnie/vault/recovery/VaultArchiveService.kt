package pe.gob.reniec.dnie.vault.recovery

import pe.gob.reniec.dnie.vault.*
import java.io.*
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import java.util.zip.*

/** Whole-vault export/import. No Android, UI, NFC, credentials or certificate persistence. */
class VaultArchiveService(private val codec: VaultArchiveCodec = VaultArchiveCodec()) {
    fun export(store: VaultFileStore, catalog: VaultCatalog, headers: Map<String, VaultHeader>,
               secrets: Map<String, ByteArray>, password: CharArray, destination: OutputStream,
               staging: File, check: () -> Unit = {}, progress: (Int, Int) -> Unit = { _, _ -> }): Int {
        require(headers.size in 1..1000 && store.list().map { it.id }.toSet() == headers.keys)
        require(store.list().sumOf { it.encryptedBytes } <= VaultArchiveCodec.MAX_BYTES)
        val zipFile = File.createTempFile("archive-export-", ".plain", staging)
        try {
            val manifest = ByteArrayOutputStream()
            DataOutputStream(manifest).use { data ->
                data.writeInt(2); data.writeInt(headers.size)
                ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                    zip.setLevel(1)
                    headers.entries.forEachIndexed { index, (id, header) ->
                        check()
                        val current = store.readHeader(id)
                        require(current.id == header.id && MessageDigest.isEqual(current.envelope, header.envelope) &&
                            MessageDigest.isEqual(current.recipient, header.recipient)) { "La bóveda cambió durante la exportación." }
                        store.restore(id, secrets.getValue(id), header.recipient, { input, metadata ->
                            data.writeUTF(metadata.name); data.writeUTF(metadata.mime)
                            data.writeLong(header.id.mostSignificantBits); data.writeLong(header.id.leastSignificantBits)
                            data.write(header.recipient)
                            val labels = VaultCatalog.encode(catalog.read(id))
                            data.writeInt(labels.size); data.write(labels)
                            zip.putNextEntry(ZipEntry("files/$index"))
                            VaultContainer.copyBounded(input, zip, check)
                            zip.closeEntry()
                        }, check)
                        require(zipFile.length() <= VaultArchiveCodec.MAX_BYTES)
                        progress(index + 1, headers.size)
                    }
                    zip.putNextEntry(ZipEntry("manifest")); zip.write(manifest.toByteArray()); zip.closeEntry()
                }
            }
            check()
            zipFile.inputStream().use { codec.seal(it, destination, password, check) }
            return headers.size
        } finally { check(zipFile.delete() || !zipFile.exists()) }
    }

    /** Authenticates archive, validates ZIP and re-encrypts all files before committing to existing store. */
    fun import(source: InputStream, password: CharArray, key: RSAPublicKey, store: VaultFileStore,
               catalog: VaultCatalog, staging: File, check: () -> Unit = {},
               progress: (Int, Int) -> Unit = { _, _ -> }, journalDirectory: File = staging): Int {
        require(key.modulus.bitLength() == 2048)
        val plain = File.createTempFile("archive-import-", ".plain", staging)
        val provisional = File(staging, "archive-stage-${UUID.randomUUID()}")
        check(provisional.mkdirs())
        val pending = VaultFileStore(File(provisional, "objects"), File(provisional, "plain"))
        val stagedLabels = linkedMapOf<String, VaultLabels>()
        val committed = mutableListOf<String>()
        val journal = File(journalDirectory, "vault-import.journal")
        var ownsJournal = false
        var decided = false
        val sourceIdentities = mutableSetOf<UUID>()
        try {
            plain.outputStream().use { codec.open(source, it, password, check) }
            ZipFile(plain).use { zip ->
                val manifest = requireNotNull(zip.getEntry("manifest")) { "Respaldo sin inventario." }
                val bytes = ByteArrayOutputStream().also { buffer -> zip.getInputStream(manifest).use {
                    VaultContainer.copyBounded(it, buffer, check, 10L * 1024 * 1024)
                } }.toByteArray()
                val data = DataInputStream(ByteArrayInputStream(bytes))
                require(data.readInt() == 2)
                val count = data.readInt().also { require(it in 1..1000) }
                val actualNames = zip.entries().toList().map { it.name }
                val expectedNames = (0 until count).map { "files/$it" } + "manifest"
                require(actualNames.size == count + 1 && actualNames.toSet() == expectedNames.toSet()) { "Inventario ZIP inválido." }
                var total = 0L
                repeat(count) { index ->
                    check()
                    val metadata = VaultMetadata(data.readUTF(), data.readUTF())
                    val originalId = UUID(data.readLong(), data.readLong())
                    val originalRecipient = ByteArray(32).also(data::readFully)
                    sourceIdentities += originalId
                    val size = data.readInt().also { require(it in 1..VaultCatalog.MAX_LABEL_BYTES) }
                    val labels = VaultCatalog.decode(ByteArray(size).also(data::readFully))
                    val entry = requireNotNull(zip.getEntry("files/$index"))
                    require(!entry.isDirectory && entry.size in 0..VaultContainer.MAX_FILE_BYTES)
                    total += entry.size; require(total <= VaultArchiveCodec.MAX_BYTES)
                    val item = VerifiedZipInput(zip.getInputStream(entry), entry).use {
                        pending.sealWithId(it, key, metadata, originalId, check)
                    }
                    stagedLabels[item.id] = labels.copy(verifiedAt = 0, backedUpAt = 0, mimeHint = metadata.mime)
                    progress(index + 1, count)
                }
                require(data.read() == -1) { "Inventario con datos adicionales." }
            }
            // Only matching original ciphertext identities are replaced, after all new objects commit.
            val replaced = store.list().filter { item ->
                val header = store.readHeader(item.id)
                header.id in sourceIdentities
            }.map { it.id }
            val planned = stagedLabels.keys.associateWith { UUID.randomUUID().toString() }
            require(replaced.size <= 1000)
            require(planned.values.none { target -> store.list().any { it.id == target } })
            check(journalDirectory.mkdirs() || journalDirectory.isDirectory)
            require(!journal.exists()) { "Hay una importación interrumpida pendiente de limpiar." }
            ownsJournal = true
            val pendingJournal = File(journalDirectory, "vault-import.journal.pending")
            FileOutputStream(pendingJournal).use { output ->
                val data = DataOutputStream(output)
                data.writeInt(2); data.writeBoolean(false)
                data.writeInt(planned.size); planned.values.forEach(data::writeUTF)
                data.writeInt(replaced.size); replaced.forEach(data::writeUTF)
                data.flush(); output.fd.sync()
            }
            check(pendingJournal.renameTo(journal)) { "No se pudo preparar la importación." }
            stagedLabels.forEach { (id, labels) ->
                check()
                pending.exportEncrypted(id) { input ->
                    val added = store.importEncryptedAs(input, planned.getValue(id), pending.list().first { it.id == id }.displayName, check)
                    committed += added.id
                    catalog.write(added.id, labels)
                }
            }
            check()
            decided = true
            RandomAccessFile(journal, "rw").use { it.seek(4); it.writeBoolean(true); it.fd.sync() }
            replaced.forEach { store.delete(it); catalog.delete(it) }
            check(journal.delete()) { "No se pudo finalizar la importación." }
            return committed.size
        } catch (failure: Throwable) {
            if (!decided) committed.forEach { id -> runCatching { store.delete(id); catalog.delete(id) } }
            // Keep journal if cleanup failed; startup will retry before showing inventory.
            if (!decided && ownsJournal && committed.none { id -> store.list().any { it.id == id } }) journal.delete()
            throw failure
        } finally {
            if (ownsJournal) File(journalDirectory, "vault-import.journal.pending").delete()
            plain.delete()
            // This directory is generated beneath the caller's private staging root only.
            provisional.deleteRecursively()
        }
    }

    fun recoverInterruptedImport(store: VaultFileStore, catalog: VaultCatalog, staging: File) {
        File(staging, "vault-import.journal.pending").delete()
        val journal = File(staging, "vault-import.journal")
        if (!journal.exists()) return
        require(journal.length() in 1..50000)
        val ids = journal.inputStream().use { input -> DataInputStream(input).run {
            val version = readInt().also { require(it in 1..2) }
            val done = version == 2 && readBoolean()
            fun readIds(allowEmpty: Boolean): List<String> {
                val count = readInt().also { require(it in (if (allowEmpty) 0 else 1)..1000) }
                return List(count) { readUTF().also { id -> require(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) } }
                    .also { require(it.toSet().size == count) }
            }
            val added = readIds(false)
            val replaced = if (version == 2) readIds(true) else emptyList()
            require(read() == -1 && added.toSet().intersect(replaced.toSet()).isEmpty())
            if (done) replaced else added
        } }
        ids.forEach { store.delete(it); catalog.delete(it) }
        check(journal.delete())
    }

    private class VerifiedZipInput(input: InputStream, private val entry: ZipEntry) : FilterInputStream(input) {
        private val crc = CRC32()
        private var count = 0L
        override fun read(): Int {
            val value = `in`.read()
            if (value >= 0) { crc.update(value); count++; require(count <= entry.size) }
            else verify()
            return value
        }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            val size = `in`.read(bytes, offset, length)
            if (size > 0) { crc.update(bytes, offset, size); count += size; require(count <= entry.size) }
            else if (size == -1) verify()
            return size
        }
        private fun verify() { require(count == entry.size && crc.value == entry.crc) { "Contenido ZIP inválido." } }
    }
}
