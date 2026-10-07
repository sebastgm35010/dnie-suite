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

/** One already authenticated, explicitly authorized connection. Close must unblock IO and
 * cause the peer owner to close its endpoint. Implementations MUST NOT reconnect/replay.
 * No production network implementation exists yet; TLS/USB/tailnet adapters are next.
 */
interface SigningExchange : AutoCloseable {
    suspend fun exchange(request: SigningFrame): SigningFrame
}

/** Adapter for existing CMS/batch engine. Pass an EMPTY PIN to that engine; authorization
 * invokes the phone's session-local PIN. Neither a fake PIN nor a real PIN crosses the link.
 */
class PrivateSigningCard(private val sessionId: UUID, private val transport: SigningExchange) : DnieTransceiverInterface {
    private val mutex = Mutex()
    private val closed = AtomicBoolean()
    private var sequence = 0L

    private suspend fun <T> execute(operation: SigningOperation, payload: ByteArray = byteArrayOf(),
                                    decode: (SigningFrame) -> T): Result<T> = mutex.withLock {
        try {
            coroutineContext.ensureActive()
            check(!closed.get() && sequence < Long.MAX_VALUE)
            val request = SigningFrame(sessionId, ++sequence, operation, SigningStatus.REQUEST, payload)
            val response = transport.exchange(request)
            check(!closed.get())
            require(response.sessionId == sessionId && response.sequence == sequence && response.operation == operation)
            require(response.status != SigningStatus.REQUEST)
            val value = decode(response)
            if (response.status != SigningStatus.OK) close()
            Result.success(value)
        } catch (failure: Exception) {
            close()
            if (failure is CancellationException) throw failure
            Result.failure(IllegalStateException("Private signing session ended"))
        }
    }

    override suspend fun selectPkiApplet(): Result<Unit> = execute(SigningOperation.OPEN) {
        require(it.status == SigningStatus.OK && it.payload.isEmpty())
    }
    override suspend fun readCertificate(type: CertificateType): Result<ByteArray> {
        if (type != CertificateType.SIGNATURE) return Result.failure(IllegalArgumentException("Only signature certificate is supported"))
        return execute(SigningOperation.CERTIFICATE) {
            require(it.status == SigningStatus.OK && it.payload.isNotEmpty())
            it.payload.copyOf()
        }
    }
    override suspend fun verifyPin(pin: String): Result<PinVerificationResult> {
        if (pin.isNotEmpty()) {
            close()
            return Result.failure(IllegalArgumentException("PIN must remain on the phone"))
        }
        return execute(SigningOperation.AUTHORIZE) {
            when (it.status) {
                SigningStatus.OK -> { require(it.payload.isEmpty()); PinVerificationResult.Success }
                SigningStatus.INVALID_PIN -> {
                    require(it.payload.size == 1)
                    PinVerificationResult.InvalidPin(it.payload[0].toInt() and 255)
                }
                SigningStatus.BLOCKED -> { require(it.payload.isEmpty()); PinVerificationResult.CardBlocked }
                else -> error("Phone authorization ended")
            }
        }
    }
    override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> {
        if (hash32Bytes.size != 32) return Result.failure(IllegalArgumentException("SHA-256 requires 32 bytes"))
        return execute(SigningOperation.SIGN_SHA256, hash32Bytes) {
            require(it.status == SigningStatus.OK && it.payload.size == 256)
            it.payload.copyOf()
        }
    }
    override fun close() { if (closed.compareAndSet(false, true)) runCatching { transport.close() } }
}
