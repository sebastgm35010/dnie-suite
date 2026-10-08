package pe.gob.reniec.dnie.data.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.vault.*
import java.io.File
import java.security.interfaces.RSAPublicKey
import pe.gob.reniec.dnie.domain.vault.VaultFileDetails

/** Android persistence/SAF adapter. Owns disk serialization, never the NFC session or credentials. */
class AndroidVaultRepository(context: Context) {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private var store: VaultFileStore? = null
    private val probe by lazy { VaultCapabilityProbe() }
    private val certificateFile get() = AtomicFile(File(app.filesDir, "vault-preview/recipient.der"))
    private val files get() = requireNotNull(store) { "La bóveda todavía no está preparada" }
    private val catalog by lazy { VaultCatalog(File(app.filesDir, "vault-preview/catalog")) }
    private val previews by lazy { VaultPreviewFiles(app) }
    private val recovery by lazy { AndroidVaultRecoveryAdapter(app) }

    suspend fun prepareRecovery(id: String, header: VaultHeader, secret: ByteArray,
                                password: CharArray, destination: Uri): Unit = io {
        recovery.prepare(files, id, header, secret, password, destination)
    }
    suspend fun restoreRecovery(id: String, packageUri: Uri, password: CharArray,
                                destination: Uri): Restoration = io {
        recovery.restore(files, id, packageUri, password, destination).also {
            mark(id, verified = true, mime = it.metadata.mime)
        }
    }

    data class Inventory(val pairedFingerprint: String?, val riskAcknowledged: Boolean)
    data class Restoration(val metadata: VaultMetadata, val nameApplied: Boolean)

    suspend fun initialize(): Inventory = io {
        if (store == null) store = VaultFileStore(File(app.filesDir, "vault-preview/objects"), File(app.cacheDir, "vault-private"))
        val fingerprint = if (certificateFile.baseFile.exists()) {
            val certificate = probe.readEncryptionCertificate(certificateFile.readFully())
            probe.fingerprint(certificate.publicKey as RSAPublicKey)
        } else null
        Inventory(fingerprint, app.getSharedPreferences("vault-preview", Context.MODE_PRIVATE).getBoolean("risk", false))
    }

    fun acknowledgeRisk() {
        app.getSharedPreferences("vault-preview", Context.MODE_PRIVATE).edit().putBoolean("risk", true).apply()
    }
    suspend fun list(): List<VaultStoredItem> = io { files.list() }
    suspend fun inventory(): List<VaultCatalogItem> = io { files.list().map { VaultCatalogItem(it, catalog.read(it.id)) } }
    suspend fun details(id: String): VaultFileDetails = io {
        val item = files.list().first { it.id == id }
        VaultFileDetails(VaultCatalogItem(item, catalog.read(id)), files.readHeader(id).recipient.joinToString("") { "%02x".format(it.toInt() and 255) })
    }
    suspend fun organize(id: String, labels: VaultLabels) = io {
        require(files.list().any { it.id == id }); catalog.write(id, labels)
    }
    suspend fun trash(id: String, trashed: Boolean) = io {
        require(files.list().any { it.id == id }); catalog.write(id, catalog.read(id).copy(trashed = trashed))
    }
    suspend fun exportBackup(destination: Uri, progress: (Int, Int) -> Unit): Unit = io {
        val active = currentCoroutineContext()
        app.contentResolver.openOutputStream(destination, "wt")?.use {
            VaultBackup(files, catalog).export(it, { active.ensureActive() }, progress)
        } ?: error("No se pudo escribir el respaldo")
        files.list().forEach { mark(it.id, backedUp = true) }
    }
    suspend fun importBackup(source: Uri, progress: (Int) -> Unit): Int = io {
        val active = currentCoroutineContext()
        app.contentResolver.openInputStream(source)?.use {
            VaultBackup(files, catalog).import(it, { active.ensureActive() }, progress)
        } ?: error("No se pudo leer el respaldo")
    }
    suspend fun preview(id: String, secret: ByteArray, recipient: ByteArray): VaultPreviewTicket = io {
        require(files.list().first { it.id == id }.encryptedBytes <= VaultPreviewFiles.MAX_BYTES + 1048576) {
            "Vista previa limitada a 32 MiB. Puedes descifrar este archivo a un destino externo."
        }
        val active = currentCoroutineContext()
        var ticket: VaultPreviewTicket? = null
        try {
            files.restore(id, secret, recipient, { input, metadata ->
                ticket = previews.create(input, metadata) { active.ensureActive() }
            }, { active.ensureActive() })
            active.ensureActive()
            mark(id, verified = true, mime = ticket?.mime)
            requireNotNull(ticket)
        } catch (failure: Throwable) { ticket?.let { previews.delete(it.token) }; throw failure }
    }
    fun discardPreview(token: String) { previews.delete(token) }
    suspend fun createNote(name: String, body: String, folder: String): Unit = io {
        val metadata = pe.gob.reniec.dnie.domain.vault.VaultNoteDraft.metadata(name, body)
        val certificate = probe.readEncryptionCertificate(certificateFile.readFully())
        certificate.checkValidity()
        val bytes = body.toByteArray(Charsets.UTF_8)
        try {
            val active = currentCoroutineContext()
            val item = files.seal(java.io.ByteArrayInputStream(bytes), certificate.publicKey as RSAPublicKey,
                metadata, { active.ensureActive() })
            runCatching { catalog.write(item.id, VaultLabels(folder, mimeHint = metadata.mime)) }
        } finally { bytes.fill(0) }
    }
    suspend fun readHeader(id: String): VaultHeader = io { files.readHeader(id) }
    suspend fun savePublicCertificate(der: ByteArray) = io {
        currentCoroutineContext().ensureActive()
        val file = certificateFile
        file.baseFile.parentFile?.mkdirs()
        val output = file.startWrite()
        try {
            output.write(der); currentCoroutineContext().ensureActive(); file.finishWrite(output)
        } catch (e: Exception) { file.failWrite(output); throw e }
    }

    suspend fun importFile(uri: Uri, folder: String = ""): Unit = io {
        val resolver = app.contentResolver
        val certificate = probe.readEncryptionCertificate(certificateFile.readFully())
        certificate.checkValidity()
        val name = displayName(uri)?.filterNot { it == '/' || it == '\\' || it.isISOControl() }
            ?.take(200)?.takeIf { it.isNotBlank() } ?: "archivo"
        val mime = resolver.getType(uri)?.takeIf { it.length <= 128 && it.matches(MIME) } ?: "application/octet-stream"
        val active = currentCoroutineContext()
        resolver.openInputStream(uri)?.use {
            val item = files.seal(it, certificate.publicKey as RSAPublicKey, VaultMetadata(name, mime), { active.ensureActive() })
            runCatching { catalog.write(item.id, VaultLabels(folder, mimeHint = mime)) }
        } ?: error("No se pudo leer el archivo")
    }
    suspend fun importArchive(uri: Uri): Unit = io {
        val hint = displayName(uri)?.takeIf { it.endsWith(".dnievault", ignoreCase = true) }
            ?.dropLast(10)?.takeIf { !it.matches(OPAQUE_ID) }
        val active = currentCoroutineContext()
        app.contentResolver.openInputStream(uri)?.use {
            files.importEncrypted(it, { active.ensureActive() }, displayName = hint)
        } ?: error("No se pudo leer el contenedor")
    }
    suspend fun exportEncrypted(id: String, destination: Uri): Unit = io {
        val active = currentCoroutineContext()
        files.exportEncrypted(id) { input ->
            app.contentResolver.openOutputStream(destination, "wt")?.use {
                VaultContainer.copyBounded(input, it, { active.ensureActive() }, VaultContainer.MAX_FILE_BYTES + 1048576)
            } ?: error("No se pudo escribir el destino")
        }
        mark(id, backedUp = true)
    }
    suspend fun delete(id: String) = io { files.delete(id); catalog.delete(id) }

    /** Caller owns and wipes the card secret. Publish sees only fully authenticated plaintext. */
    suspend fun restore(id: String, secret: ByteArray, recipient: ByteArray, destination: Uri,
                        onMetric: (String, Long) -> Unit = { _, _ -> }): Restoration = io {
        val active = currentCoroutineContext()
        var nameApplied = false
        val verifyStarted = System.nanoTime()
        val metadata = files.restore(id, secret, recipient, { input, original ->
            val publishStarted = System.nanoTime()
            onMetric("file.authenticate", (publishStarted - verifyStarted) / 1000000)
            active.ensureActive()
            app.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                VaultContainer.copyBounded(input, output, { active.ensureActive() }); output.flush()
            } ?: error("No se pudo abrir el destino elegido")
            onMetric("file.saf_publish", (System.nanoTime() - publishStarted) / 1000000)
            active.ensureActive()
            nameApplied = RestoredDocumentName.apply(app, destination, original.name)
            onMetric("file.restore_name", if (nameApplied) 1 else 0)
        }, { active.ensureActive() })
        mark(id, verified = true, mime = metadata.mime)
        Restoration(metadata, nameApplied)
    }

    private fun mark(id: String, verified: Boolean = false, backedUp: Boolean = false, mime: String? = null) {
        runCatching {
            val labels = catalog.read(id)
            catalog.write(id, labels.copy(verifiedAt = if (verified) System.currentTimeMillis() else labels.verifiedAt,
                backedUpAt = if (backedUp) System.currentTimeMillis() else labels.backedUpAt,
                mimeHint = mime ?: labels.mimeHint))
        }
    }

    private fun displayName(uri: Uri): String? = app.contentResolver.query(uri,
        arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
    private suspend fun <T> io(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { currentCoroutineContext().ensureActive(); action() }
    }
    private companion object {
        val MIME = Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")
        val OPAQUE_ID = Regex("[0-9a-fA-F-]{36}")
    }
}
