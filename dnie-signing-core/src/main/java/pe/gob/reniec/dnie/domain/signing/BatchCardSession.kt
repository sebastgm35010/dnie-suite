package pe.gob.reniec.dnie.domain.signing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.diagnostics.DiagnosticLog
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult
import kotlin.coroutines.coroutineContext

/** Authorization belongs to one explicitly approved batch and one physical connection.
 * Some cards consume their PIN authorization after a private operation. Reauthorize
 * before the NEXT operation, without repeating a failed command or reopening PACE.
 * No PIN cache survives close, cancellation or a card error.
 */
internal class BatchCardSession(
    private val card: DnieTransceiverInterface,
    pin: String
) : AutoCloseable {
    private val credentials = pin.toCharArray()
    private val mutex = Mutex()
    @Volatile private var closed = false
    private var ready = false
    private var failed = false
    private var signatures = 0

    suspend fun open(): ByteArray = mutex.withLock {
        check(!closed && !ready && !failed) { "La sesión del lote no está disponible" }
        try {
            coroutineContext.ensureActive()
            DiagnosticLog.measureSuspend("signer", "card_open_pace", "protocol") { card.selectPkiApplet().getOrThrow() }
            authorize()
            coroutineContext.ensureActive()
            val certificate = DiagnosticLog.measureSuspend("signer", "card_certificate", "protocol") { card.readCertificate(CertificateType.SIGNATURE).getOrThrow() }
            ready = true
            DiagnosticLog.event("signer", "batch_session_ready")
            certificate
        } catch (e: Exception) {
            failed = true
            credentials.fill('\u0000')
            throw e
        }
    }

    suspend fun sign(hash: ByteArray): Result<ByteArray> = mutex.withLock {
        try {
            check(ready && !closed && !failed) { "La sesión del lote terminó; vuelva a autorizar" }
            require(hash.size == 32) { "El resumen SHA-256 debe tener 32 bytes" }
            coroutineContext.ensureActive()
            if (signatures > 0) authorize()
            coroutineContext.ensureActive()
            check(!closed) { "La sesión del lote está cerrada" }
            val signature = DiagnosticLog.measureSuspend("signer", "card_sign", "protocol") { card.signSha256Hash(hash).getOrThrow() }
            signatures++
            DiagnosticLog.event("signer", "batch_card_signature", "count" to signatures)
            Result.success(signature)
        } catch (e: Exception) {
            failed = true
            credentials.fill('\u0000')
            DiagnosticLog.failure("signer", "batch_card_signature", e)
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun authorize() {
        coroutineContext.ensureActive()
        check(!closed) { "La sesión del lote está cerrada" }
        DiagnosticLog.event("signer", "batch_authorize", "count" to signatures + 1)
        when (val result = DiagnosticLog.measureSuspend("signer", "card_verify_pin", "protocol") { card.verifyPin(String(credentials)).getOrThrow() }) {
            PinVerificationResult.Success -> Unit
            is PinVerificationResult.InvalidPin -> error("PIN incorrecto. Le quedan ${result.attemptsRemaining} intento(s) antes del bloqueo.")
            PinVerificationResult.CardBlocked -> error("El chip está bloqueado (69 83).")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        credentials.fill('\u0000')
        card.close()
    }
}
