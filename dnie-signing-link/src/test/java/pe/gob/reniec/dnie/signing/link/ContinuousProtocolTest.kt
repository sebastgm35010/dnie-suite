package pe.gob.reniec.dnie.signing.link

import java.io.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.domain.model.*

class ContinuousProtocolTest {
    @Test fun pairingRoundTripsButNeverPrintsCredential() {
        val pair = SuitePairing(UUID.randomUUID(), "192.168.1.5", ByteArray(32) { 7 }, ByteArray(32) { 9 })
        SuitePairing.parse(pair.encode()).use { assertEquals(pair.encode(), it.encode()) }
        assertFalse(pair.toString().contains("0909"))
        assertTrue(runCatching { SuitePairing.parse(pair.encode().replace("192.168.1.5", "8.8.8.8")) }.isFailure)
        pair.close(); assertTrue(pair.token.all { it == 0.toByte() })
    }
    @Test fun beginIsBoundedAndWipesOwnedPin() {
        val id = UUID.randomUUID(); val packet = SuitePackets.begin(id, 2, "1234".toCharArray())
        val wire = ByteArrayOutputStream(); SuitePackets.write(wire, packet)
        SuitePackets.job(SuitePackets.read(ByteArrayInputStream(wire.toByteArray()))!!).use {
            assertEquals(id, it.id); assertEquals(2, it.count); assertArrayEquals("1234".toCharArray(), it.pin)
            it.close(); assertTrue(it.pin.all { c -> c == '\u0000' })
        }
        assertFalse(packet.toString().contains("1234"))
        assertTrue(runCatching { SuitePackets.begin(id, 51, "1234".toCharArray()) }.isFailure)
        assertTrue(runCatching { SuitePackets.job(SuitePacket(SuitePacketKind.BEGIN, packet.bytes + byteArrayOf(0))) }.isFailure)
    }
    @Test fun replayJobIdClosesConnectionBeforeSecondCardAcquisition() = runBlocking {
        val id = UUID.randomUUID(); val input = ByteArrayOutputStream()
        SuitePackets.write(input, SuitePackets.begin(id, 1, "1234".toCharArray()))
        SuitePackets.write(input, SuitePacket(SuitePacketKind.END))
        SuitePackets.write(input, SuitePackets.begin(id, 1, "1234".toCharArray()))
        var acquisitions = 0; var closed = false
        val channel = object : SigningDuplex {
            override val input = ByteArrayInputStream(input.toByteArray())
            override val output = ByteArrayOutputStream()
            override fun close() { closed = true }
        }
        val card = object : DnieTransceiverInterface {
            override suspend fun selectPkiApplet() = Result.success(Unit)
            override suspend fun verifyPin(pin: String) = Result.success<PinVerificationResult>(PinVerificationResult.Success)
            override suspend fun readCertificate(type: CertificateType) = Result.success(byteArrayOf(1))
            override suspend fun signSha256Hash(hash32Bytes: ByteArray) = Result.success(ByteArray(256))
            override fun close() {}
        }
        val session = ContinuousSigningConnection(channel, { acquisitions++; card }, {})
        assertTrue(runCatching { session.run() }.isFailure)
        assertEquals(1, acquisitions); assertTrue(closed)
    }
    @Test fun repeatedTagDoesNotReplacePaceAndRemovedTagCanBeReplacedBetweenJobs() = runBlocking {
        val slot = SuiteCardSlot(); var present = true; var closes = 0; var selects = 0
        fun card() = object : DnieTransceiverInterface {
            override suspend fun selectPkiApplet() = Result.success(Unit).also { selects++ }
            override suspend fun verifyPin(pin: String) = Result.success<PinVerificationResult>(PinVerificationResult.Success)
            override suspend fun readCertificate(type: CertificateType) = Result.success(byteArrayOf(1))
            override suspend fun signSha256Hash(hash32Bytes: ByteArray) = Result.success(ByteArray(256))
            override fun close() { closes++ }
        }
        assertTrue(slot.offer { card() to { present } })
        slot.acquire().selectPkiApplet().getOrThrow(); slot.finishJob()
        assertFalse(slot.offer { error("Must not create another IsoDep wrapper") })
        slot.acquire().selectPkiApplet().getOrThrow(); slot.finishJob(); assertEquals(1, selects)
        present = false
        assertTrue(slot.offer { card() to { true } }); assertEquals(1, closes)
        slot.acquire().selectPkiApplet().getOrThrow(); assertEquals(2, selects)
        slot.close(); assertEquals(2, closes)
    }
    @Test fun idleRemovalIsVisibleAndPendingJobWaitsForFreshTag() = runBlocking<Unit> {
        val slot = SuiteCardSlot(); var present = true; var closes = 0
        fun card() = object : DnieTransceiverInterface {
            override suspend fun selectPkiApplet() = Result.success(Unit)
            override suspend fun verifyPin(pin: String) = Result.success<PinVerificationResult>(PinVerificationResult.Success)
            override suspend fun readCertificate(type: CertificateType) = Result.success(byteArrayOf(1))
            override suspend fun signSha256Hash(hash32Bytes: ByteArray) = Result.success(ByteArray(256))
            override fun close() { closes++ }
        }
        slot.offer { card() to { present } }
        assertEquals(SuiteCardState.DETECTED, slot.state.value)
        slot.acquire().selectPkiApplet().getOrThrow(); slot.finishJob()
        assertEquals(SuiteCardState.READY, slot.state.value)
        present = false; slot.pollPresence()
        assertEquals(SuiteCardState.REMOVED, slot.state.value); assertEquals(1, closes)
        val pending = async { slot.acquire() }
        delay(25); assertFalse(pending.isCompleted)
        slot.offer { card() to { true } }
        pending.await().selectPkiApplet().getOrThrow(); slot.finishJob()
        assertEquals(SuiteCardState.READY, slot.state.value)
        slot.close(); assertEquals(2, closes)
    }
    @Test fun lostSignatureReplyClosesCardWithoutReplayingOperation() = runBlocking<Unit> {
        var signatures = 0; var closes = 0
        val card = object : DnieTransceiverInterface {
            override suspend fun selectPkiApplet() = Result.success(Unit)
            override suspend fun verifyPin(pin: String) = Result.success<PinVerificationResult>(PinVerificationResult.Success)
            override suspend fun readCertificate(type: CertificateType) = Result.success(byteArrayOf(1))
            override suspend fun signSha256Hash(hash32Bytes: ByteArray) = Result.success(ByteArray(256)).also { signatures++ }
            override fun close() { closes++ }
        }
        val id = UUID.randomUUID(); val requests = ByteArrayOutputStream()
        SuitePackets.write(requests, SuitePackets.begin(id, 1, "1234".toCharArray()))
        listOf(SigningOperation.OPEN, SigningOperation.AUTHORIZE, SigningOperation.CERTIFICATE, SigningOperation.SIGN_SHA256).forEachIndexed { i, op ->
            SuitePackets.write(requests, SuitePackets.frame(SuitePacketKind.FRAME,
                SigningFrame(id, (i+1).toLong(), op, SigningStatus.REQUEST, if (op == SigningOperation.SIGN_SHA256) ByteArray(32) else byteArrayOf())))
        }
        SuitePackets.write(requests, SuitePacket(SuitePacketKind.END))
        val channel = object : SigningDuplex {
            override val input = ByteArrayInputStream(requests.toByteArray())
            override val output = object : OutputStream() {
                override fun write(b: Int) { if (signatures > 0) throw IOException("Synthetic lost response") }
            }
            override fun close() {}
        }
        val session = ContinuousSigningConnection(channel, { card }, {})
        assertTrue(runCatching { session.run() }.isFailure)
        assertEquals(1, signatures); assertEquals(1, closes)
    }

}
