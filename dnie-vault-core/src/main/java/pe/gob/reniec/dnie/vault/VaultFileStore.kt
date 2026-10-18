package pe.gob.reniec.dnie.vault

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.util.UUID

data class VaultStoredItem(val id: String, val encryptedBytes: Long, val displayName: String? = null, val createdAt: Long = 0)

/** App-private storage. Optional plaintext name labels; content stays encrypted. */
class VaultFileStore(private val root: File, private val staging: File,
                     private val container: VaultContainer = VaultContainer()) {
    init {
        check(root.mkdirs() || root.isDirectory)
        check(staging.mkdirs() || staging.isDirectory)
        root.listFiles()?.filter { it.name.startsWith(".pending-") && it.isFile }?.forEach { it.delete() }
        staging.listFiles()?.filter { it.name.endsWith(".plain") && it.isFile }?.forEach { it.delete() }
    }

    fun list(): List<VaultStoredItem> = root.listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".dnievault") && validId(it.name.removeSuffix(".dnievault")) }
        .sortedByDescending { it.lastModified() }
        .map { val id = it.name.removeSuffix(".dnievault"); VaultStoredItem(id, it.length(), readName(id), it.lastModified()) }

    fun seal(input: InputStream, key: RSAPublicKey, metadata: VaultMetadata,
             checkCancelled: () -> Unit = {}): VaultStoredItem = sealWithId(input, key, metadata, UUID.randomUUID(), checkCancelled)

    fun sealWithId(input: InputStream, key: RSAPublicKey, metadata: VaultMetadata,
                   contentId: UUID, checkCancelled: () -> Unit = {}): VaultStoredItem = commit(checkCancelled) { output ->
        container.sealWithId(input, output, key, metadata, contentId, checkCancelled)
    }.let { item ->
        rememberName(item.id, metadata.name)
        item.copy(displayName = readName(item.id))
    }

    /** Only syntax is checked here; authenticity is verified when the DNI opens it. */
    fun importEncrypted(input: InputStream, checkCancelled: () -> Unit = {}, displayName: String? = null): VaultStoredItem =
        importEncryptedAs(input, UUID.randomUUID().toString(), displayName, checkCancelled)

    fun importEncryptedAs(input: InputStream, targetId: String, displayName: String? = null,
                          checkCancelled: () -> Unit = {}): VaultStoredItem = commit(checkCancelled, targetId) { output ->
        VaultContainer.copyBounded(input, output, checkCancelled, VaultContainer.MAX_FILE_BYTES + 1048576)
    }.let { item ->
        if (displayName != null) rememberName(item.id, displayName)
        item.copy(displayName = readName(item.id))
    }

    fun readHeader(id: String): VaultHeader = FileInputStream(file(id)).use(container::readHeader)

    fun exportEncrypted(id: String, publish: (InputStream) -> Unit) {
        FileInputStream(file(id)).use(publish)
    }

    /** No consumer sees unauthenticated plaintext, even when the final segment fails. */
    fun restore(id: String, secret: ByteArray, expectedRecipient: ByteArray,
                publish: (InputStream, VaultMetadata) -> Unit, checkCancelled: () -> Unit = {}): VaultMetadata {
        val temporary = File(staging, "${UUID.randomUUID()}.plain")
        try {
            val metadata = FileInputStream(file(id)).use { input ->
                val header = container.readHeader(input)
                require(MessageDigest.isEqual(header.recipient, expectedRecipient)) { "Este archivo requiere otro DNI" }
                FileOutputStream(temporary).use { output ->
                    container.openPayload(input, output, header, secret, checkCancelled).also {
                        output.flush(); output.fd.sync()
                    }
                }
            }
            checkCancelled()
            FileInputStream(temporary).use { publish(it, metadata) }
            rememberName(id, metadata.name)
            return metadata
        } finally {
            check(temporary.delete() || !temporary.exists()) { "No se pudo retirar el archivo temporal privado" }
        }
    }

    fun delete(id: String) {
        val target = file(id)
        check(!target.exists() || target.delete()) { "No se pudo eliminar el archivo cifrado" }
        File(root, "$id.name").delete()
    }

    private fun commit(checkCancelled: () -> Unit, id: String = UUID.randomUUID().toString(), write: (FileOutputStream) -> Unit): VaultStoredItem {
        require(validId(id) && !file(id).exists()) { "El destino cifrado ya existe." }
        val temporary = File(root, ".pending-$id")
        val target = file(id)
        try {
            FileOutputStream(temporary).use { output ->
                write(output); output.flush(); output.fd.sync()
            }
            FileInputStream(temporary).use { input ->
                container.readHeader(input)
                require(input.read() != -1) { "El contenedor no tiene contenido cifrado" }
            }
            checkCancelled()
            check(temporary.renameTo(target)) { "No se pudo finalizar el archivo cifrado" }
            return VaultStoredItem(id, target.length())
        } finally { temporary.delete() }
    }

    private fun file(id: String): File {
        require(validId(id)) { "Identificador inválido" }
        return File(root, "$id.dnievault")
    }
    private fun readName(id: String): String? = try {
        val label = File(root, "$id.name")
        if (label.length() !in 1..1024) null
        else FileInputStream(label).use { input ->
            val bytes = ByteArray(1025)
            var size = 0
            while (size < bytes.size) {
                val count = input.read(bytes, size, bytes.size - size)
                if (count < 0) break
                if (count == 0) return@use null
                size += count
            }
            if (size > 1024) null else bytes.copyOf(size).toString(Charsets.UTF_8).takeIf(::validName)
        }
    } catch (_: Exception) { null }

    /** Labels are a convenience, never a key, destination path or authenticated metadata. */
    private fun rememberName(id: String, name: String) {
        if (!validName(name)) return
        val temporary = File(root, ".pending-name-$id")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(name.toByteArray(Charsets.UTF_8)); output.flush(); output.fd.sync()
            }
            val label = File(root, "$id.name")
            if (!temporary.renameTo(label)) {
                // Windows cannot replace through renameTo; a missing label is harmless after interruption.
                if (!label.exists() || label.delete()) temporary.renameTo(label)
            }
        } catch (_: Exception) { /* A label failure must not discard a committed encrypted file. */ }
        finally { temporary.delete() }
    }
    private fun validName(name: String) = name.isNotBlank() && name.toByteArray(Charsets.UTF_8).size <= 1024 &&
        name.none { it == '/' || it == '\\' || it.isISOControl() }
    private fun validId(id: String) = id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
}
