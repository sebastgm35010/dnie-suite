package pe.gob.reniec.dnie.passkeys.android

import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.core.nfc.VaultProbeCard
import pe.gob.reniec.dnie.passkeys.*

data class PasskeyAccount(val id: String, val site: String, val username: String)
data class PasskeyOperationResult(val response: Intent? = null, val accounts: List<PasskeyAccount> = emptyList(), val continuationToken: String? = null) {
    override fun toString() = "PasskeyOperationResult(privateData=redacted)"
}

/** Signing requires DNI unwrap in this ceremony, or its short-lived single-use account-selection grant. */
@RequiresApi(34)
class PasskeyOperations(context: Context) {
    private val independent = IndependentPasskeyVault(context)
    private val repository = EncryptedPasskeyRepository(context)
    private val access = PasskeyCardAccess(repository)
    private val adapter = PasskeyRequestAdapter(context)
    suspend fun executeIndependent(ceremony: PasskeyCeremony, pending: IndependentPasskeyVault.Pending,
        cipher: javax.crypto.Cipher, deleteId: String? = null): PasskeyOperationResult = withContext(Dispatchers.IO) {
        PasskeyCardAccess.serialized {
            independent.open(pending, cipher).use { session ->
                val catalog = session.catalog
                currentCoroutineContext().ensureActive()
                when (ceremony) {
                    is PasskeyCeremony.Create -> {
                        val (record, response) = withContext(Dispatchers.Default) {
                            PasskeyEngine().create(ceremony.options, ceremony.caller, catalog)
                        }
                        catalog.records.add(record)
                        currentCoroutineContext().ensureActive()
                        independent.save(session)
                        PasskeyOperationResult(response = adapter.createResult(response))
                    }
                    is PasskeyCeremony.Get -> {
                        val record = catalog.matching(ceremony.options).singleOrNull { it.encodedId == ceremony.id }
                            ?: error("Credencial no disponible")
                        val json = withContext(Dispatchers.Default) {
                            PasskeyEngine().authenticate(ceremony.options, ceremony.caller, record)
                        }
                        PasskeyOperationResult(response = adapter.getResult(json))
                    }
                    is PasskeyCeremony.Discover -> {
                        val (response, token) = adapter.discoveryResult(ceremony, catalog)
                        currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { failure ->
                            if (failure != null && token != null) PasskeyAuthorizations.continuation.cancel(token)
                        }
                        PasskeyOperationResult(response = response, continuationToken = token)
                    }
                    PasskeyCeremony.Manage -> {
                        if (deleteId != null) {
                            val record = catalog.records.singleOrNull { it.encodedId == deleteId }
                                ?: error("La passkey ya no existe")
                            catalog.records.remove(record); record.destroy()
                            currentCoroutineContext().ensureActive()
                            independent.save(session)
                        }
                        PasskeyOperationResult(accounts = catalog.records.map { PasskeyAccount(it.encodedId, it.rpId, it.username) })
                    }
                }
            }
        }
    }
    suspend fun executeBiometric(ceremony: PasskeyCeremony, catalog: PasskeyCatalog, revision: ByteArray): PasskeyOperationResult =
        withContext(Dispatchers.IO) { PasskeyCardAccess.serialized {
            check(!independent.exists()) { "Usa el almacén independiente" }
            require(ceremony is PasskeyCeremony.Get || ceremony is PasskeyCeremony.Discover)
            require(java.security.MessageDigest.isEqual(revision, repository.revision()))
            currentCoroutineContext().ensureActive()
            when (ceremony) {
                is PasskeyCeremony.Get -> {
                    val record = catalog.matching(ceremony.options).singleOrNull { it.encodedId == ceremony.id }
                        ?: error("Credencial no disponible")
                    val json = withContext(Dispatchers.Default) { PasskeyEngine().authenticate(ceremony.options, ceremony.caller, record) }
                    PasskeyOperationResult(response = adapter.getResult(json))
                }
                is PasskeyCeremony.Discover -> {
                    val (response, token) = adapter.discoveryResult(ceremony, catalog)
                    currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { failure ->
                        if (failure != null && token != null) PasskeyAuthorizations.continuation.cancel(token)
                    }
                    PasskeyOperationResult(response = response, continuationToken = token)
                }
                else -> error("Operación requiere DNI")
            }
        } }
    suspend fun continueGet(ceremony: PasskeyCeremony.Get): PasskeyOperationResult? = withContext(Dispatchers.IO) {
        val token = ceremony.authorization ?: return@withContext null
        val record = PasskeyAuthorizations.continuation.consume(token, ceremony.scope, ceremony.options, ceremony.id)
            ?: return@withContext null
        try {
            currentCoroutineContext().ensureActive()
            val json = withContext(Dispatchers.Default) { PasskeyEngine().authenticate(ceremony.options, ceremony.caller, record) }
            pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "continuation_consumed")
            PasskeyOperationResult(response = adapter.getResult(json))
        } finally { record.destroy() }
    }
    suspend fun execute(ceremony: PasskeyCeremony, card: VaultProbeCard, pin: String, deleteId: String? = null): PasskeyOperationResult =
        withContext(Dispatchers.IO) {
            check(!independent.exists()) { "Usa el almacén independiente" }
            access.open(card, pin, ceremony is PasskeyCeremony.Create).use { session ->
                currentCoroutineContext().ensureActive()
                val catalog = session.catalog
                when (ceremony) {
                    is PasskeyCeremony.Create -> {
                        val json = withContext(Dispatchers.Default) {
                            val (record, response) = PasskeyEngine().create(ceremony.options, ceremony.caller, catalog)
                            catalog.records.add(record)
                            response
                        }
                        repository.save(catalog, session.key)
                        PasskeyOperationResult(response = adapter.createResult(json))
                    }
                    is PasskeyCeremony.Get -> {
                        val record = catalog.matching(ceremony.options).singleOrNull { it.encodedId == ceremony.id }
                            ?: error("La passkey seleccionada no pertenece a este sitio o fue eliminada")
                        val json = withContext(Dispatchers.Default) { PasskeyEngine().authenticate(ceremony.options, ceremony.caller, record) }
                        PasskeyOperationResult(response = adapter.getResult(json))
                    }
                    is PasskeyCeremony.Discover -> {
                        val (response, token) = adapter.discoveryResult(ceremony, catalog)
                        currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { failure ->
                            if (failure != null && token != null) PasskeyAuthorizations.continuation.cancel(token)
                        }
                        PasskeyOperationResult(response = response, continuationToken = token)
                    }
                    PasskeyCeremony.Manage -> {
                        if (deleteId != null) {
                            val record = catalog.records.singleOrNull { it.encodedId == deleteId } ?: error("La passkey ya no existe")
                            catalog.records.remove(record); record.destroy()
                            repository.save(catalog, session.key)
                        }
                        PasskeyOperationResult(accounts = catalog.records.map { PasskeyAccount(it.encodedId, it.rpId, it.username) })
                    }
                }
            }
        }
}
