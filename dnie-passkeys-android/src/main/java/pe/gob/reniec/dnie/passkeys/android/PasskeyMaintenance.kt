package pe.gob.reniec.dnie.passkeys.android

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import pe.gob.reniec.dnie.core.nfc.VaultProbeCard
import pe.gob.reniec.dnie.passkeys.PasskeyCatalog
import pe.gob.reniec.dnie.passkeys.PasskeyBackupPolicy
import java.io.ByteArrayOutputStream
import java.security.interfaces.RSAPublicKey

/** SAF and card maintenance, independent of UI. Decrypted migration data exists only in RAM. */
class PasskeyMaintenance(private val context: Context,
    private val repository: EncryptedPasskeyRepository = EncryptedPasskeyRepository(context)) {
    suspend fun export(uri: Uri, card: VaultProbeCard, pin: String): Int = withContext(Dispatchers.IO) {
        PasskeyCardAccess(repository).open(card, pin, false).use { session ->
            val active = currentCoroutineContext()
            val excluded = session.catalog.records.count { !it.backupEligible }
            PasskeyBackupPolicy.portableCopy(session.catalog).use { backup ->
                val bytes = backup.encode()
                try { requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
                    pe.gob.reniec.dnie.vault.VaultContainer().seal(java.io.ByteArrayInputStream(bytes), output, session.key,
                        pe.gob.reniec.dnie.vault.VaultMetadata("passkeys.bin", "application/x-dnie-passkeys")) { active.ensureActive() }
                } } finally { bytes.fill(0) }
                currentCoroutineContext().ensureActive()
                session.catalog.records.filter { it.backupEligible }.forEach { it.backedUp = true }
                repository.save(session.catalog, session.key)
            }
            excluded
        }
    }
    suspend fun stage(uri: Uri): String = withContext(Dispatchers.IO) {
        val name = "incoming-${java.util.UUID.randomUUID()}.dnievault"
        requireNotNull(context.contentResolver.openInputStream(uri)).use { EncryptedPasskeyRepository(context, name).importEncrypted(it) }
        name
    }
    data class Snapshot(val bytes: ByteArray, val revision: ByteArray)
    suspend fun open(card: VaultProbeCard, pin: String, enrollment: Boolean = false): Snapshot {
        var owned: ByteArray? = null
        try { return withContext(Dispatchers.IO) {
            PasskeyCardAccess(repository).open(card, pin, enrollment).use {
                owned = it.catalog.encode()
                Snapshot(requireNotNull(owned), if (enrollment && !repository.exists()) ByteArray(32) else repository.revision())
            }
        } } catch (e: Throwable) { owned?.fill(0); throw e }
    }
    suspend fun restore(card: VaultProbeCard, pin: String, staged: String) = withContext(Dispatchers.IO) {
        val incoming = EncryptedPasskeyRepository(context, staged)
        PasskeyCardAccess(incoming).open(card, pin, false).use { session ->
            PasskeyBackupPolicy.validateRestore(session.catalog)
            // Fully authenticated and parsed before replacing the active catalog.
            saveRollback()
            repository.save(session.catalog, session.key)
        }
        incoming.discard(); PasskeyAuthorizations.continuation.cancel()
    }
    suspend fun migrate(card: VaultProbeCard, pin: String, bytes: ByteArray, revision: ByteArray) = withContext(Dispatchers.IO) {
        val empty = object : PasskeyStore {
            override suspend fun header(): pe.gob.reniec.dnie.vault.VaultHeader? = null
            override suspend fun open(secret: ByteArray, expectedRecipient: ByteArray): PasskeyCatalog = error("No catalog")
            override suspend fun save(catalog: PasskeyCatalog, key: RSAPublicKey) = error("No writes")
        }
        PasskeyCardAccess(empty).open(card, pin, true).use { newCard ->
            require(java.security.MessageDigest.isEqual(revision, repository.revision())) { "El catálogo cambió" }
            currentCoroutineContext().ensureActive()
            PasskeyCatalog.decode(bytes).use { catalog -> saveRollback(); repository.save(catalog, newCard.key) }
        }
        PasskeyAuthorizations.continuation.cancel()
    }
    private suspend fun saveRollback() {
        if (!repository.exists()) return
        val target = android.util.AtomicFile(java.io.File(context.filesDir, "passkeys/pre-maintenance.dnievault"))
        val output = target.startWrite()
        try { repository.export(output); target.finishWrite(output) } catch (e: Throwable) { target.failWrite(output); throw e }
    }
    suspend fun discard(name: String) { EncryptedPasskeyRepository(context, name).discard() }
    fun hasPrevious() = EncryptedPasskeyRepository(context, "pre-maintenance.dnievault").exists()
    suspend fun restorePrevious(card: VaultProbeCard, pin: String) = withContext(Dispatchers.IO) {
        PasskeyCardAccess(EncryptedPasskeyRepository(context, "pre-maintenance.dnievault")).open(card, pin, false).use { session ->
            saveRollback(); repository.save(session.catalog, session.key)
        }
    }
}
