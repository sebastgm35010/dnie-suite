package pe.gob.reniec.dnie.desktop

import java.net.NetworkInterface
import java.net.ServerSocket
import java.nio.file.Files
import java.util.UUID
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
import pe.gob.reniec.dnie.desktop.continuous.SuitePcIdentity
import pe.gob.reniec.dnie.desktop.continuous.WindowsPrivateStore
import pe.gob.reniec.dnie.desktop.demo.SimulatedSigningCard
import pe.gob.reniec.dnie.desktop.model.*
import pe.gob.reniec.dnie.desktop.remote.*
import pe.gob.reniec.dnie.signing.link.*

class ContinuousSuiteTest {
    private fun address(): String {
        val ip = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.mapNotNull { it.hostAddress }.firstOrNull(SigningProvision::privateAddress)
        assumeTrue(ip != null); return ip!!
    }
    @Test fun twoJobsSharePinnedTlsAndPaceButNotPinAuthorization() = runBlocking<Unit> {
        val ip = address(); val port = ServerSocket(0).use { it.localPort }
        val folder = Files.createTempDirectory("continuous-suite-")
        val executor = Executors.newSingleThreadExecutor()
        val identity = JobTlsIdentity()
        val stale = if (ip != "192.168.255.254") "192.168.255.254" else "10.254.254.254"
        val pair = SuitePairing(UUID.randomUUID(), stale, identity.fingerprint, ByteArray(32) { 3 })
        val provision = pair.provision()
        val phone = PhoneSigningTransport(provision, port, continuous = true)
        val currentRoute = SigningProvision(provision.sessionId, ip, provision.expiresAt, provision.count, provision.fingerprint, provision.token)
        val pc = ReverseTlsExchange(ip, currentRoute, identity, port, continuous = true)
        val card = SimulatedSigningCard()
        val slot = SuiteCardSlot()
        val authenticatedPeer = java.util.concurrent.atomic.AtomicReference<String>()
        val peerKnown = java.util.concurrent.CountDownLatch(1)
        assertTrue(slot.offer { card to { true } })
        val worker = executor.submit { runBlocking {
            ContinuousSigningConnection(phone.accept(onPeerAddress = { authenticatedPeer.set(it); peerKnown.countDown() }), slot::acquire, slot::invalidate, finishJob = slot::finishJob).run()
        } }
        try {
            val client = ContinuousSigningClient(pc.openChannel())
            assertTrue(peerKnown.await(5, TimeUnit.SECONDS))
            assertEquals(ip, authenticatedPeer.get())
            client.use {
                repeat(2) { round ->
                    val input = folder.resolve("document-$round.txt"); Files.writeString(input, "Synthetic continuous job $round")
                    val id = UUID.randomUUID(); val pin = SimulatedSigningCard.DEMO_PIN.toCharArray()
                    val job = client.begin(id, 1, pin); pin.fill('\u0000')
                    val result = DesktopSigningService().execute(DesktopSigningRequest(listOf(input), folder.resolve("jobs")),
                        PrivateSigningCard(id, job), "", SigningBackend.SIMULATED)
                    assertTrue(result.complete)
                    val doc = result.documents.single()
                    val cms = CMSSignedData(CMSProcessableByteArray(Files.readAllBytes(doc.original)), Files.readAllBytes(doc.signature!!))
                    assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().build(card.certificate)))
                    assertEquals(0, card.closes)
                }
            }
            worker.get(10, TimeUnit.SECONDS)
            assertEquals(1, card.selections); assertEquals(2, card.verifications); assertEquals(2, card.signatures)
            assertEquals(1, card.closes)
        } finally {
            pc.close(); phone.close(); slot.close(); provision.close(); currentRoute.close(); pair.close(); executor.shutdownNow()
            Files.walk(folder).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
    @Test fun rejectedPinDoesNotRetryAndNextJobRequiresFreshPhysicalCard() = runBlocking<Unit> {
        val ip = address(); val port = ServerSocket(0).use { it.localPort }
        val identity = JobTlsIdentity(); val pair = SuitePairing(UUID.randomUUID(), ip, identity.fingerprint, ByteArray(32))
        val provision = pair.provision(); val phone = PhoneSigningTransport(provision, port, continuous = true)
        val pc = ReverseTlsExchange(ip, provision, identity, port, continuous = true)
        val slot = SuiteCardSlot(); val first = SimulatedSigningCard(); val second = SimulatedSigningCard()
        slot.offer { first to { true } }
        val executor = Executors.newSingleThreadExecutor()
        val worker = executor.submit { runBlocking {
            ContinuousSigningConnection(phone.accept(), slot::acquire, slot::invalidate, finishJob = slot::finishJob).run()
        } }
        try {
            ContinuousSigningClient(pc.openChannel()).use { client ->
                val id = UUID.randomUUID()
                val card = PrivateSigningCard(id, client.begin(id, 1, "9999".toCharArray()))
                card.selectPkiApplet().getOrThrow()
                assertTrue(card.verifyPin("").getOrThrow() is pe.gob.reniec.dnie.domain.model.PinVerificationResult.InvalidPin)
                assertEquals(1, first.verifications); assertEquals(0, first.signatures); assertEquals(1, first.closes)
                assertTrue(slot.offer { second to { true } })
                val next = UUID.randomUUID()
                val nextCard = PrivateSigningCard(next, client.begin(next, 1, "0000".toCharArray()))
                nextCard.selectPkiApplet().getOrThrow(); nextCard.verifyPin("").getOrThrow()
                nextCard.readCertificate(pe.gob.reniec.dnie.domain.model.CertificateType.SIGNATURE).getOrThrow()
                nextCard.signSha256Hash(ByteArray(32)).getOrThrow(); nextCard.close()
                assertEquals(1, second.signatures)
            }
            worker.get(10, TimeUnit.SECONDS)
        } finally { pc.close(); phone.close(); slot.close(); provision.close(); pair.close(); executor.shutdownNow() }
    }
    @Test fun persistentWindowsIdentitySurvivesRestartAndAddressChangeButRejectsTampering() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val root = Files.createTempDirectory("suite-dpapi-"); val path = root.resolve("identity.bin")
        try {
            val fingerprint: String
            val encoded: String
            SuitePcIdentity.open(path, "192.168.1.5").use { fingerprint = it.pairing.fingerprintText(); encoded = it.pairing.encode() }
            assertFalse(String(Files.readAllBytes(path), Charsets.ISO_8859_1).contains("DNISUITE2"))
            SuitePcIdentity.open(path, "192.168.1.5").use {
                assertEquals(fingerprint, it.pairing.fingerprintText()); assertEquals(encoded, it.pairing.encode())
            }
            val originalBytes = Files.readAllBytes(path)
            val originalPair = SuitePairing.parse(encoded)
            originalPair.use { original ->
                SuitePcIdentity.open(path, "192.168.1.6").use { moved ->
                    assertEquals("192.168.1.6", moved.pairing.pc)
                    assertEquals(original.id, moved.pairing.id)
                    assertEquals(fingerprint, moved.pairing.fingerprintText())
                    assertArrayEquals(original.token, moved.pairing.token)
                    assertArrayEquals(original.fingerprint, moved.tls.fingerprint)
                }
            }
            assertArrayEquals(originalBytes, Files.readAllBytes(path))
            val damaged = Files.readAllBytes(path); damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
            assertTrue(runCatching { WindowsPrivateStore().unprotect(damaged) }.isFailure)
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(root) }
    }
    @Test fun boundedDpapiHelperTerminatesSeparatelyWhenStdinBlocksOrWhenStdoutBlocks() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        // 1. Stdin saturation block: child never reads stdin while parent writes 512 KB (> OS pipe buffer).
        val startStdin = System.nanoTime()
        val largeStdin = ByteArray(524_288) { 0x41 }
        val stdinFailure = runCatching {
            WindowsPrivateStore.runBoundedProcess(
                listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "Start-Sleep -Seconds 30"),
                largeStdin,
                timeoutMillis = 400
            )
        }
        val elapsedStdinMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startStdin)
        assertTrue(stdinFailure.isFailure)
        assertTrue("Expected bounded stdin timeout, took ${elapsedStdinMs}ms", elapsedStdinMs < 8_000)
        assertTrue(largeStdin.all { it == 0.toByte() })

        // 2. Stdout hang after draining stdin: child consumes stdin, emits partial stdout without EOF, and hangs.
        val startStdout = System.nanoTime()
        val smallStdin = "ok\r\n".toByteArray(Charsets.US_ASCII)
        val stdoutFailure = runCatching {
            WindowsPrivateStore.runBoundedProcess(
                listOf(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "[Console]::In.ReadLine() | Out-Null; [Console]::Out.Write('partial'); [Console]::Out.Flush(); Start-Sleep -Seconds 30"
                ),
                smallStdin,
                timeoutMillis = 400
            )
        }
        val elapsedStdoutMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startStdout)
        assertTrue(stdoutFailure.isFailure)
        assertTrue("Expected bounded stdout timeout, took ${elapsedStdoutMs}ms", elapsedStdoutMs < 8_000)
        assertTrue(smallStdin.all { it == 0.toByte() })
    }
    @Test fun absentCardTimeoutKeepsSameTlsForNextExplicitJob() = runBlocking<Unit> {
        val ip = address(); val port = ServerSocket(0).use { it.localPort }
        val identity = JobTlsIdentity(); val pair = SuitePairing(UUID.randomUUID(), ip, identity.fingerprint, ByteArray(32))
        val provision = pair.provision(); val phone = PhoneSigningTransport(provision, port, continuous = true)
        val pc = ReverseTlsExchange(ip, provision, identity, port, continuous = true)
        val slot = SuiteCardSlot(); val card = SimulatedSigningCard()
        val executor = Executors.newSingleThreadExecutor()
        val worker = executor.submit { runBlocking {
            ContinuousSigningConnection(phone.accept(), slot::acquire, slot::invalidate,
                finishJob = slot::finishJob, cardWaitMillis = 75).run()
        } }
        try {
            ContinuousSigningClient(pc.openChannel()).use { client ->
                val absent = UUID.randomUUID()
                val missing = runCatching { client.begin(absent, 1, "0000".toCharArray()) }.exceptionOrNull()
                assertTrue(missing is SuiteCardUnavailableException)
                assertEquals(0, card.verifications); assertEquals(0, card.signatures)
                assertTrue(slot.offer { card to { true } })
                val next = UUID.randomUUID(); val signer = PrivateSigningCard(next, client.begin(next, 1, "0000".toCharArray()))
                signer.selectPkiApplet().getOrThrow(); signer.verifyPin("").getOrThrow()
                signer.readCertificate(pe.gob.reniec.dnie.domain.model.CertificateType.SIGNATURE).getOrThrow()
                signer.signSha256Hash(ByteArray(32)).getOrThrow(); signer.close()
                assertEquals(1, card.signatures)
            }
            worker.get(10, TimeUnit.SECONDS)
        } finally { pc.close(); phone.close(); slot.close(); provision.close(); pair.close(); executor.shutdownNow() }
    }
}
