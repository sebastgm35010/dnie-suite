package pe.gob.reniec.dnie.nfc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel
import pe.gob.reniec.dnie.core.nfc.DnieTransceiver

class VaultProbeProtocolTest {
    @Test fun privatePlaintextNeverEntersLoggerHistoryOrReplay() {
        val secret = ByteArray(32) { 0x5A }
        val logger = ApduLogger()
        logger.logRx(ResponseApdu(secret, 0x90, 0), description = "PSO DECIPHER", redactData = true)
        assertEquals("[DATOS PRIVADOS OCULTOS] 9000", logger.getHistory().single().apduHex)
        assertEquals(logger.getHistory().single(), logger.events.replayCache.single())
        assertFalse(logger.getHistory().single().toString().contains("5A 5A"))
    }

    @Test fun refusesUnverifiedSessionBeforeAnyPrivateCommandAndClosesIt() = runTest {
        var exchanges = 0
        var closed = false
        val card = object : DnieCardChannel {
            override fun transceive(command: ByteArray): ByteArray { exchanges++; error("Must not reach card") }
            override fun isConnected() = !closed
            override fun setTimeout(timeoutMs: Int) {}
            override fun close() { closed = true }
        }
        val client = DnieTransceiver(card, ioDispatcher = Dispatchers.Unconfined)
        assertTrue(client.decipherTestSecret(ByteArray(256)).isFailure)
        assertEquals(0, exchanges)
        assertTrue(closed)
        assertTrue(client.signSha256Hash(ByteArray(32)).isFailure)
    }
}
