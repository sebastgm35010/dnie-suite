package pe.gob.reniec.dnie.nfc

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel
import pe.gob.reniec.dnie.core.nfc.PaceBridgeChannel

class PaceBridgeChannelTest {
    @Test fun missingIasClosesFreshTransport() {
        var closed = false
        var calls = 0
        val card = object : DnieCardChannel {
            override fun transceive(command: ByteArray): ByteArray {
                calls++
                return byteArrayOf(0x6A, 0x82.toByte())
            }
            override fun isConnected() = !closed
            override fun setTimeout(timeoutMs: Int) {}
            override fun close() { closed = true }
        }
        assertThrows(IllegalStateException::class.java) { PaceBridgeChannel.open(card, "123456") }
        assertEquals(1, calls)
        assertTrue(closed)
    }
}
