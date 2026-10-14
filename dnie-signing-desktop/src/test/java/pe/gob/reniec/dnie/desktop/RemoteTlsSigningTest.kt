package pe.gob.reniec.dnie.desktop

import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import pe.gob.reniec.dnie.desktop.application.DesktopSigningService
import pe.gob.reniec.dnie.desktop.demo.SimulatedSigningCard
import pe.gob.reniec.dnie.desktop.model.*
import pe.gob.reniec.dnie.desktop.remote.*
import pe.gob.reniec.dnie.signing.link.*

class RemoteTlsSigningTest {
    private fun address(): String {
        val found = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.mapNotNull { it.hostAddress }
            .firstOrNull(SigningProvision::privateAddress)
        assumeTrue("TLS integration requires a private IPv4 interface", found != null)
        return found!!
    }
    @Test fun cmsBatchCrossesActualPinnedTlsWithPhonePin() = runBlocking {
        val ip = address()
        val port = ServerSocket(0).use { it.localPort }
        val folder = Files.createTempDirectory("remote-tls-")
        val executor = Executors.newSingleThreadExecutor()
        val identity = JobTlsIdentity()
        val pair = SigningProvision(UUID.randomUUID(), "192.168.255.254", System.currentTimeMillis() + 60_000, 2,
            identity.fingerprint, ByteArray(32) { (it + 1).toByte() })
        val phone = PhoneSigningTransport(pair, port)
        val currentRoute = SigningProvision(pair.sessionId, ip, pair.expiresAt, pair.count, pair.fingerprint, pair.token)
        val exchange = ReverseTlsExchange(ip, currentRoute, identity, port)
        val card = SimulatedSigningCard()
        val worker = executor.submit {
            runBlocking {
                val stream = phone.accept()
                AuthorizedSigningConnection(stream, AuthorizedSigningEndpoint(pair.sessionId, card,
                    SimulatedSigningCard.DEMO_PIN.toCharArray(), 2)).run()
            }
        }
        try {
            val inputs = listOf(folder.resolve("uno.txt"), folder.resolve("dos.bin"))
            inputs.forEach { Files.writeString(it, "Synthetic private TLS document") }
            val result = DesktopSigningService().execute(DesktopSigningRequest(inputs, folder.resolve("jobs")),
                PrivateSigningCard(pair.sessionId, exchange), "", SigningBackend.SIMULATED)
            assertTrue(result.complete)
            worker.get(10, TimeUnit.SECONDS)
            assertEquals(2, card.signatures); assertEquals(2, card.verifications); assertEquals(1, card.closes)
            result.documents.forEach { doc ->
                val cms = CMSSignedData(CMSProcessableByteArray(Files.readAllBytes(doc.original)), Files.readAllBytes(doc.signature!!))
                assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().build(card.certificate)))
            }
        } finally {
            exchange.close(); phone.close(); pair.close(); currentRoute.close(); executor.shutdownNow()
            Files.walk(folder).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
    @Test fun wrongFingerprintOrTokenCannotReachCard() = runBlocking {
        repeat(2) { variant ->
        val ip = address(); val port = ServerSocket(0).use { it.localPort }
        val identity = JobTlsIdentity()
        val deadline = System.currentTimeMillis() + 800
        val serverPair = SigningProvision(UUID.randomUUID(), ip, deadline, 1, identity.fingerprint, ByteArray(32))
        val phonePair = SigningProvision(serverPair.sessionId, "192.168.255.254", deadline, 1,
            if (variant == 0) ByteArray(32) else identity.fingerprint,
            if (variant == 0) serverPair.token else ByteArray(32) { 1 })
        val phone = PhoneSigningTransport(phonePair, port)
        val exchange = ReverseTlsExchange(ip, serverPair, identity, port)
        val executor = Executors.newSingleThreadExecutor()
        val worker = executor.submit<Boolean> { runCatching { phone.accept() }.isFailure }
        try {
            assertTrue(PrivateSigningCard(serverPair.sessionId, exchange).selectPkiApplet().isFailure)
            assertTrue(worker.get(10, TimeUnit.SECONDS))
        } finally { exchange.close(); phone.close(); executor.shutdownNow(); serverPair.close(); phonePair.close() }
        }
    }
    @Test fun preAuthDisconnectAndWrongTokenAllowSubsequentValidPeerAndHonorCancellation() = runBlocking {
        val ip = address()
        val port = ServerSocket(0).use { it.localPort }
        val folder = Files.createTempDirectory("remote-tls-retry-")
        val executor = Executors.newCachedThreadPool()
        val identity = JobTlsIdentity()
        val pair = SigningProvision(UUID.randomUUID(), ip, System.currentTimeMillis() + 30_000, 1,
            identity.fingerprint, ByteArray(32) { (it + 5).toByte() })
        val phone = PhoneSigningTransport(pair, port)
        val ready = CountDownLatch(1)
        val card = SimulatedSigningCard()
        val worker = executor.submit {
            runBlocking {
                val stream = phone.accept(onEvent = { if (it == "listener_ready") ready.countDown() })
                AuthorizedSigningConnection(stream, AuthorizedSigningEndpoint(pair.sessionId, card,
                    SimulatedSigningCard.DEMO_PIN.toCharArray(), 1)).run()
            }
        }
        val wrongPair = SigningProvision(pair.sessionId, ip, pair.expiresAt, 1, identity.fingerprint, ByteArray(32) { 99 })
        val wrongExchange = ReverseTlsExchange(ip, wrongPair, identity, port)
        val validExchange = ReverseTlsExchange(ip, pair, identity, port)
        try {
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            // 1. Unauthenticated TCP probe + immediate disconnect must not terminate listener.
            Socket(ip, port).use { }
            // 2. Wrong token over TLS must be rejected while keeping listener alive for the valid peer.
            assertTrue(PrivateSigningCard(pair.sessionId, wrongExchange).selectPkiApplet().isFailure)
            // 3. Valid peer connects and completes signing within the same provision window.
            val input = folder.resolve("valido.txt"); Files.writeString(input, "Documento tras reintento")
            val result = DesktopSigningService().execute(DesktopSigningRequest(listOf(input), folder.resolve("jobs")),
                PrivateSigningCard(pair.sessionId, validExchange), "", SigningBackend.SIMULATED)
            assertTrue(result.complete)
            worker.get(10, TimeUnit.SECONDS)
            assertEquals(1, card.signatures)
            // 4. Explicit close() while waiting in accept() must cancel immediately.
            val cancelPort = ServerSocket(0).use { it.localPort }
            val cancelPair = SigningProvision(UUID.randomUUID(), ip, System.currentTimeMillis() + 60_000, 1,
                identity.fingerprint, ByteArray(32) { 7 })
            val cancelPhone = PhoneSigningTransport(cancelPair, cancelPort)
            val cancelReady = CountDownLatch(1)
            val cancelFuture = executor.submit<Boolean> {
                runCatching { cancelPhone.accept(onEvent = { if (it == "listener_ready") cancelReady.countDown() }) }.isFailure
            }
            try {
                assertTrue(cancelReady.await(5, TimeUnit.SECONDS))
                cancelPhone.close()
                assertTrue(cancelFuture.get(3, TimeUnit.SECONDS))
            } finally {
                cancelPhone.close(); cancelPair.close()
            }
        } finally {
            wrongExchange.close(); validExchange.close(); phone.close(); pair.close(); wrongPair.close(); executor.shutdownNow()
            Files.walk(folder).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
    @Test fun fragmentedTlsHandshakeCannotOutliveProvisionDeadlineOrBlockSubsequentValidPeer() = runBlocking {
        val ip = address()
        val executor = Executors.newCachedThreadPool()
        try {
            // 1. Fragmented TLS handshake drip-fed across provision expiry must be terminated at expiresAt.
            val port1 = ServerSocket(0).use { it.localPort }
            val pair1 = SigningProvision(UUID.randomUUID(), ip, System.currentTimeMillis() + 1_200,
                1, ByteArray(32), ByteArray(32))
            val phone1 = PhoneSigningTransport(pair1, port1)
            val ready1 = CountDownLatch(1)
            val worker1 = executor.submit<Boolean> {
                runCatching { phone1.accept(onEvent = { if (it == "listener_ready") ready1.countDown() }) }.isFailure
            }
            try {
                assertTrue(ready1.await(5, TimeUnit.SECONDS))
                Socket(ip, port1).use { peer ->
                    val partialRecord = byteArrayOf(22, 3, 3, 0, 64) + ByteArray(27)
                    for (byte in partialRecord) {
                        if (worker1.isDone) break
                        runCatching { peer.outputStream.write(byte.toInt()); peer.outputStream.flush() }
                        Thread.sleep(100)
                    }
                }
                assertTrue(worker1.get(1, TimeUnit.SECONDS))
                val overdue = System.currentTimeMillis() - pair1.expiresAt
                assertTrue("Expected prompt termination at provision deadline, overdue=${overdue}ms", overdue < 600)
            } finally {
                phone1.close(); pair1.close()
            }

            // 2. Slow fragmented peer during a longer provision window is cut off by per-attempt deadline so valid peer succeeds.
            val port2 = ServerSocket(0).use { it.localPort }
            val folder = Files.createTempDirectory("remote-tls-slowloris-")
            val identity = JobTlsIdentity()
            val pair2 = SigningProvision(UUID.randomUUID(), ip, System.currentTimeMillis() + 20_000, 1,
                identity.fingerprint, ByteArray(32) { (it + 9).toByte() })
            val phone2 = PhoneSigningTransport(pair2, port2, preAuthAttemptTimeoutMs = 450L)
            val ready2 = CountDownLatch(1)
            val card = SimulatedSigningCard()
            val worker2 = executor.submit {
                runBlocking {
                    val stream = phone2.accept(onEvent = { if (it == "listener_ready") ready2.countDown() })
                    AuthorizedSigningConnection(stream, AuthorizedSigningEndpoint(pair2.sessionId, card,
                        SimulatedSigningCard.DEMO_PIN.toCharArray(), 1)).run()
                }
            }
            val validExchange = ReverseTlsExchange(ip, pair2, identity, port2)
            try {
                assertTrue(ready2.await(5, TimeUnit.SECONDS))
                Socket(ip, port2).use { slowPeer ->
                    val partialRecord = byteArrayOf(22, 3, 3, 0, 64) + ByteArray(10)
                    for (byte in partialRecord) {
                        if (runCatching { slowPeer.outputStream.write(byte.toInt()); slowPeer.outputStream.flush() }.isFailure) break
                        Thread.sleep(80)
                    }
                }
                val input = folder.resolve("tras-slowloris.txt"); Files.writeString(input, "Firmado tras cortar slowloris")
                val result = DesktopSigningService().execute(DesktopSigningRequest(listOf(input), folder.resolve("jobs")),
                    PrivateSigningCard(pair2.sessionId, validExchange), "", SigningBackend.SIMULATED)
                assertTrue(result.complete)
                worker2.get(10, TimeUnit.SECONDS)
                assertEquals(1, card.signatures)
            } finally {
                validExchange.close(); phone2.close(); pair2.close()
                Files.walk(folder).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
            }
        } finally {
            executor.shutdownNow()
        }
    }
    @Test fun remoteOptionsRejectSecretsPublicPeersAndDuplicateParameters() {
        val valid = arrayOf("remote", "--pc", "192.168.1.5", "--phone", "192.168.1.11", "--workspace", "jobs", "--input", "doc.txt")
        assertEquals("192.168.1.11", RemoteOptions.parse(valid).phone)
        assertTrue(runCatching { RemoteOptions.parse(valid + arrayOf("--pin", "1234")) }.isFailure)
        assertTrue(runCatching { RemoteOptions.parse(valid + arrayOf("--pc", "192.168.1.6")) }.isFailure)
        assertTrue(runCatching { RemoteOptions.parse(valid.map { if (it == "192.168.1.11") "8.8.8.8" else it }.toTypedArray()) }.isFailure)
    }
}
