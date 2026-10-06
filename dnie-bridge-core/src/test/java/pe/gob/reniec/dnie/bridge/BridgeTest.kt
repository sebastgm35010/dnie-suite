package pe.gob.reniec.dnie.bridge

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

class BridgeTest {
    @Test fun fragmentedAndConsecutiveFrames() {
        val bytes = ByteArrayOutputStream()
        VpcdFrames.write(bytes, byteArrayOf(4))
        VpcdFrames.write(bytes, byteArrayOf(0, 0xB0.toByte(), 0, 0, 0))
        val input = object : ByteArrayInputStream(bytes.toByteArray()) {
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 1))
        }
        assertArrayEquals(byteArrayOf(4), VpcdFrames.read(input))
        assertArrayEquals(byteArrayOf(0, 0xB0.toByte(), 0, 0, 0), VpcdFrames.read(input))
        assertNull(VpcdFrames.read(input))
    }
    @Test fun incompleteAndZeroFramesRejected() {
        assertThrows(EOFException::class.java) { VpcdFrames.read(ByteArrayInputStream(byteArrayOf(0, 2, 4))) }
        assertThrows(IllegalArgumentException::class.java) { VpcdFrames.read(ByteArrayInputStream(byteArrayOf(0, 0))) }
    }
    private class Card : BridgeCard {
        var calls = 0
        var closed = false
        override val atr = byteArrayOf(0x3B, 0x80.toByte(), 0x80.toByte(), 1, 1)
        override fun transmit(command: ByteArray): ByteArray {
            calls++
            return if (command[1] == 0xA4.toByte()) byteArrayOf(0x90.toByte(), 0)
                else byteArrayOf(0x62, 0x82.toByte())
        }
        override fun close() { closed = true }
    }
    @Test fun mutationsAndProtectedCommandsNeverReachCard() {
        val card = Card()
        val bridge = ReadOnlyBridge(card)
        for (ins in 0..255) {
            if (ins == 0xB0) continue
            assertArrayEquals(byteArrayOf(0x69, 0x85.toByte()), bridge.process(byteArrayOf(0, ins.toByte(), 0, 0, 0)))
        }
        assertArrayEquals(byteArrayOf(0x69, 0x85.toByte()), bridge.process(byteArrayOf(0x0C, 0xB0.toByte(), 0, 0, 0)))
        assertEquals(0, card.calls)
    }
    @Test fun resetsRetireSessionAndReadPreservesActualStatus() {
        val card = Card()
        val bridge = ReadOnlyBridge(card)
        assertArrayEquals(byteArrayOf(0x69, 0x85.toByte()), bridge.process(byteArrayOf(0, 0xB0.toByte(), 0, 0, 0)))
        assertEquals(0, card.calls)
        bridge.process(byteArrayOf(0, 0xA4.toByte(), 2, 4, 2, 0xCE.toByte(), 0x82.toByte()))
        assertArrayEquals(byteArrayOf(0x62, 0x82.toByte()), bridge.process(byteArrayOf(0, 0xB0.toByte(), 0, 0, 0)))
        assertThrows(IllegalStateException::class.java) { bridge.process(byteArrayOf(2)) }
        assertTrue(card.closed)
        assertThrows(IllegalStateException::class.java) { bridge.process(byteArrayOf(4)) }
    }
}
