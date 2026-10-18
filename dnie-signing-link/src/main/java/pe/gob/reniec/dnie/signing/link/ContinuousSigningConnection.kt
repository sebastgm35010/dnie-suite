package pe.gob.reniec.dnie.signing.link

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface

/** Authenticated PC is authorized by the phone for the foreground connection, not forever.
 * A BEGIN supplies a fresh PC PIN and budget. No APDU/reset path and no replay/reconnect.
 */
class ContinuousSigningConnection(private val channel: SigningDuplex,
    private val acquireCard: suspend () -> DnieTransceiverInterface,
    private val invalidateCard: () -> Unit,
    private val onJob: (Int) -> Unit = {},
    private val onStatus: (SigningStatus) -> Unit = {},
    private val onIdle: () -> Unit = {},
    private val finishJob: () -> Unit = {},
    private val onOperation: (SigningOperation, SigningStatus) -> Unit = { _, _ -> },
    private val cardWaitMillis: Long = 120_000,
    private val onUnavailable: () -> Unit = {}) : AutoCloseable {
    init { require(cardWaitMillis in 1..120_000) }
    private val closed = AtomicBoolean()
    private val used = AtomicBoolean()
    private val stateLock = Any()
    @Volatile private var endpoint: AuthorizedSigningEndpoint? = null
    private val seen = mutableSetOf<UUID>()

    suspend fun run() = withContext(Dispatchers.IO) {
        check(used.compareAndSet(false, true))
        try {
            while (!closed.get()) {
                val begin = SuitePackets.read(channel.input) ?: break
                check(synchronized(stateLock) { endpoint == null })
                val job = try { SuitePackets.job(begin) } finally { begin.bytes.fill(0) }
                val activeEndpoint: AuthorizedSigningEndpoint = try {
                    job.use {
                        check(seen.size < 4096 && seen.add(job.id))
                        onJob(job.count)
                        val card = kotlinx.coroutines.withTimeout(cardWaitMillis) { acquireCard() }
                        val created = AuthorizedSigningEndpoint(job.id, card, job.pin, job.count)
                        synchronized(stateLock) {
                            if (closed.get()) {
                                created.close()
                                error("Connection closed")
                            }
                            endpoint = created
                        }
                        created
                    }
                } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                    SuitePackets.write(channel.output, SuitePacket(SuitePacketKind.NOT_READY))
                    onIdle(); onUnavailable(); continue // No card/PIN/signature operation was started.
                }
                var jobCompletedCleanly = false
                try {
                    SuitePackets.write(channel.output, SuitePacket(SuitePacketKind.READY))
                    var failed = false
                    while (!closed.get()) {
                        val packet = SuitePackets.read(channel.input) ?: error("Connection lost during job")
                        if (packet.kind == SuitePacketKind.END) {
                            require(packet.bytes.isEmpty())
                            synchronized(stateLock) {
                                if (endpoint === activeEndpoint) endpoint = null
                            }
                            activeEndpoint.close()
                            finishJob()
                            jobCompletedCleanly = true
                            SuitePackets.write(channel.output, SuitePacket(SuitePacketKind.ENDED))
                            onIdle(); break
                        }
                        check(!failed && packet.kind == SuitePacketKind.FRAME)
                        val response = activeEndpoint.process(SuitePackets.frame(packet))
                        onStatus(response.status)
                        onOperation(response.operation, response.status)
                        SuitePackets.write(channel.output, SuitePackets.frame(SuitePacketKind.REPLY, response))
                        if (response.status != SigningStatus.OK || response.operation == SigningOperation.CLOSE) {
                            failed = true
                            activeEndpoint.close()
                            invalidateCard()
                        }
                    }
                } finally {
                    val detached = synchronized(stateLock) {
                        if (endpoint === activeEndpoint) {
                            endpoint = null
                            true
                        } else false
                    }
                    activeEndpoint.close()
                    if (detached && !jobCompletedCleanly) {
                        invalidateCard()
                    }
                }
            }
        } finally { close() }
    }

    override fun close() {
        val current = synchronized(stateLock) {
            val firstClose = closed.compareAndSet(false, true)
            val active = endpoint
            endpoint = null
            if (firstClose) active to true else active to false
        }
        try {
            if (current.second) channel.close()
        } finally {
            current.first?.close()
            if (current.second) invalidateCard()
        }
    }
}

class SuiteCardUnavailableException : IllegalStateException("DNI not available; no signature started")

/** One job borrows the channel. Ending it does not end TLS; a transport failure does. */
class ContinuousSigningClient(private val channel: SigningDuplex) : AutoCloseable {
    private var active = false
    private val closed = AtomicBoolean()
    suspend fun begin(id: UUID, count: Int, pin: CharArray): SigningExchange = withContext(Dispatchers.IO) {
        check(!closed.get() && !active)
        val packet = SuitePackets.begin(id, count, pin)
        pin.fill('\u0000')
        try {
            try { SuitePackets.write(channel.output, packet) } finally { packet.bytes.fill(0) }
            val ready = SuitePackets.read(channel.input) ?: error("Peer closed")
            if (ready.kind == SuitePacketKind.NOT_READY) {
                require(ready.bytes.isEmpty()); throw SuiteCardUnavailableException()
            }
            require(ready.kind == SuitePacketKind.READY && ready.bytes.isEmpty())
            active = true
        } catch (e: SuiteCardUnavailableException) { throw e }
        catch (e: Exception) { close(); throw e } finally { packet.bytes.fill(0) }
        object : SigningExchange {
            private var ended = false
            override suspend fun exchange(request: SigningFrame): SigningFrame = withContext(Dispatchers.IO) {
                check(!ended && !closed.get())
                try {
                    SuitePackets.write(channel.output, SuitePackets.frame(SuitePacketKind.FRAME, request))
                    val response = SuitePackets.read(channel.input) ?: error("Reply lost")
                    require(response.kind == SuitePacketKind.REPLY)
                    SuitePackets.frame(response)
                } catch (e: Exception) { this@ContinuousSigningClient.close(); throw e }
            }
            override fun close() {
                if (ended) return
                ended = true
                if (!closed.get()) try {
                    SuitePackets.write(channel.output, SuitePacket(SuitePacketKind.END))
                    val response = SuitePackets.read(channel.input) ?: error("Job close lost")
                    require(response.kind == SuitePacketKind.ENDED && response.bytes.isEmpty())
                } catch (_: Exception) { this@ContinuousSigningClient.close() }
                active = false
            }
        }
    }
    override fun close() { if (closed.compareAndSet(false, true)) channel.close() }
}
