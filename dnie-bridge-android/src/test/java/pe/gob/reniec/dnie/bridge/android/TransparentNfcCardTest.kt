package pe.gob.reniec.dnie.bridge.android

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel

class TransparentNfcCardTest {
    @Test fun resumesRawAfterPowerOffWithoutIntroducingPace() {
        val opened = mutableListOf<Physical>()
        val card = TransparentNfcCard(byteArrayOf(0x3b, 0)) { Physical().also { opened.add(it) } }
        card.powerOff()
        val protected = byteArrayOf(0x0c, 0xa4.toByte(), 2, 4, 0)
        assertArrayEquals(protected, card.transmit(protected))
        assertEquals(2, opened.size)
        assertEquals(1, opened.last().commands)
        assertTrue(opened.first().closed)
        card.close()
    }
    private class Physical : DnieCardChannel {
        var closed = false
        var commands = 0
        override fun transceive(command: ByteArray): ByteArray { commands++; return command.copyOf() }
        override fun isConnected() = !closed
        override fun setTimeout(timeoutMs: Int) {}
        override fun close() { closed = true }
    }
    @Test fun noPaceOrApduOnOpenResetPowerOffOrHeartbeat() {
        val opened = mutableListOf<Physical>()
        val card = TransparentNfcCard(byteArrayOf(0x3b, 0)) { Physical().also { opened.add(it) } }
        card.checkPresence(); card.reset()
        assertTrue(opened[0].closed)
        assertEquals(0, opened.sumOf { it.commands })
        val command = byteArrayOf(0, 0xa4.toByte(), 4, 4, 0)
        assertArrayEquals(command, card.transmit(command))
        card.powerOff(); card.checkPresence(); card.reset()
        assertEquals(3, opened.size); assertTrue(opened[1].closed)
        card.close(); assertTrue(opened.last().closed)
        assertThrows(IllegalStateException::class.java) { card.reset() }
    }
}
