package pe.gob.reniec.dnie.domain.signing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult

class BatchCardSessionTest {
    private class SingleUseCard(private val rejectedAt: Int = 0, private val loseAt: Int = 0) : DnieTransceiverInterface {
        var opens = 0
        var verifies = 0
        var reads = 0
        var signs = 0
        var closes = 0
        var authorized = false
        override suspend fun selectPkiApplet() = Result.success(Unit).also { opens++ }
        override suspend fun verifyPin(pin: String): Result<PinVerificationResult> {
            verifies++
            val result = if (verifies == rejectedAt) PinVerificationResult.InvalidPin(2) else PinVerificationResult.Success
            authorized = result == PinVerificationResult.Success
            return Result.success(result)
        }
        override suspend fun readCertificate(type: CertificateType) = Result.success(byteArrayOf(1)).also { reads++ }
        override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> {
            signs++
            if (signs == loseAt) return Result.failure(IllegalStateException("Canal perdido"))
            if (!authorized) return Result.failure(IllegalStateException("6982"))
            authorized = false
            return Result.success(ByteArray(256))
        }
        override fun close() { closes++ }
    }

    @Test fun consumedAuthorizationIsRenewedWithoutReopeningOrRereadingCertificate() = runTest {
        val card = SingleUseCard()
        BatchCardSession(card, "1234").use { session ->
            session.open()
            repeat(4) { assertTrue(session.sign(ByteArray(32)).isSuccess) }
        }
        assertEquals(1, card.opens)
        assertEquals(1, card.reads)
        assertEquals(4, card.verifies)
        assertEquals(4, card.signs)
        assertEquals(1, card.closes)
    }

    @Test fun rejectedRenewalStopsBeforeNextSignatureAndIsNeverRetried() = runTest {
        val card = SingleUseCard(rejectedAt = 2)
        BatchCardSession(card, "1234").use { session ->
            session.open()
            assertTrue(session.sign(ByteArray(32)).isSuccess)
            assertTrue(session.sign(ByteArray(32)).isFailure)
            assertTrue(session.sign(ByteArray(32)).isFailure)
        }
        assertEquals(2, card.verifies)
        assertEquals(1, card.signs)
        assertEquals(1, card.opens)
    }

    @Test fun transportFailureCannotReopenOrReplayAPrivateOperation() = runTest {
        val card = SingleUseCard(loseAt = 2)
        BatchCardSession(card, "1234").use { session ->
            session.open()
            assertTrue(session.sign(ByteArray(32)).isSuccess)
            assertTrue(session.sign(ByteArray(32)).isFailure)
            assertTrue(session.sign(ByteArray(32)).isFailure)
        }
        assertEquals(2, card.signs)
        assertEquals(2, card.verifies)
        assertEquals(1, card.opens)
    }

    @Test fun closedSessionCannotReuseAuthorizationAndCloseIsIdempotent() = runTest {
        val card = SingleUseCard()
        val session = BatchCardSession(card, "1234")
        session.open()
        session.close()
        session.close()
        assertTrue(session.sign(ByteArray(32)).isFailure)
        assertEquals(0, card.signs)
        assertEquals(1, card.closes)
    }

    @Test fun cancellationDuringRenewalStopsWithoutSigningOrRetrying() = runTest {
        val card = SingleUseCard()
        val cancelling = object : DnieTransceiverInterface by card {
            override suspend fun verifyPin(pin: String): Result<PinVerificationResult> =
                if (card.verifies == 1) throw CancellationException("Cancelado") else card.verifyPin(pin)
        }
        BatchCardSession(cancelling, "1234").use { session ->
            session.open()
            session.sign(ByteArray(32)).getOrThrow()
            try {
                session.sign(ByteArray(32))
                fail("Debe propagar cancelación")
            } catch (_: CancellationException) {}
            assertTrue(session.sign(ByteArray(32)).isFailure)
        }
        assertEquals(1, card.signs)
        assertEquals(1, card.verifies)
        assertEquals(1, card.closes)
    }
}
