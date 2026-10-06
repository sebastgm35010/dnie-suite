package pe.gob.reniec.dnie.vault

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backup bundles contain encrypted containers plus public convenience labels. No plaintext payloads. */
class VaultBackup(private val files: VaultFileStore, private val catalog: VaultCatalog) {
    fun export(output: OutputStream, checkCancelled: () -> Unit = {}, progress: (Int, Int) -> Unit = { _, _ -> }) {
        val items = files.list()
        require(items.size <= MAX_ITEMS)
        require(items.sumOf { it.encryptedBytes } <= MAX_TOTAL_BYTES) { "Respaldo demasiado grande" }
        ZipOutputStream(output).use { zip ->
            zip.setLevel(0) // Already encrypted: compression wastes CPU.
            zip.putNextEntry(ZipEntry("DNIE-BACKUP-1")); DataOutputStream(zip).writeInt(items.size); zip.closeEntry()
            items.forEachIndexed { index, item ->
                checkCancelled()
                zip.putNextEntry(ZipEntry("${item.id}.info"))
                val info = DataOutputStream(zip)
                info.writeUTF(item.displayName.orEmpty())
                info.writeLong(item.createdAt)
                val labels = VaultCatalog.encode(catalog.read(item.id))
                info.writeInt(labels.size); info.write(labels); zip.closeEntry()
                zip.putNextEntry(ZipEntry("${item.id}.dnievault"))
                files.exportEncrypted(item.id) { VaultContainer.copyBounded(it, zip, checkCancelled, VaultContainer.MAX_FILE_BYTES + 1048576) }
                zip.closeEntry(); progress(index + 1, items.size)
            }
            zip.putNextEntry(ZipEntry("DNIE-BACKUP-END")); DataOutputStream(zip).writeInt(items.size); zip.closeEntry()
        }
    }

    /** Roll back this import on failure; existing objects are never overwritten. ZIP names aren't extracted. */
    fun import(input: InputStream, checkCancelled: () -> Unit = {}, progress: (Int) -> Unit = {}): Int {
        val imported = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var total = 0L
        try {
            ZipInputStream(input).use { zip ->
                require(zip.nextEntry?.name == "DNIE-BACKUP-1") { "Respaldo no compatible" }
                val count = DataInputStream(zip).readInt().also { require(it in 0..MAX_ITEMS) }
                require(zip.read() == -1)
                zip.closeEntry()
                repeat(count) {
                    checkCancelled()
                    val entry = requireNotNull(zip.nextEntry) { "Respaldo incompleto" }
                    require(imported.size < MAX_ITEMS)
                    val id = entry.name.removeSuffix(".info")
                    require(entry.name == "$id.info" && id.matches(ID) && seen.add(id)) { "Índice del respaldo inválido" }
                    val bytes = ByteArrayOutputStream()
                    VaultContainer.copyBounded(zip, bytes, checkCancelled, 12288)
                    val info = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
                    val name = info.readUTF()
                    require(name.length <= 1024 && name.none { it == '/' || it == '\\' || it.isISOControl() })
                    info.readLong() // Source date is advisory; imported objects use their local commit time.
                    val size = info.readInt().also { require(it in 1..VaultCatalog.MAX_LABEL_BYTES) }
                    val labels = VaultCatalog.decode(ByteArray(size).also(info::readFully))
                    require(info.read() == -1)
                    zip.closeEntry()
                    require(zip.nextEntry?.name == "$id.dnievault") { "Falta un contenedor del respaldo" }
                    val limited = object : FilterInputStream(zip) {
                        override fun read(): Int = super.read().also { if (it >= 0) account(1) }
                        override fun read(b: ByteArray, off: Int, len: Int): Int = `in`.read(b, off, len).also { if (it > 0) account(it) }
                        private fun account(count: Int) { total += count; require(total <= MAX_TOTAL_BYTES) { "Respaldo demasiado grande" } }
                    }
                    val item = files.importEncrypted(limited, checkCancelled, name.takeIf(String::isNotBlank))
                    imported += item.id
                    catalog.write(item.id, labels.copy(verifiedAt = 0, backedUpAt = 0))
                    zip.closeEntry(); progress(imported.size)
                }
                require(zip.nextEntry?.name == "DNIE-BACKUP-END") { "Respaldo incompleto" }
                require(DataInputStream(zip).readInt() == count && zip.read() == -1)
                zip.closeEntry()
                require(zip.nextEntry == null) { "Entradas adicionales no permitidas" }
                checkCancelled()
            }
            return imported.size
        } catch (failure: Throwable) {
            imported.forEach { id -> runCatching { files.delete(id) }; runCatching { catalog.delete(id) } }
            throw failure
        }
    }

    companion object {
        const val MAX_ITEMS = 10000
        const val MAX_TOTAL_BYTES = 16L * 1024 * 1024 * 1024
        private val ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
