package pe.gob.reniec.dnie.vault

import java.io.*
import java.util.UUID

/** Convenience labels are public, untrusted metadata; never used as paths or cryptographic identity. */
data class VaultLabels(val folder: String = "", val tags: List<String> = emptyList(), val trashed: Boolean = false,
    val verifiedAt: Long = 0, val backedUpAt: Long = 0, val mimeHint: String? = null)
enum class VaultSort { NEWEST, NAME, SIZE }

class VaultCatalog(private val root: File) {
    init { check(root.mkdirs() || root.isDirectory) }

    fun read(id: String): VaultLabels {
        val file = file(id)
        return try {
            require(file.length() in 1..MAX_LABEL_BYTES.toLong())
            file.inputStream().use { decode(it.readBytes()) }
        } catch (_: Exception) { VaultLabels() }
    }

    fun write(id: String, labels: VaultLabels) {
        val target = file(id)
        val bytes = encode(labels)
        val temporary = File(root, ".pending-${UUID.randomUUID()}")
        try {
            FileOutputStream(temporary).use { it.write(bytes); it.flush(); it.fd.sync() }
            check(temporary.renameTo(target) || ((!target.exists() || target.delete()) && temporary.renameTo(target)))
        } finally { temporary.delete() }
    }

    fun delete(id: String) { file(id).delete() }
    private fun file(id: String): File {
        require(id.matches(ID)) { "Identificador inválido" }
        return File(root, "$id.labels")
    }

    companion object {
        private val ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        const val MAX_LABEL_BYTES = 8192
        fun encode(labels: VaultLabels): ByteArray {
            validate(labels)
            return ByteArrayOutputStream().also { buffer ->
                DataOutputStream(buffer).apply {
                    writeInt(3); writeUTF(labels.folder); writeBoolean(labels.trashed)
                    writeInt(labels.tags.size); labels.tags.forEach(::writeUTF)
                    writeLong(labels.verifiedAt); writeLong(labels.backedUpAt)
                    writeUTF(labels.mimeHint.orEmpty())
                }
            }.toByteArray().also { require(it.size <= MAX_LABEL_BYTES) }
        }
        fun decode(bytes: ByteArray): VaultLabels {
            require(bytes.size <= MAX_LABEL_BYTES)
            return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val version = input.readInt().also { require(it in 1..3) }
                val folder = input.readUTF()
                val trash = input.readBoolean()
                val count = input.readInt().also { require(it in 0..16) }
                val tags = List(count) { input.readUTF() }
                val labels = VaultLabels(folder, tags, trash,
                    if (version >= 2) input.readLong() else 0, if (version >= 2) input.readLong() else 0,
                    if (version >= 3) input.readUTF().takeIf(String::isNotBlank) else null)
                require(input.read() == -1); validate(labels); labels
            }
        }
        private fun validate(labels: VaultLabels) {
            require(labels.verifiedAt >= 0 && labels.backedUpAt >= 0)
            require(labels.mimeHint == null || (labels.mimeHint.length <= 128 && labels.mimeHint.none(Char::isISOControl)))
            require(labels.folder.length <= 80 && labels.folder.none { it.isISOControl() }) { "Carpeta demasiado larga" }
            require(labels.tags.size <= 16 && labels.tags.all { it.isNotBlank() && it.length <= 40 && it.none(Char::isISOControl) }) { "Etiquetas inválidas" }
        }
    }
}

data class VaultCatalogItem(val file: VaultStoredItem, val labels: VaultLabels)
