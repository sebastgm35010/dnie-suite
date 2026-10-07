package pe.gob.reniec.dnie.signing.link

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult

class SigningLinkTest {
    private val id = UUID.randomUUID()
    private val pin = charArrayOf('1', '2', '3', '4')
    private class Card : DnieTransceiverInterface {
        var verifies = 0; var signs = 0; var closes = 0; var opens = 0
        var failure = false
        var cancelled = false
        var pinResult: PinVerificationResult = PinVerificationResult.Success
        override suspend fun selectPkiApplet() = Result.success(Unit).also { opens++ }
        override suspend fun readCertificate(type: CertificateType) = Result.success(byteArrayOf(1, 2, 3))
        override suspend fun verifyPin(pin: String): Result<PinVerificationResult> {
            assertEquals("1234", pin)
            verifies++
            return Result.success(pinResult)
        }
        override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> {
            signs++
            if (cancelled) throw CancellationException("cancelled")
            return if (failure) Result.failure(Exception("PRIVATE physical detail")) else Result.success(ByteArray(256))
        }
        override fun close() { closes++ }
    }
    private class LocalExchange(private val endpoint: AuthorizedSigningEndpoint) : SigningExchange {
        val frames = mutableListOf<ByteArray>()
        private fun wire(frame: SigningFrame): SigningFrame {
            val bytes = ByteArrayOutputStream().also { SigningFrames.write(it, frame) }.toByteArray()
            frames.add(bytes)
            return SigningFrames.read(ByteArrayInputStream(bytes))!!
        }
        override suspend fun exchange(request: SigningFrame) = wire(endpoint.process(wire(request)))
        override fun close() { endpoint.close() }
    }
    private fun request(sequence: Long, operation: SigningOperation, payload: ByteArray = byteArrayOf(),
                        session: UUID = id) = SigningFrame(session, sequence, operation, SigningStatus.REQUEST, payload)
    private suspend fun ready(endpoint: AuthorizedSigningEndpoint) {
        assertEquals(SigningStatus.OK, endpoint.process(request(1, SigningOperation.OPEN)).status)
        assertEquals(SigningStatus.OK, endpoint.process(request(2, SigningOperation.AUTHORIZE)).status)
        assertEquals(SigningStatus.OK, endpoint.process(request(3, SigningOperation.CERTIFICATE)).status)
    }

    @Test fun batchUsesPhonePinAndConsumesAuthorizationForEachSignature() = runBlocking {
        val physical = Card()
        val endpoint = AuthorizedSigningEndpoint(id, physical, pin, 2)
        pin.fill('9') // Endpoint owns a copy, not the UI's buffer.
        val exchange = LocalExchange(endpoint)
        val remote = PrivateSigningCard(id, exchange)
        remote.selectPkiApplet().getOrThrow()
        repeat(2) { index ->
            assertSame(PinVerificationResult.Success, remote.verifyPin("").getOrThrow())
            if (index == 0) assertArrayEquals(byteArrayOf(1, 2, 3), remote.readCertificate(CertificateType.SIGNATURE).getOrThrow())
            assertEquals(256, remote.signSha256Hash(ByteArray(32)).getOrThrow().size)
        }
        assertTrue(remote.verifyPin("").isFailure) // No extension of the approved batch.
        assertEquals(1, physical.opens); assertEquals(2, physical.verifies); assertEquals(2, physical.signs)
        remote.close(); endpoint.close()
        assertEquals(1, physical.closes)
        assertFalse(exchange.frames.any { String(it, Charsets.ISO_8859_1).contains("1234") })
    }

    @Test fun replayClosesWithoutExecutingAnotherPrivateOperation() = runBlocking {
        val card = Card(); val endpoint = AuthorizedSigningEndpoint(id, card, pin, 2)
        ready(endpoint)
        val sign = request(4, SigningOperation.SIGN_SHA256, ByteArray(32))
        assertEquals(SigningStatus.OK, endpoint.process(sign).status)
        assertEquals(SigningStatus.FAILED, endpoint.process(sign).status)
        assertEquals(SigningStatus.FAILED, endpoint.process(request(5, SigningOperation.AUTHORIZE)).status)
        assertEquals(1, card.signs); assertEquals(1, card.closes)
    }

    @Test fun wrongSessionCannotEvenSelectTheApplet() = runBlocking {
        val card = Card(); val endpoint = AuthorizedSigningEndpoint(id, card, pin, 1)
        assertEquals(SigningStatus.FAILED, endpoint.process(request(1, SigningOperation.OPEN, session = UUID.randomUUID())).status)
        assertEquals(0, card.opens); assertEquals(1, card.closes)
    }

    @Test fun invalidPayloadAndOutOfOrderSignatureDoNotReachCard() = runBlocking {
        for (payload in listOf(ByteArray(31), ByteArray(32))) {
            val card = Card(); val endpoint = AuthorizedSigningEndpoint(id, card, pin, 1)
            assertEquals(SigningStatus.FAILED, endpoint.process(request(1, SigningOperation.SIGN_SHA256, payload)).status)
            assertEquals(0, card.signs); assertEquals(1, card.closes)
        }
        val card = Card(); val endpoint = AuthorizedSigningEndpoint(id, card, pin, 1)
        assertEquals(SigningStatus.FAILED, endpoint.process(request(1, SigningOperation.OPEN, byteArrayOf(0))).status)
        assertEquals(0, card.opens)
    }

    @Test fun failedPinIsReportedOnceAndNotRetried() = runBlocking {
        for (result in listOf(PinVerificationResult.InvalidPin(2), PinVerificationResult.CardBlocked)) {
            val card = Card().apply { pinResult = result }
            val endpoint = AuthorizedSigningEndpoint(id, card, pin, 1)
            val remote = PrivateSigningCard(id, LocalExchange(endpoint))
            remote.selectPkiApplet().getOrThrow()
            assertEquals(result, remote.verifyPin("").getOrThrow())
            assertTrue(remote.verifyPin("").isFailure)
            assertEquals(1, card.verifies); assertEquals(0, card.signs); assertEquals(1, card.closes)
        }
    }

    @Test fun privateFailureHasNoRetryAndNoPhysicalExceptionOnWire() = runBlocking {
        val card = Card().apply { failure = true }
        val endpoint = AuthorizedSigningEndpoint(id, card, pin, 2)
        ready(endpoint)
        val response = endpoint.process(request(4, SigningOperation.SIGN_SHA256, ByteArray(32)))
        assertEquals(SigningStatus.FAILED, response.status); assertEquals(0, response.payload.size)
        endpoint.process(request(5, SigningOperation.SIGN_SHA256, ByteArray(32)))
        assertEquals(1, card.signs); assertEquals(1, card.closes)
    }

    @Test fun monotonicExpiryClosesBeforeAnotherCardOperation() = runBlocking {
        var clock = 10L
        val card = Card(); val endpoint = AuthorizedSigningEndpoint(id, card, pin, 1, 100) { clock }
        ready(endpoint); clock = 110
        assertEquals(SigningStatus.FAILED, endpoint.process(request(4, SigningOperation.SIGN_SHA256, ByteArray(32))).status)
        assertEquals(0, card.signs); assertEquals(1, card.closes)
    }

    @Test fun cancellationPropagatesAndCloses() = runBlocking {
        val card = Card().apply { cancelled = true }
        val endpoint = AuthorizedSigningEndpoint(id, card, pin, 1)
        ready(endpoint)
        try {
            endpoint.process(request(4, SigningOperation.SIGN_SHA256, ByteArray(32)))
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, card.signs); assertEquals(1, card.closes)
    }

    @Test fun pcPinIsRejectedBeforeItCanCrossTheLink() = runBlocking {
        val card = Card(); val endpoint = AuthorizedSigningEndpoint(id, card, pin, 1)
        val exchange = LocalExchange(endpoint); val remote = PrivateSigningCard(id, exchange)
        assertTrue(remote.verifyPin("1234").isFailure)
        assertEquals(0, exchange.frames.size); assertEquals(0, card.verifies); assertEquals(1, card.closes)
    }

    @Test fun wrongResponseIdentityAndLostReplyAreTerminal() = runBlocking {
        repeat(2) { variant ->
            var calls = 0; var closes = 0
            val remote = PrivateSigningCard(id, object : SigningExchange {
                override suspend fun exchange(request: SigningFrame): SigningFrame {
                    calls++
                    if (variant == 0) throw java.io.EOFException("after execution")
                    return SigningFrame(UUID.randomUUID(), request.sequence, request.operation, SigningStatus.OK)
                }
                override fun close() { closes++ }
            })
            assertTrue(remote.selectPkiApplet().isFailure)
            assertTrue(remote.selectPkiApplet().isFailure)
            assertEquals(1, calls); assertEquals(1, closes)
        }
    }

    @Test fun codecRejectsOversizeUnknownVersionAndTruncatedFrames() {
        val bytes = ByteArrayOutputStream().also { SigningFrames.write(it, request(1, SigningOperation.OPEN)) }.toByteArray()
        assertNull(SigningFrames.read(ByteArrayInputStream(byteArrayOf())))
        val malformed = listOf(
            bytes.copyOf().apply { this[4] = 2 },
            bytes.copyOf().apply { this[29] = 99 },
            bytes.copyOf().apply { this[31] = 127 },
            bytes.copyOf().apply { this[31] = -1 },
            bytes.copyOf(bytes.size - 1))
        malformed.forEach { assertTrue(runCatching { SigningFrames.read(ByteArrayInputStream(it)) }.isFailure) }
    }

    @Test fun duplicateAuthorizationDoesNotSpendAnotherPinAttempt() = runBlocking {
        val card = Card(); val endpoint = AuthorizedSigningEndpoint(id, card, pin, 2)
        endpoint.process(request(1, SigningOperation.OPEN))
        endpoint.process(request(2, SigningOperation.AUTHORIZE))
        assertEquals(SigningStatus.FAILED, endpoint.process(request(3, SigningOperation.AUTHORIZE)).status)
        assertEquals(1, card.verifies); assertEquals(1, card.closes)
    }
}
