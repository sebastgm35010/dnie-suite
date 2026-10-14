package pe.gob.reniec.dnie.signing.link

import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Reverse TCP, but PC remains TLS server. No keys/documents reach a plaintext stream.
 * Owns listener and socket immediately, so close can cancel accept/handshake/read.
 */
class PhoneSigningTransport(private val provision: SigningProvision,
                            private val port: Int = SigningProvision.PHONE_PORT,
                            private val continuous: Boolean = false,
                            private val preAuthAttemptTimeoutMs: Long = 5_000L) : AutoCloseable {
    init {
        require(port in 1024..65535)
        require(preAuthAttemptTimeoutMs in 100L..300_000L)
    }
    private val closed = AtomicBoolean()
    private val used = AtomicBoolean()
    private val lifecycle = Any()
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var preAuthWatchdog: ScheduledExecutorService? = null
    fun accept(onPeerAddress: (String) -> Unit = {}, onEvent: (String) -> Unit = {}): SigningDuplex {
        check(used.compareAndSet(false, true)) { "Request already used" }
        check(System.currentTimeMillis() < provision.expiresAt)
        val server = ServerSocket().apply { reuseAddress = true }
        val watchdog = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "dnie-preauth-watchdog").apply { isDaemon = true }
        }
        synchronized(lifecycle) {
            if (closed.get()) {
                server.close()
                watchdog.shutdownNow()
                error("Closed")
            }
            listener = server
            preAuthWatchdog = watchdog
        }
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException()
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                require(chain.isNotEmpty())
                chain[0].checkValidity()
                if (!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(chain[0].encoded), provision.fingerprint))
                    throw CertificateException("Suite identity mismatch")
            }
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), null) }
        val sessionAuthenticated = AtomicBoolean(false)
        val expiryDelayMs = (provision.expiresAt - System.currentTimeMillis()).coerceAtLeast(1L)
        val expiryTask = watchdog.schedule({
            if (!sessionAuthenticated.get()) {
                synchronized(lifecycle) {
                    runCatching { listener?.close() }
                    runCatching { socket?.close() }
                }
            }
        }, expiryDelayMs, TimeUnit.MILLISECONDS)
        try {
            server.bind(java.net.InetSocketAddress(port), 1)
            onEvent("listener_ready")
            var lastFailure: Exception? = null
            while (true) {
                val remaining = provision.expiresAt - System.currentTimeMillis()
                if (remaining <= 0L) throw (lastFailure ?: SocketTimeoutException("Signing provision expired"))
                server.soTimeout = remaining.coerceIn(1L, 300_000L).toInt()
                val raw = try {
                    server.accept()
                } catch (acceptFailure: Exception) {
                    if (System.currentTimeMillis() >= provision.expiresAt) {
                        throw (lastFailure ?: SocketTimeoutException("Signing provision expired").apply { initCause(acceptFailure) })
                    }
                    if (acceptFailure is SocketTimeoutException) throw (lastFailure ?: acceptFailure)
                    throw acceptFailure
                }
                synchronized(lifecycle) {
                    if (closed.get()) { raw.close(); error("Session closed") }
                    socket = raw
                }
                var tls: SSLSocket? = null
                val attemptSettled = AtomicBoolean(false)
                val attemptBudgetMs = (provision.expiresAt - System.currentTimeMillis()).coerceIn(1L, preAuthAttemptTimeoutMs)
                val attemptTask = watchdog.schedule({
                    if (attemptSettled.compareAndSet(false, true)) {
                        runCatching { tls?.close() }
                        runCatching { raw.close() }
                    }
                }, attemptBudgetMs, TimeUnit.MILLISECONDS)
                try {
                    // The saved IP is a pairing hint, not identity. DHCP/interface changes are allowed.
                    val peer = ReverseTlsPeer.requirePrivate(raw.inetAddress)
                    onEvent("peer_route_allowed")
                    val attemptTimeout = attemptBudgetMs.toInt()
                    raw.soTimeout = attemptTimeout
                    val candidate = context.socketFactory.createSocket(raw, peer, raw.port, true) as SSLSocket
                    tls = candidate
                    synchronized(lifecycle) {
                        if (closed.get() || System.currentTimeMillis() >= provision.expiresAt) {
                            candidate.close()
                            error("Session closed")
                        }
                        socket = candidate
                    }
                    candidate.enabledProtocols = candidate.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
                    candidate.soTimeout = attemptTimeout
                    candidate.startHandshake()
                    onEvent("tls_ready")
                    check(System.currentTimeMillis() < provision.expiresAt) { "Signing provision expired" }
                    candidate.outputStream.write("DNISIGN1".toByteArray(Charsets.US_ASCII))
                    candidate.outputStream.write(provision.token); candidate.outputStream.flush()
                    check(candidate.inputStream.read() == 1) { "Suite authorization rejected" }
                    check(System.currentTimeMillis() < provision.expiresAt) { "Signing provision expired" }
                    check(attemptSettled.compareAndSet(false, true)) { "Signing authentication timed out" }
                    sessionAuthenticated.set(true)
                    attemptTask.cancel(false)
                    expiryTask.cancel(false)
                    synchronized(lifecycle) {
                        runCatching { preAuthWatchdog?.shutdownNow() }
                        preAuthWatchdog = null
                        runCatching { server.close() }
                        listener = null
                    }
                    onEvent("peer_authorized")
                    onPeerAddress(peer) // Publish the address only after pinned TLS and token authorization.
                    // Idle input is bounded in this one-job prototype; never replay after timeout.
                    candidate.soTimeout = if (continuous) 0 else 300_000
                    return object : SigningDuplex {
                        override val input = candidate.inputStream
                        override val output = candidate.outputStream
                        override fun close() = this@PhoneSigningTransport.close()
                    }
                } catch (attemptFailure: Exception) {
                    attemptSettled.set(true)
                    attemptTask.cancel(false)
                    runCatching { tls?.close() }
                    runCatching { raw.close() }
                    synchronized(lifecycle) {
                        if (socket === tls || socket === raw) socket = null
                        if (closed.get()) throw attemptFailure
                    }
                    if (System.currentTimeMillis() >= provision.expiresAt) {
                        throw (lastFailure ?: SocketTimeoutException("Signing provision expired").apply { initCause(attemptFailure) })
                    }
                    lastFailure = attemptFailure
                }
            }
        } catch (failure: Exception) {
            expiryTask.cancel(false)
            close()
            throw failure
        }
    }
    override fun close() {
        synchronized(lifecycle) {
            if (closed.compareAndSet(false, true)) {
                runCatching { preAuthWatchdog?.shutdownNow() }
                runCatching { listener?.close() }
                runCatching { socket?.close() }
                preAuthWatchdog = null
                listener = null
                socket = null
            }
        }
    }
}
