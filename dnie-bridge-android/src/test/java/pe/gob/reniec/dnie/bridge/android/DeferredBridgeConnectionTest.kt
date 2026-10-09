package pe.gob.reniec.dnie.bridge.android

import org.junit.Assert.*
import org.junit.Test
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import pe.gob.reniec.dnie.bridge.BridgeMode
import java.math.BigInteger
import java.security.*
import java.util.Date
import java.util.concurrent.*
import javax.net.ssl.*

class DeferredBridgeConnectionTest {
    @Test fun realTlsAuthenticatesBeforeAnyCardAndStopUnblocksPhysicalWait() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val name = X500Name("CN=Synthetic deferred bridge")
        val cert = JcaX509CertificateConverter().getCertificate(JcaX509v3CertificateBuilder(name, BigInteger.ONE,
            Date(now - 60_000), Date(now + 600_000), name, key.public)
            .build(JcaContentSignerBuilder("SHA256withRSA").build(key.private)))
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null, null); setKeyEntry("test", key.private, "test".toCharArray(), arrayOf(cert))
        }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "test".toCharArray()) }
        val ssl = SSLContext.getInstance("TLS").apply { init(km.keyManagers, null, null) }
        val server = ssl.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        server.soTimeout = 5000
        val executor = Executors.newFixedThreadPool(2)
        val waiting = CountDownLatch(1)
        val pending = PendingCard<ByteArray>()
        var physical = 0
        val pair = BridgePairing("127.0.0.1", server.localPort, MessageDigest.getInstance("SHA-256").digest(cert.encoded), ByteArray(32), Long.MAX_VALUE)
        val connection = BridgeConnection(pair, cancelPendingCard = pending::close) { physical++; error("No card should open") }
        try {
            val peer = executor.submit {
                (server.accept() as SSLSocket).use { socket ->
                    socket.soTimeout = 5000; socket.startHandshake()
                    val hello = socket.inputStream.readNBytes(40)
                    assertEquals(40, hello.size)
                    socket.outputStream.write(1); socket.outputStream.flush()
                    assertEquals(-1, socket.inputStream.read())
                }
            }
            val phone = executor.submit(Callable {
                runCatching { connection.runAwaitingCard("123456", BridgeMode.DIRECT_WINDOWS_PACE, pending::await) {
                    if (it == BridgeConnection.Phase.WAITING_CARD) waiting.countDown()
                } }
            })
            assertTrue(waiting.await(5, TimeUnit.SECONDS))
            assertEquals(0, physical)
            connection.close()
            assertTrue(phone.get(5, TimeUnit.SECONDS).isFailure)
            peer.get(5, TimeUnit.SECONDS)
            assertEquals(0, physical)
        } finally { connection.close(); pair.close(); server.close(); executor.shutdownNow() }
    }
}
