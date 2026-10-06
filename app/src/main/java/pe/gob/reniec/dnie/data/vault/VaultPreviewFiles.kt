package pe.gob.reniec.dnie.data.vault

import android.content.Context
import pe.gob.reniec.dnie.vault.VaultContainer
import pe.gob.reniec.dnie.vault.VaultMetadata
import java.io.File
import java.io.InputStream
import java.util.UUID
import pe.gob.reniec.dnie.domain.vault.VaultPreviewPolicy

data class VaultPreviewTicket(val token: String, val name: String, val mime: String)

/** Plaintext is private, never exposed by FileProvider; only the internal viewer consumes tickets. */
class VaultPreviewFiles(context: Context) {
    private val root = File(context.applicationContext.cacheDir, "vault-viewer-private")

    fun create(input: InputStream, metadata: VaultMetadata, checkCancelled: () -> Unit): VaultPreviewTicket {
        val mime = requireNotNull(VaultPreviewPolicy.resolve(metadata.name, metadata.mime)) {
            "Vista previa disponible para PDF, imágenes y texto. Exporta este formato para abrirlo."
        }
        check(root.mkdirs() || root.isDirectory)
        val ticket = VaultPreviewTicket(UUID.randomUUID().toString(), metadata.name, mime)
        val file = file(ticket.token)
        try {
            file.outputStream().use { VaultContainer.copyBounded(input, it, checkCancelled, MAX_BYTES) }
            checkCancelled()
            return ticket
        } catch (failure: Throwable) { file.delete(); throw failure }
    }

    fun file(token: String): File {
        require(token.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        return File(root, "$token.plain")
    }
    fun delete(token: String) { file(token).delete() }
    fun clear() { root.listFiles()?.filter { it.isFile && it.name.endsWith(".plain") }?.forEach { it.delete() } }

    companion object {
        const val MAX_BYTES = VaultPreviewPolicy.MAX_BYTES
    }
}
