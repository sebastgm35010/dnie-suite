package pe.gob.reniec.dnie.bridge

import org.junit.Assert.*
import org.junit.Test

class ContactlessReaderCardTest {
    private class Card : BridgeCard {
        override val atr = byteArrayOf(0x3B, 0)
        var calls = 0
        var resets = 0
        var closed = false
        override fun transmit(command: ByteArray): ByteArray { calls++; return byteArrayOf(0x6A, 0x82.toByte()) }
        override fun reset(): ByteArray { resets++; return atr }
        override fun close() { closed = true }
    }
    @Test fun uidQueryDoesNotTouchSecureChannelAndNextCardCommandStillRuns() {
        val card = Card()
        val uid = byteArrayOf(1, 2, 3, 4)
        ContactlessReaderCard(card, uid).use { reader ->
            uid.fill(0)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 0x90.toByte(), 0), reader.transmit(byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 0, 0, 0)))
            assertEquals(0, card.calls)
            assertArrayEquals(byteArrayOf(0x6A, 0x82.toByte()), reader.transmit(byteArrayOf(0, 0xA4.toByte(), 4, 12)))
            assertEquals(1, card.calls)
            assertArrayEquals(card.atr, reader.reset())
            assertEquals(1, card.resets)
        }
        assertTrue(card.closed)
    }
    @Test fun missingUidAtsAndShortBufferNeverReachTheCardOrInventData() {
        val card = Card()
        ContactlessReaderCard(card, byteArrayOf(1, 2, 3, 4)).use { reader ->
            assertArrayEquals(byteArrayOf(0x6C, 4), reader.transmit(byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 0, 0, 2)))
            assertArrayEquals(byteArrayOf(0x6A, 0x81.toByte()), reader.transmit(byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 1, 0, 0)))
            assertEquals(0, card.calls)
        }
        ContactlessReaderCard(Card(), byteArrayOf()).use { reader ->
            assertArrayEquals(byteArrayOf(0x6A, 0x81.toByte()), reader.transmit(byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 0, 0, 0)))
        }
    }
}
