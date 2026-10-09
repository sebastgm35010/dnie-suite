package pe.gob.reniec.dnie.signing.link

import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
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
                            private val continuous: Boolean = false) : AutoCloseable {
    init { require(port in 1024..65535) }
    private val closed = AtomicBoolean()
    private val used = AtomicBoolean()
    private val lifecycle = Any()
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var socket: Socket? = null
    fun accept(onPeerAddress: (String) -> Unit = {}, onEvent: (String) -> Unit = {}): SigningDuplex {
        check(used.compareAndSet(false, true)) { "Request already used" }
        check(System.currentTimeMillis() < provision.expiresAt)
        val server = ServerSocket().apply { reuseAddress = true }
        synchronized(lifecycle) { if (closed.get()) { server.close(); error("Closed") }; listener = server }
        try {
            server.bind(java.net.InetSocketAddress(port), 1)
            onEvent("listener_ready")
            server.soTimeout = (provision.expiresAt - System.currentTimeMillis()).coerceIn(1, 300_000).toInt()
            val raw = server.accept()
            synchronized(lifecycle) {
                if (closed.get()) { raw.close(); error("Session closed") }
                socket = raw
            }
            server.close(); listener = null
            // The saved IP is a pairing hint, not identity. DHCP/interface changes are allowed.
            val peer = ReverseTlsPeer.requirePrivate(raw.inetAddress)
            onEvent("peer_route_allowed")
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
            val tls = context.socketFactory.createSocket(raw, peer, raw.port, true) as SSLSocket
            synchronized(lifecycle) { if (closed.get()) { tls.close(); error("Session closed") }; socket = tls }
            tls.enabledProtocols = tls.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
            tls.soTimeout = 30_000
            tls.startHandshake()
            onEvent("tls_ready")
            check(System.currentTimeMillis() < provision.expiresAt)
            tls.outputStream.write("DNISIGN1".toByteArray(Charsets.US_ASCII))
            tls.outputStream.write(provision.token); tls.outputStream.flush()
            check(tls.inputStream.read() == 1) { "Suite authorization rejected" }
            onEvent("peer_authorized")
            onPeerAddress(peer) // Publish the address only after pinned TLS and token authorization.
            // Idle input is bounded in this one-job prototype; never replay after timeout.
            tls.soTimeout = if (continuous) 0 else 300_000
            return object : SigningDuplex {
                override val input = tls.inputStream
                override val output = tls.outputStream
                override fun close() = this@PhoneSigningTransport.close()
            }
        } catch (failure: Exception) { close(); throw failure }
    }
    override fun close() {
        synchronized(lifecycle) {
            if (closed.compareAndSet(false, true)) {
                runCatching { listener?.close() }; runCatching { socket?.close() }
                listener = null; socket = null
            }
        }
    }
}
