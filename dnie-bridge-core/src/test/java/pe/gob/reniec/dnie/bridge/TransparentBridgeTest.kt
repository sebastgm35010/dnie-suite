package pe.gob.reniec.dnie.bridge

import org.junit.Assert.*
import org.junit.Test

class TransparentBridgeTest {
    private class Card : BridgeCard {
        override val atr = byteArrayOf(0x3b, 0x00)
        val commands = mutableListOf<ByteArray>()
        var resets = 0; var powers = 0; var closed = false
        var response = byteArrayOf(0x61, 0x10)
        override fun transmit(command: ByteArray): ByteArray { commands.add(command.copyOf()); return response.copyOf() }
        override fun reset(): ByteArray { resets++; return atr.copyOf() }
        override fun powerOff() { powers++ }
        override fun close() { closed = true }
    }
    @Test fun forwardsAppletPaceSmPinAndGetResponseWithoutRewrite() {
        val card = Card()
        TransparentBridge(card).use { bridge ->
            val commands = listOf(
                byteArrayOf(0, 0xa4.toByte(), 4, 4, 1, 1, 0),
                byteArrayOf(0, 0x22, 0xc1.toByte(), 0xa4.toByte(), 1, 0),
                byteArrayOf(0x10, 0x86.toByte(), 0, 0, 1, 0),
                byteArrayOf(0x0c, 0xb0.toByte(), 0, 0, 1, 0),
                byteArrayOf(0, 0x20, 0, 1, 1, 0),
                byteArrayOf(0, 0xc0.toByte(), 0, 0, 0x10),
                byteArrayOf(0x80.toByte(), 0xff.toByte(), 0, 0)
            )
            commands.forEach { assertArrayEquals(card.response, bridge.process(it)) }
            assertEquals(commands.size, card.commands.size)
            commands.zip(card.commands).forEach { (expected, actual) -> assertArrayEquals(expected, actual) }
        }
        assertTrue(card.closed)
    }
    @Test fun controlsDoNotBecomeApduOrSyntheticSuccessResponses() {
        val card = Card(); val bridge = TransparentBridge(card)
        assertArrayEquals(card.atr, bridge.process(byteArrayOf(4)))
        assertArrayEquals(byteArrayOf(1), bridge.process(byteArrayOf(5)))
        assertArrayEquals(card.atr, bridge.process(byteArrayOf(1)))
        assertArrayEquals(card.atr, bridge.process(byteArrayOf(2)))
        bridge.process(byteArrayOf(0))
        assertEquals(2, card.resets); assertEquals(1, card.powers); assertTrue(card.commands.isEmpty())
        val failure = assertThrows(IllegalStateException::class.java) { bridge.process(byteArrayOf(99)) }
        assertNotNull(failure.cause)
        assertTrue(card.closed)
    }
}
