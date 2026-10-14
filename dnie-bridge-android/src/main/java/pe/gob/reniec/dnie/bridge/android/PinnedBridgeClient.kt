package pe.gob.reniec.dnie.bridge.android

import pe.gob.reniec.dnie.bridge.BridgeCard
import pe.gob.reniec.dnie.bridge.ReadOnlyBridge
import pe.gob.reniec.dnie.bridge.TransparentBridge
import pe.gob.reniec.dnie.bridge.BridgeProcessor
import pe.gob.reniec.dnie.bridge.VpcdFrames
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Provisioning secrets must come from a user-approved pairing, never discovery/logs. */
class BridgePairing(
    val host: String,
    val port: Int,
    certificateSha256: ByteArray,
    token: ByteArray,
    val expiresAtEpochMillis: Long,
    val transport: String = "tls",
    val persistent: Boolean = false
) : Closeable {
    internal val pin = certificateSha256.copyOf()
    internal val secret = token.copyOf()
    init {
        require(host.isNotBlank() && host.length <= 253 && port in 1024..65535)
        require(pin.size == 32 && secret.size == 32)
        require(transport in setOf("tls", "tailscale_tcp", "tailscale_sync", "tls_reverse", "usb_accessory"))
        if (transport.startsWith("tailscale_")) require(TailnetAddress.validHost(host))
    }
    fun fingerprint(): String = pin.joinToString(":") { "%02X".format(it) }
    fun discoveryOffer(): AutoCloseable {
        require(transport=="tls_reverse")
        return pe.gob.reniec.dnie.network.PhoneDiscovery.offer(pe.gob.reniec.dnie.network.DiscoveryService.BRIDGE,pin,secret)
    }
    /** Change destination metadata without replacing trusted credentials or transport policy. */
    fun withHost(host: String) = BridgePairing(host, port, pin, secret, expiresAtEpochMillis, transport, persistent)
    override fun close() { pin.fill(0); secret.fill(0) }
}

internal class PinnedTrust(private val pin: ByteArray) : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        throw CertificateException("Client certificates not accepted")
    }
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        if (chain.isEmpty()) throw CertificateException("Missing server certificate")
        chain[0].checkValidity()
        val actual = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
        if (!MessageDigest.isEqual(pin, actual)) throw CertificateException("Pairing certificate mismatch")
    }
}

/** Blocking IO adapter: run off Main, and close() from cancellation/disconnect owner. No logging. */
class PinnedBridgeClient(private val pairing: BridgePairing,
    private val settings: pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings = pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings(),
    private val transportSocket: (() -> Socket)? = null) : Closeable {
    private val lifecycle = Any()
    private var closed = false
    private var used = false
    private var socket: Socket? = null
    private var preAuthWatchdog: ScheduledExecutorService? = null

    private fun openAuthenticatedSocket(direct: Boolean, factory: SSLSocketFactory?, event: (String) -> Unit): Socket {
        val retryableReverse = transportSocket != null && pairing.transport == "tls_reverse"
        val watchdog = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "dnie-bridge-preauth-watchdog").apply { isDaemon = true }
        }
        synchronized(lifecycle) {
            if (closed) {
                watchdog.shutdownNow()
                throw IllegalStateException("Bridge client cannot be reused")
            }
            preAuthWatchdog = watchdog
        }
        val sessionAuthenticated = AtomicBoolean(false)
        val initialRemaining = pairing.expiresAtEpochMillis - System.currentTimeMillis()
        val expiryTask = if (initialRemaining in 1L..3_600_000L) {
            watchdog.schedule({
                if (!sessionAuthenticated.get()) {
                    synchronized(lifecycle) {
                        runCatching { socket?.close() }
                    }
                }
            }, initialRemaining, TimeUnit.MILLISECONDS)
        } else null
        var lastFailure: Exception? = null
        try {
            while (true) {
                if (System.currentTimeMillis() >= pairing.expiresAtEpochMillis) {
                    throw (lastFailure ?: IllegalStateException("Pairing expired"))
                }
                var raw: Socket? = null
                var connection: Socket? = null
                var attemptTask: ScheduledFuture<*>? = null
                val attemptSettled = AtomicBoolean(false)
                try {
                    // Wait for an incoming socket (or create an outbound one) BEFORE starting the per-attempt authentication timer.
                    raw = transportSocket?.invoke()
                    val remainingAfterAccept = pairing.expiresAtEpochMillis - System.currentTimeMillis()
                    if (remainingAfterAccept <= 0L) {
                        runCatching { raw?.close() }
                        throw (lastFailure ?: IllegalStateException("Pairing expired"))
                    }
                    val candidate = if (raw != null) checkNotNull(factory).createSocket(raw, raw.inetAddress.hostAddress, raw.port, true)
                        else if (direct) Socket() else checkNotNull(factory).createSocket()
                    connection = candidate
                    synchronized(lifecycle) {
                        if (closed || System.currentTimeMillis() >= pairing.expiresAtEpochMillis) {
                            runCatching { candidate.close() }
                            runCatching { raw?.close() }
                            throw IllegalStateException("Bridge client cannot be reused")
                        }
                        socket = candidate
                    }
                    val attemptBudgetMs = if (retryableReverse) remainingAfterAccept.coerceIn(1L, 5_000L) else remainingAfterAccept.coerceIn(1L, 30_000L)
                    val currentRaw = raw
                    attemptTask = watchdog.schedule({
                        if (attemptSettled.compareAndSet(false, true)) {
                            runCatching { candidate.close() }
                            runCatching { currentRaw?.close() }
                        }
                    }, attemptBudgetMs, TimeUnit.MILLISECONDS)
                    val attemptTimeout = attemptBudgetMs.toInt()
                    candidate.soTimeout = attemptTimeout // Bound the unauthenticated synchronization/handshake.
                    candidate.tcpNoDelay = true
                    if (raw == null) {
                        val address = if (direct) {
                            InetAddress.getAllByName(pairing.host).firstOrNull(TailnetAddress::isTailnet)
                                ?: error("Tailscale address unavailable")
                        } else InetAddress.getByName(pairing.host)
                        candidate.connect(InetSocketAddress(address, pairing.port), minOf(settings.connectTimeoutMs, attemptTimeout))
                    }
                    if (candidate is SSLSocket) {
                        candidate.enabledProtocols = candidate.supportedProtocols.filter { it in setOf("TLSv1.2", "TLSv1.3") }.toTypedArray()
                        candidate.startHandshake()
                    }
                    event(if (direct) "tailscale_tcp_ready" else "tls_ready")
                    check(System.currentTimeMillis() < pairing.expiresAtEpochMillis) { "Pairing expired" }
                    val input = candidate.inputStream
                    val output = candidate.outputStream
                    val sessionToken = if (pairing.transport == "tailscale_sync") {
                        TailnetSynchronization.request(input, output).also { event("tailscale_synchronized") }
                    } else pairing.secret.copyOf()
                    try {
                        output.write("DNIBRDG1".toByteArray(Charsets.US_ASCII))
                        output.write(sessionToken)
                        output.flush()
                    } finally { sessionToken.fill(0) }
                    check(input.read() == 1) { "Bridge authentication rejected" }
                    check(System.currentTimeMillis() < pairing.expiresAtEpochMillis) { "Pairing expired" }
                    check(attemptSettled.compareAndSet(false, true)) { "Bridge authentication timed out" }
                    sessionAuthenticated.set(true)
                    attemptTask.cancel(false)
                    expiryTask?.cancel(false)
                    synchronized(lifecycle) {
                        runCatching { preAuthWatchdog?.shutdownNow() }
                        preAuthWatchdog = null
                    }
                    event("peer_authorized")
                    return candidate
                } catch (attemptFailure: Exception) {
                    attemptSettled.set(true)
                    attemptTask?.cancel(false)
                    runCatching { connection?.close() }
                    runCatching { raw?.close() }
                    synchronized(lifecycle) {
                        if (socket === connection || socket === raw) socket = null
                        if (closed || !retryableReverse) throw attemptFailure
                    }
                    if (System.currentTimeMillis() >= pairing.expiresAtEpochMillis) {
                        throw (lastFailure ?: attemptFailure)
                    }
                    lastFailure = attemptFailure
                }
            }
        } catch (failure: Exception) {
            expiryTask?.cancel(false)
            synchronized(lifecycle) {
                runCatching { preAuthWatchdog?.shutdownNow() }
                preAuthWatchdog = null
            }
            throw failure
        }
    }

    fun run(event: (String) -> Unit = {}, restricted: Boolean = true, authorizedCard: () -> BridgeCard) {
        val direct = pairing.transport.startsWith("tailscale_")
        val factory = if (direct) null else SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(PinnedTrust(pairing.pin)), null)
        }.socketFactory
        synchronized(lifecycle) {
            if (closed || used) {
                throw IllegalStateException("Bridge client cannot be reused")
            }
            used = true
        }
        try {
            check(System.currentTimeMillis() < pairing.expiresAtEpochMillis) { "Pairing expired" }
            val connection = openAuthenticatedSocket(direct, factory, event)
            val input = connection.inputStream
            val output = connection.outputStream
            connection.soTimeout = 0 // Authenticated idle sessions have no lifetime cap.
            val card = authorizedCard()
            val processor: BridgeProcessor = if (restricted) ReadOnlyBridge(card) else TransparentBridge(card)
            processor.use { bridge ->
                while (!connection.isClosed) {
                    // Wait for the first byte indefinitely; bound incomplete frames.
                    val first = input.read()
                    if (first < 0) break
                    connection.soTimeout = settings.readTimeoutMs
                    val frame = VpcdFrames.read(object : java.io.InputStream() {
                        var pending = true
                        override fun read(): Int = if (pending) { pending = false; first } else input.read()
                        override fun read(b: ByteArray, off: Int, len: Int): Int =
                            if (pending) { if (len == 0) 0 else { b[off] = first.toByte(); pending = false; 1 } }
                            else input.read(b, off, len)
                    }) ?: break
                    connection.soTimeout = 0
                    try {
                        val response = bridge.process(frame)
                        if (response != null) {
                            try { VpcdFrames.write(output, response) } finally { response.fill(0) }
                        }
                    } finally { frame.fill(0) }
                }
            }
        } finally { close() }
    }
    override fun close() {
        synchronized(lifecycle) {
            if (!closed) {
                closed = true
                runCatching { preAuthWatchdog?.shutdownNow() }
                preAuthWatchdog = null
                socket?.close()
                socket = null
            }
        }
    }
}
