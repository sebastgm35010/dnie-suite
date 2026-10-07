package pe.gob.reniec.dnie.signing.link

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult

/** Create ONLY after authenticated peer + explicit phone approval of this batch.
 * Owns an already prepared transceiver; never negotiates PC-PACE or exposes arbitrary APDU.
 * PIN belongs to the phone. A session cannot be resumed, extended or retried after failure.
 * This protocol is not encryption/authentication: caller must supply the trusted transport.
 */
class AuthorizedSigningEndpoint(
    private val sessionId: UUID,
    private val card: DnieTransceiverInterface,
    pin: CharArray,
    private val approvedSignatures: Int,
    private val lifetimeMillis: Long = 300_000,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) : AutoCloseable {
    init {
        require(pin.size in 4..8 && pin.all { it in '0'..'9' })
        require(approvedSignatures in 1..50 && lifetimeMillis in 1..3_600_000)
    }
    private val credentials = pin.copyOf()
    private val start = monotonicMillis()
    private val mutex = Mutex()
    private val closed = AtomicBoolean()
    private var nextSequence = 1L
    private var selected = false
    private var authorized = false
    private var certificateRead = false
    private var signatures = 0

    suspend fun process(request: SigningFrame): SigningFrame = mutex.withLock {
        fun response(status: SigningStatus, payload: ByteArray = byteArrayOf()) =
            SigningFrame(request.sessionId, request.sequence, request.operation, status, payload)
        try {
            coroutineContext.ensureActive()
            check(!closed.get() && request.sessionId == sessionId && request.sequence == nextSequence)
            check(request.status == SigningStatus.REQUEST && nextSequence < Long.MAX_VALUE)
            check(monotonicMillis() - start in 0 until lifetimeMillis)
            nextSequence++ // Consume before execution, including failed private commands.
            if (request.operation == SigningOperation.SIGN_SHA256) require(request.payload.size == 32)
            else require(request.payload.isEmpty())
            when (request.operation) {
                SigningOperation.OPEN -> {
                    check(!selected)
                    card.selectPkiApplet().getOrThrow()
                    selected = true
                    response(SigningStatus.OK)
                }
                SigningOperation.AUTHORIZE -> {
                    check(selected && !authorized && signatures < approvedSignatures)
                    when (val result = card.verifyPin(String(credentials)).getOrThrow()) {
                        PinVerificationResult.Success -> { authorized = true; response(SigningStatus.OK) }
                        is PinVerificationResult.InvalidPin -> {
                            val remaining = result.attemptsRemaining
                            require(remaining in 0..255)
                            close()
                            response(SigningStatus.INVALID_PIN, byteArrayOf(remaining.toByte()))
                        }
                        PinVerificationResult.CardBlocked -> { close(); response(SigningStatus.BLOCKED) }
                    }
                }
                SigningOperation.CERTIFICATE -> {
                    check(selected && authorized && !certificateRead)
                    val cert = card.readCertificate(CertificateType.SIGNATURE).getOrThrow()
                    require(cert.isNotEmpty() && cert.size <= SigningFrames.MAX_PAYLOAD)
                    certificateRead = true
                    response(SigningStatus.OK, cert)
                }
                SigningOperation.SIGN_SHA256 -> {
                    check(selected && certificateRead && authorized && signatures < approvedSignatures)
                    authorized = false
                    signatures++
                    val signature = card.signSha256Hash(request.payload.copyOf()).getOrThrow()
                    require(signature.size == 256)
                    response(SigningStatus.OK, signature)
                }
                SigningOperation.CLOSE -> { close(); response(SigningStatus.OK) }
            }
        } catch (failure: Exception) {
            close()
            if (failure is CancellationException) throw failure
            response(SigningStatus.FAILED) // Never send physical exceptions / APDU / secrets.
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            credentials.fill('\u0000')
            runCatching { card.close() }
        }
    }
}
