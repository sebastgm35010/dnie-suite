package pe.gob.reniec.dnie.domain.vault

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import pe.gob.reniec.dnie.core.nfc.VaultProbeCard
import pe.gob.reniec.dnie.domain.model.PinVerificationResult
import pe.gob.reniec.dnie.vault.VaultHeader

/** Borrowed by persistence adapter; secrets are ephemeral and always closed by the coordinator. */
class UnlockedRecoveryBatch(val headers: Map<String, VaultHeader>, val secrets: Map<String, ByteArray>) : AutoCloseable {
    override fun close() = secrets.values.forEach { it.fill(0) }
    override fun toString() = "UnlockedRecoveryBatch(secrets=redacted)"
}

class UnlockRecoveryBatch {
    suspend fun run(card: VaultProbeCard, headers: Map<String, VaultHeader>, pin: String,
                    progress: (Int, Int) -> Unit): UnlockedRecoveryBatch {
        require(headers.size in 1..1000)
        val secrets = linkedMapOf<String, ByteArray>()
        try {
            headers.entries.forEachIndexed { index, (id, header) ->
                currentCoroutineContext().ensureActive()
                // Caller verified the first PIN. DECIPHER consumes that authorization locally;
                // later envelopes need fresh VERIFY, without asking the user again or retrying errors.
                if (index > 0) when (val result = card.verifyPin(pin).getOrThrow()) {
                    PinVerificationResult.Success -> Unit
                    is PinVerificationResult.InvalidPin -> error("PIN rechazado: ${result.attemptsRemaining} intentos. Lote detenido sin reintentos.")
                    PinVerificationResult.CardBlocked -> error("PIN bloqueado. Lote detenido.")
                }
                secrets[id] = card.unwrapVaultKey(header.envelope).getOrThrow()
                require(secrets.getValue(id).size == 32)
                progress(index + 1, headers.size)
            }
            currentCoroutineContext().ensureActive()
            return UnlockedRecoveryBatch(headers, secrets)
        } catch (failure: Throwable) { secrets.values.forEach { it.fill(0) }; throw failure }
    }
}
