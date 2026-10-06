package pe.gob.reniec.dnie.passkeys.android

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.passkeys.PasskeyCatalog
import pe.gob.reniec.dnie.vault.*
import java.io.*
import java.security.interfaces.RSAPublicKey

/** A separate encrypted catalog. No RP/account index, no plaintext staging and no file-vault exports. */
class EncryptedPasskeyRepository(context: Context, private val name: String = "catalog.dnievault",
    private val invalidateDevice: () -> Unit = { PasskeyDeviceVault(context).disable() }) : PasskeyStore {
    private val app = context.applicationContext
    init { require(name == "catalog.dnievault" || name == "pre-maintenance.dnievault" || name.matches(Regex("incoming-[0-9a-f-]{36}\\.dnievault"))) }
    private val target = AtomicFile(File(app.filesDir, "passkeys/$name"))
    private val container by lazy { VaultContainer() }
    fun exists() = target.baseFile.exists() || File(target.baseFile.path + ".bak").exists()
    override suspend fun header(): VaultHeader? = io {
        if (exists()) target.openRead().use(container::readHeader) else null
    }
    override suspend fun open(secret: ByteArray, expectedRecipient: ByteArray): PasskeyCatalog = io {
        val active = currentCoroutineContext()
        target.openRead().use { input ->
            val header = container.readHeader(input)
            require(java.security.MessageDigest.isEqual(header.recipient, expectedRecipient))
            val output = object : ByteArrayOutputStream() {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    require(count.toLong() + len <= PasskeyCatalog.MAX_BYTES + 4096); super.write(b, off, len)
                }
                override fun write(value: Int) { require(count < PasskeyCatalog.MAX_BYTES + 4096); super.write(value) }
                fun wipe() { buf.fill(0); reset() }
            }
            try {
                val metadata = container.openPayload(input, output, header, secret) { active.ensureActive() }
                require(metadata.name == "passkeys.bin" && metadata.mime == "application/x-dnie-passkeys")
                val bytes = output.toByteArray()
                try { PasskeyCatalog.decode(bytes) } finally { bytes.fill(0) }
            } finally { output.wipe() }
        }
    }
    override suspend fun save(catalog: PasskeyCatalog, key: RSAPublicKey): Unit = io {
        val active = currentCoroutineContext()
        val bytes = catalog.encode()
        target.baseFile.parentFile?.mkdirs()
        val output = target.startWrite()
        try {
            container.seal(ByteArrayInputStream(bytes), output, key,
                VaultMetadata("passkeys.bin", "application/x-dnie-passkeys")) { active.ensureActive() }
            active.ensureActive()
            if (name == "catalog.dnievault") {
                invalidateDevice()
                PasskeyAuthorizations.continuation.cancel()
            }
            target.finishWrite(output)
        } catch (failure: Throwable) { target.failWrite(output); throw failure }
        finally { bytes.fill(0) }
    }
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
    suspend fun export(output: OutputStream) = io { target.openRead().use { it.copyTo(output) }; Unit }
    suspend fun revision(): ByteArray = io { target.openRead().use {
        val bytes = it.readNBytes(2 * 1024 * 1024 + 1)
        require(bytes.size <= 2 * 1024 * 1024)
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
    } }
    suspend fun importEncrypted(input: InputStream) = io {
        require(name.startsWith("incoming-"))
        val bytes = input.readNBytes(2 * 1024 * 1024 + 1)
        require(bytes.size <= 2 * 1024 * 1024)
        container.readHeader(ByteArrayInputStream(bytes))
        target.baseFile.parentFile?.mkdirs()
        val output = target.startWrite()
        try { output.write(bytes); target.finishWrite(output) } catch (e: Throwable) { target.failWrite(output); throw e }
    }
    suspend fun discard() = io { require(name.startsWith("incoming-")); target.delete() }
    companion object { private val mutex = Mutex() }
}
