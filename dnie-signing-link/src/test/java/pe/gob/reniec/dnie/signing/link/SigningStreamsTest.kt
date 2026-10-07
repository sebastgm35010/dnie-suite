package pe.gob.reniec.dnie.signing.link

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult

class SigningStreamsTest {
    private class Channel(bytes: ByteArray) : SigningDuplex {
        override val input = ByteArrayInputStream(bytes)
        override val output = ByteArrayOutputStream()
        var closes = 0
        override fun close() { closes++ }
    }
    private class Card : DnieTransceiverInterface {
        var closes = 0
        override suspend fun selectPkiApplet() = Result.success(Unit)
        override suspend fun verifyPin(pin: String) = Result.success<PinVerificationResult>(PinVerificationResult.Success)
        override suspend fun readCertificate(type: CertificateType) = Result.success(byteArrayOf(1))
        override suspend fun signSha256Hash(hash32Bytes: ByteArray) = Result.success(ByteArray(256))
        override fun close() { closes++ }
    }
    @Test fun serverProcessesFramesAndClosesCardOnEof() = runBlocking {
        val id = UUID.randomUUID()
        val bytes = ByteArrayOutputStream()
        listOf(SigningOperation.OPEN, SigningOperation.AUTHORIZE, SigningOperation.CERTIFICATE, SigningOperation.SIGN_SHA256)
            .forEachIndexed { index, operation -> SigningFrames.write(bytes, SigningFrame(id, index + 1L,
                operation, SigningStatus.REQUEST, if (operation == SigningOperation.SIGN_SHA256) ByteArray(32) else byteArrayOf())) }
        val channel = Channel(bytes.toByteArray()); val card = Card()
        val connection = AuthorizedSigningConnection(channel, AuthorizedSigningEndpoint(id, card, "1234".toCharArray(), 1))
        connection.run(); connection.close()
        val replies = ByteArrayInputStream(channel.output.toByteArray())
        repeat(4) { assertEquals(SigningStatus.OK, SigningFrames.read(replies)!!.status) }
        assertNull(SigningFrames.read(replies))
        assertEquals(1, card.closes); assertEquals(1, channel.closes)
    }
    @Test fun brokenFrameClosesEndpointWithoutAReply() = runBlocking {
        val channel = Channel(byteArrayOf(1)); val card = Card()
        val connection = AuthorizedSigningConnection(channel, AuthorizedSigningEndpoint(UUID.randomUUID(), card, "1234".toCharArray(), 1))
        assertTrue(runCatching { connection.run() }.isFailure)
        assertEquals(0, channel.output.size()); assertEquals(1, card.closes); assertEquals(1, channel.closes)
    }
    @Test fun clientDoesNotRetryWhenPeerCloses() = runBlocking {
        val channel = Channel(byteArrayOf()); val exchange = StreamSigningExchange(channel)
        val request = SigningFrame(UUID.randomUUID(), 1, SigningOperation.OPEN, SigningStatus.REQUEST)
        assertTrue(runCatching { exchange.exchange(request) }.isFailure)
        val bytesSent = channel.output.size()
        assertTrue(runCatching { exchange.exchange(request) }.isFailure)
        assertEquals(bytesSent, channel.output.size()); assertEquals(1, channel.closes)
    }
}
