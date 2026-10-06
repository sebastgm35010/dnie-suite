package pe.gob.reniec.dnie.passkeys.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import pe.gob.reniec.dnie.core.nfc.VaultProbeCard
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult
import pe.gob.reniec.dnie.passkeys.PasskeyCatalog
import pe.gob.reniec.dnie.vault.*
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

class OpenPasskeySession(val catalog: PasskeyCatalog, val key: RSAPublicKey, private val release: () -> Unit) : AutoCloseable {
    private var closed = false
    override fun close() { if (!closed) { closed = true; catalog.close(); release() } }
}

/** Same IAS/PKI/PACE path as the vault. Local authorization only, one PIN attempt, always closes NFC. */
class PasskeyCardAccess(private val repository: PasskeyStore) {
    suspend fun open(card: VaultProbeCard, pin: String, creating: Boolean): OpenPasskeySession {
        var secret: ByteArray? = null
        var owned = false
        try {
            ceremonyMutex.lock(); owned = true
            require(pin.length in 4..8 && pin.all { it in '0'..'9' })
            pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "card_stage", "stage" to "select_pki")
            card.selectPkiApplet().getOrThrow(); currentCoroutineContext().ensureActive()
            card.requireVaultProbeSupport().getOrThrow()
            val der = card.readCertificate(CertificateType.ENCRYPTION).getOrThrow()
            pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "card_stage", "stage" to "certificate_read")
            val probe = withContext(Dispatchers.Default) { VaultCapabilityProbe() }
            val certificate = withContext(Dispatchers.Default) { probe.readEncryptionCertificate(der) }
            if (creating) certificate.checkValidity()
            val key = certificate.publicKey as RSAPublicKey
            val recipient = MessageDigest.getInstance("SHA-256").digest(key.encoded)
            val header = repository.header()
            require(creating || header != null) { "No hay passkeys registradas" }
            require(header == null || MessageDigest.isEqual(header.recipient, recipient)) { "Estas passkeys requieren otro DNI. No se verificó el PIN." }
            when (val result = card.verifyPin(pin).getOrThrow()) {
                PinVerificationResult.Success -> Unit
                is PinVerificationResult.InvalidPin -> error("PIN incorrecto. Quedan ${result.attemptsRemaining} intentos. No se reintentó.")
                PinVerificationResult.CardBlocked -> error("PIN bloqueado")
            }
            pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "card_stage", "stage" to "pin_verified")
            currentCoroutineContext().ensureActive()
            val catalog = if (header != null) {
                val recovered = card.unwrapVaultKey(header.envelope).getOrThrow().also { secret = it }
                card.close()
                repository.open(recovered, recipient)
            } else {
                require(creating) { "No hay passkeys registradas" }
                withContext(Dispatchers.Default) {
                    require(probe.verify(key, CardKeyUnwrapper { card.decipherTestSecret(it).getOrThrow() }).verified)
                }
                PasskeyCatalog()
            }
            try { currentCoroutineContext().ensureActive() } catch (failure: Throwable) { catalog.close(); throw failure }
            val session = OpenPasskeySession(catalog, key) { ceremonyMutex.unlock() }
            pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "card_stage", "stage" to "catalog_unlocked")
            owned = false
            return session
        } finally { secret?.fill(0); try { card.close() } finally { if (owned) ceremonyMutex.unlock() } }
    }
    companion object {
        private val ceremonyMutex = Mutex()
        suspend fun <T> serialized(block: suspend () -> T): T {
            ceremonyMutex.lock()
            try { return block() } finally { ceremonyMutex.unlock() }
        }
    }
}
