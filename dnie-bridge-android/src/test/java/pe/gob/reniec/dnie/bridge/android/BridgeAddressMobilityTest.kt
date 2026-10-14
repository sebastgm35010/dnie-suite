package pe.gob.reniec.dnie.bridge.android

import java.net.ServerSocket
import java.net.Socket
import java.math.BigInteger
import java.security.*
import java.util.Collections
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
            val listener = ServerSocket(0).apply { soTimeout = 600 }
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
            val expiresAt = if (wrongPin == 0) Long.MAX_VALUE else System.currentTimeMillis() + 600
            // DNS is intentionally unavailable: reverse sockets must never resolve this hint.
            val pair = BridgePairing("old-pc.invalid", 39443, fingerprint, ByteArray(32) { 9 }, expiresAt, "tls_reverse")
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

    @Test fun reverseTlsSurvivesPreAuthDisconnectAndRejectedTokenBeforeAcceptingValidPeer() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=Synthetic retry mobility peer")
        val now = System.currentTimeMillis()
        val cert = JcaX509CertificateConverter().getCertificate(JcaX509v3CertificateBuilder(name, BigInteger.ONE,
            Date(now - 60_000), Date(now + 600_000), name, key.public)
            .build(JcaContentSignerBuilder("SHA256withRSA").build(key.private)))
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null, null); setKeyEntry("test", key.private, "test".toCharArray(), arrayOf(cert))
        }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "test".toCharArray()) }
        val ssl = SSLContext.getInstance("TLS").apply { init(km.keyManagers, null, null) }
        val listener = ServerSocket(0).apply { soTimeout = 5000 }
        val pool = Executors.newSingleThreadExecutor()
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        val pair = BridgePairing("old-pc.invalid", 39443, fingerprint, ByteArray(32) { 9 }, System.currentTimeMillis() + 10_000, "tls_reverse")
        val client = PinnedBridgeClient(pair, transportSocket = { listener.accept() })
        var cards = 0
        val card = object : BridgeCard {
            override val atr = byteArrayOf(0x3b, 0)
            override fun transmit(command: ByteArray) = byteArrayOf(0x90.toByte(), 0)
            override fun close() {}
        }
        val sequence = pool.submit(Callable {
            // 1. Unauthenticated TCP probe + immediate close
            Socket("127.0.0.1", listener.localPort).use { }
            // 2. TLS peer that rejects token (writes 0)
            Socket("127.0.0.1", listener.localPort).use { raw ->
                (ssl.socketFactory.createSocket(raw, "127.0.0.1", listener.localPort, true) as SSLSocket).use {
                    it.useClientMode = false; it.soTimeout = 5000; it.startHandshake()
                    it.inputStream.readNBytes(40)
                    it.outputStream.write(0); it.outputStream.flush()
                }
            }
            // 3. Valid TLS peer that accepts token (writes 1)
            Socket("127.0.0.1", listener.localPort).use { raw ->
                (ssl.socketFactory.createSocket(raw, "127.0.0.1", listener.localPort, true) as SSLSocket).use {
                    it.useClientMode = false; it.soTimeout = 5000; it.startHandshake()
                    val hello = it.inputStream.readNBytes(40)
                    assertEquals("DNIBRDG1", hello.take(8).toByteArray().toString(Charsets.US_ASCII))
                    assertArrayEquals(ByteArray(32) { 9 }, hello.drop(8).toByteArray())
                    it.outputStream.write(1); it.outputStream.flush()
                }
            }
        })
        try {
            client.run { cards++; card }
            sequence.get(5, TimeUnit.SECONDS)
            assertEquals(1, cards)
        } finally {
            client.close(); pair.close(); listener.close(); pool.shutdownNow()
        }
    }

    @Test fun validPeerArrivingAfterIdleWaitReceivesFullAuthenticationBudget() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=Synthetic delayed valid peer")
        val now = System.currentTimeMillis()
        val cert = JcaX509CertificateConverter().getCertificate(JcaX509v3CertificateBuilder(name,
            BigInteger.ONE, Date(now - 60_000), Date(now + 600_000), name, key.public)
            .build(JcaContentSignerBuilder("SHA256withRSA").build(key.private)))
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null, null); setKeyEntry("test", key.private, "test".toCharArray(), arrayOf(cert))
        }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "test".toCharArray()) }
        val ssl = SSLContext.getInstance("TLS").apply { init(km.keyManagers, null, null) }
        val listener = ServerSocket(0).apply { soTimeout = 15_000 }
        val entered = CountDownLatch(1)
        val pool = Executors.newCachedThreadPool()
        val pair = BridgePairing("old-pc.invalid", 39443, MessageDigest.getInstance("SHA-256").digest(cert.encoded),
            ByteArray(32) { 9 }, Long.MAX_VALUE, "tls_reverse", true)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val client = PinnedBridgeClient(pair, transportSocket = { entered.countDown(); listener.accept() })
        var cards = 0
        val card = object : BridgeCard {
            override val atr = byteArrayOf(0x3b, 0)
            override fun transmit(command: ByteArray) = byteArrayOf(0x90.toByte(), 0)
            override fun close() {}
        }
        val worker = pool.submit<Throwable?> { runCatching { client.run(event = { events += it }) { cards++; card } }.exceptionOrNull() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            Thread.sleep(4_500)
            val peer = pool.submit<Throwable?> {
                runCatching {
                    Socket("127.0.0.1", listener.localPort).use { raw ->
                        (ssl.socketFactory.createSocket(raw, "127.0.0.1", listener.localPort, true) as SSLSocket).use { tls ->
                            tls.useClientMode = false; tls.soTimeout = 3_000; tls.startHandshake()
                            assertEquals(40, tls.inputStream.readNBytes(40).size)
                            Thread.sleep(850)
                            tls.outputStream.write(1); tls.outputStream.flush()
                        }
                    }
                }.exceptionOrNull()
            }
            val peerFailure = peer.get(5, TimeUnit.SECONDS)
            Thread.sleep(100)
            assertEquals("A trusted peer arriving after 4.5s idle must have its own 5s authentication budget", 1, cards)
            assertNull(peerFailure)
            assertNull(worker.get(3, TimeUnit.SECONDS))
        } finally {
            client.close(); listener.close(); pair.close(); pool.shutdownNow()
        }
    }

    @Test fun slowPeerArrivingAfterIdleCannotEscapeAttemptDeadline() {
        val listener = ServerSocket(0).apply { soTimeout = 20_000 }
        val entered = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        val pair = BridgePairing("old-pc.invalid", 39443, ByteArray(32), ByteArray(32), Long.MAX_VALUE, "tls_reverse", true)
        val client = PinnedBridgeClient(pair, transportSocket = { entered.countDown(); listener.accept() })
        val card = object : BridgeCard {
            override val atr = byteArrayOf(0x3b, 0)
            override fun transmit(command: ByteArray) = byteArrayOf(0x90.toByte(), 0)
            override fun close() {}
        }
        pool.submit<Throwable?> { runCatching { client.run { card } }.exceptionOrNull() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            Thread.sleep(5_500)
            Socket("127.0.0.1", listener.localPort).use { peer ->
                val incompleteRecord = byteArrayOf(22, 3, 3, 0, 64) + ByteArray(40)
                var disconnected = false
                for (value in incompleteRecord) {
                    if (runCatching { peer.outputStream.write(value.toInt()); peer.outputStream.flush() }.isFailure) {
                        disconnected = true; break
                    }
                    Thread.sleep(130)
                }
                assertTrue("Per-attempt deadline must close a slowly fragmented TLS peer even after the initial idle wait", disconnected)
            }
        } finally {
            client.close(); listener.close(); pair.close(); pool.shutdownNow()
        }
    }
}
