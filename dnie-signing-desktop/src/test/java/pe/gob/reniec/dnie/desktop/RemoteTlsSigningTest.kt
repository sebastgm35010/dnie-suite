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
        val serverPair = SigningProvision(UUID.randomUUID(), ip, System.currentTimeMillis() + 60_000, 1, identity.fingerprint, ByteArray(32))
        val phonePair = SigningProvision(serverPair.sessionId, "192.168.255.254", serverPair.expiresAt, 1,
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
    @Test fun remoteOptionsRejectSecretsPublicPeersAndDuplicateParameters() {
        val valid = arrayOf("remote", "--pc", "192.168.1.5", "--phone", "192.168.1.11", "--workspace", "jobs", "--input", "doc.txt")
        assertEquals("192.168.1.11", RemoteOptions.parse(valid).phone)
        assertTrue(runCatching { RemoteOptions.parse(valid + arrayOf("--pin", "1234")) }.isFailure)
        assertTrue(runCatching { RemoteOptions.parse(valid + arrayOf("--pc", "192.168.1.6")) }.isFailure)
        assertTrue(runCatching { RemoteOptions.parse(valid.map { if (it == "192.168.1.11") "8.8.8.8" else it }.toTypedArray()) }.isFailure)
    }
}
