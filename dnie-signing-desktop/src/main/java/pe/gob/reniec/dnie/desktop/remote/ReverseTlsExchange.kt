package pe.gob.reniec.dnie.desktop.remote

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.io.DataInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import pe.gob.reniec.dnie.signing.link.*

class ReverseTlsExchange(private val phone: String, private val provision: SigningProvision,
                         private val identity: JobTlsIdentity,
                         private val port: Int = SigningProvision.PHONE_PORT,
                         private val continuous: Boolean = false,
                         private val autoDiscover: Boolean = port==SigningProvision.PHONE_PORT) : SigningExchange {
    init { require((phone.isEmpty() || SigningProvision.privateAddress(phone) || NetworkTargets.tailnetHost(phone)) && port in 1024..65535) }
    @Volatile var connectedPhone: String=""
        private set
    @Volatile var connectedPc: String=""
        private set
    private val closed = AtomicBoolean()
    private val lifecycle = Any()
    @Volatile private var socket: Socket? = null
    private var exchange: StreamSigningExchange? = null
    override suspend fun exchange(request: SigningFrame): SigningFrame = withContext(Dispatchers.IO) {
        check(!closed.get())
        val current = exchange ?: StreamSigningExchange(openChannel()).also { exchange = it }
        current.exchange(request)
    }
    suspend fun openChannel(): SigningDuplex {
        val pc = InetAddress.getByName(provision.pcAddress)
        while (System.currentTimeMillis() < provision.expiresAt) {
            coroutineContext.ensureActive(); check(!closed.get())
            val context=coroutineContext
            val hints=NetworkTargets.resolveHint(phone)
            val discovered=if(autoDiscover) pe.gob.reniec.dnie.network.DiscoveryClient.find(
                pe.gob.reniec.dnie.network.DiscoveryService.SUITE,provision.fingerprint,provision.token,
                (pe.gob.reniec.dnie.network.DiscoveryClient.broadcasts()+hints+NetworkTargets.tailnetPeers()).distinct().take(64)) {
                    context.ensureActive();check(!closed.get())
                } else emptyList()
            val address=(discovered+hints).firstOrNull()
            if(address==null) {kotlinx.coroutines.delay(500);continue}
            val raw = Socket()
            synchronized(lifecycle) { if (closed.get()) { raw.close(); error("Closed") }; socket = raw }
            try {
                if(!autoDiscover)raw.bind(InetSocketAddress(pc, 0))
                raw.connect(InetSocketAddress(address, port), 1000)
            } catch (failure: java.io.IOException) {
                raw.close()
                if (closed.get()) throw failure
                kotlinx.coroutines.delay(500)
                continue // Only connect retries BEFORE TLS, authorization or any card request.
            }
            try {
                val tls = identity.context.socketFactory.createSocket(raw, address.hostAddress, port, true) as SSLSocket
                synchronized(lifecycle) { if (closed.get()) { tls.close(); error("Closed") }; socket = tls }
                tls.useClientMode = false
                tls.enabledProtocols = tls.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
                tls.soTimeout = 30_000
                tls.startHandshake()
                val input = DataInputStream(tls.inputStream)
                val magic = ByteArray(8); val token = ByteArray(32)
                try {
                    input.readFully(magic); input.readFully(token)
                    require(magic.contentEquals("DNISIGN1".toByteArray(Charsets.US_ASCII)))
                    check(System.currentTimeMillis() < provision.expiresAt && MessageDigest.isEqual(token, provision.token))
                } finally { token.fill(0) }
                tls.outputStream.write(1); tls.outputStream.flush()
                connectedPhone=address.hostAddress
                connectedPc=raw.localAddress.hostAddress
                tls.soTimeout = if (continuous) 0 else 300_000
                return object : SigningDuplex {
                    override val input = tls.inputStream
                    override val output = tls.outputStream
                    override fun close() { tls.close() }
                }
            } catch (failure: Exception) { close(); throw failure }
        }
        close(); error("Suite request expired")
    }
    override fun close() {
        synchronized(lifecycle) { if (closed.compareAndSet(false, true)) {
            runCatching { socket?.close() }; exchange?.close(); socket = null
        } }
    }
}
