package pe.gob.reniec.dnie.bridge.android

import java.net.ServerSocket
import java.net.Socket
import java.math.BigInteger
import java.security.*
import java.util.Date
import java.util.concurrent.*
import javax.net.ssl.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.*
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.bridge.BridgeCard

class BridgeAddressMobilityTest {
    @Test fun editingDestinationKeepsPinTokenTransportAndIndependentSecretOwnership() {
        val original = BridgePairing("192.168.1.5", 39443, ByteArray(32) { 7 }, ByteArray(32) { 9 }, Long.MAX_VALUE, "tls", true)
        val updated = original.withHost("192.168.2.8")
        original.close()
        updated.use {
            assertEquals("192.168.2.8", it.host)
            assertEquals("tls", it.transport); assertTrue(it.persistent)
            assertEquals(Long.MAX_VALUE, it.expiresAtEpochMillis)
            assertArrayEquals(ByteArray(32) { 7 }, it.pin); assertArrayEquals(ByteArray(32) { 9 }, it.secret)
        }
    }

    @Test fun reverseTlsUsesAcceptedSocketInsteadOfResolvingHistoricalHostAndRejectsWrongPin() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=Synthetic mobility peer")
        val now = System.currentTimeMillis()
        val cert = JcaX509CertificateConverter().getCertificate(JcaX509v3CertificateBuilder(name, BigInteger.ONE,
            Date(now - 60_000), Date(now + 600_000), name, key.public)
            .build(JcaContentSignerBuilder("SHA256withRSA").build(key.private)))
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null, null); setKeyEntry("test", key.private, "test".toCharArray(), arrayOf(cert))
        }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "test".toCharArray()) }
        val ssl = SSLContext.getInstance("TLS").apply { init(km.keyManagers, null, null) }
        repeat(2) { wrongPin ->
            val listener = ServerSocket(0)
            val pool = Executors.newSingleThreadExecutor()
            val connected = CountDownLatch(1)
            val peer = pool.submit(Callable {
                runCatching {
                    val raw = Socket("127.0.0.1", listener.localPort)
                    connected.countDown()
                    (ssl.socketFactory.createSocket(raw, "127.0.0.1", listener.localPort, true) as SSLSocket).use {
                        it.useClientMode = false; it.soTimeout = 5000; it.startHandshake()
                        val hello = it.inputStream.readNBytes(40)
                        assertEquals("DNIBRDG1", hello.take(8).toByteArray().toString(Charsets.US_ASCII))
                        assertArrayEquals(ByteArray(32) { 9 }, hello.drop(8).toByteArray())
                        it.outputStream.write(1); it.outputStream.flush()
                    }
                }
            })
            val fingerprint = if (wrongPin == 0) MessageDigest.getInstance("SHA-256").digest(cert.encoded) else ByteArray(32)
            // DNS is intentionally unavailable: reverse sockets must never resolve this hint.
            val pair = BridgePairing("old-pc.invalid", 39443, fingerprint, ByteArray(32) { 9 }, Long.MAX_VALUE, "tls_reverse")
            val client = PinnedBridgeClient(pair, transportSocket = { listener.accept() })
            var cards = 0
            val card = object : BridgeCard {
                override val atr = byteArrayOf(0x3b, 0)
                override fun transmit(command: ByteArray) = byteArrayOf(0x90.toByte(), 0)
                override fun close() {}
            }
            try {
                assertTrue(connected.await(5, TimeUnit.SECONDS))
                val outcome = runCatching { client.run { cards++; card } }
                if (wrongPin == 0) { outcome.getOrThrow(); assertEquals(1, cards); peer.get(5, TimeUnit.SECONDS).getOrThrow() }
                else { assertTrue(outcome.isFailure); assertEquals(0, cards); assertTrue(peer.get(5, TimeUnit.SECONDS).isFailure) }
            } finally { client.close(); pair.close(); listener.close(); pool.shutdownNow() }
        }
    }
}
