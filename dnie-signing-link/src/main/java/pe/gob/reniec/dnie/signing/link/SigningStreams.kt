package pe.gob.reniec.dnie.signing.link

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Supplied ONLY by the future TLS/pinned-pairing or verified-tailnet transport adapter.
 * Streams carry authenticated application data, never an unverified raw network socket.
 * close() must unblock reads/writes; owner closes before cancelling blocked IO. Transport
 * bounds incomplete frames and connect/handshake; this layer creates no socket/listener.
 */
interface SigningDuplex : AutoCloseable {
    val input: InputStream
    val output: OutputStream
}

class StreamSigningExchange(private val channel: SigningDuplex) : SigningExchange {
    private val mutex = Mutex()
    private val closed = AtomicBoolean()
    override suspend fun exchange(request: SigningFrame): SigningFrame = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                check(!closed.get())
                coroutineContext.ensureActive()
                SigningFrames.write(channel.output, request)
                SigningFrames.read(channel.input) ?: error("Private signing peer closed")
            } catch (failure: Exception) { close(); throw failure }
        }
    }
    override fun close() { if (closed.compareAndSet(false, true)) runCatching { channel.close() } }
}

/** Phone-side loop. The endpoint/card is owned and closed on EOF, bad framing or lost reply.
 * Never reopen physical PACE, extend approval, retry a signature or publish a system reader.
 */
class AuthorizedSigningConnection(private val channel: SigningDuplex,
                                  private val endpoint: AuthorizedSigningEndpoint) : AutoCloseable {
    private val used = AtomicBoolean()
    private val closed = AtomicBoolean()
    suspend fun run() = withContext(Dispatchers.IO) {
        check(used.compareAndSet(false, true)) { "Private signing connection cannot be reused" }
        try {
            check(!closed.get())
            while (!closed.get()) {
                coroutineContext.ensureActive()
                val request = SigningFrames.read(channel.input) ?: break
                val response = endpoint.process(request)
                SigningFrames.write(channel.output, response)
                if (request.operation == SigningOperation.CLOSE || response.status != SigningStatus.OK) break
            }
        } finally { close() }
    }
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try { channel.close() } finally { endpoint.close() }
        }
    }
}
