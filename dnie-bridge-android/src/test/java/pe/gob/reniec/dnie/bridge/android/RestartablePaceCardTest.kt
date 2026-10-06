package pe.gob.reniec.dnie.bridge.android

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel
import pe.gob.reniec.dnie.bridge.ReadOnlyBridge
import pe.gob.reniec.dnie.bridge.TransparentBridge

class RestartablePaceCardTest {
    private class Channel : DnieCardChannel {
        var closed = false
        var calls = 0
        override fun transceive(command: ByteArray): ByteArray {
            check(!closed); calls++; return byteArrayOf(0x90.toByte(), 0)
        }
        override fun close() { closed = true }
        override fun isConnected() = !closed
        override fun setTimeout(timeoutMs: Int) {}
    }
    private val atr = byteArrayOf(0x3B, 0x80.toByte(), 0x80.toByte(), 1, 1)
    @Test fun unrestrictedLogicalCommandsAndResumeAfterPowerOff() {
        val commands = mutableListOf<ByteArray>()
        var handshakes = 0
        val channels = mutableListOf<Channel>()
        val card = RestartablePaceCard.open("123456", atr,
            { Channel().also { channels.add(it) } }, establish = { physical, _ ->
                handshakes++
                object : DnieCardChannel by physical {
                    override fun transceive(command: ByteArray): ByteArray {
                        commands.add(command.copyOf())
                        return physical.transceive(command)
                    }
                }
            })
        TransparentBridge(card).use { bridge ->
            val select = byteArrayOf(0, 0xA4.toByte(), 2, 4, 2, 0x50, 0x31, 0)
            val verify = byteArrayOf(0, 0x20, 0, 1, 1, 0x31)
            assertArrayEquals(byteArrayOf(0x90.toByte(), 0), bridge.process(select))
            bridge.process(byteArrayOf(0))
            assertTrue(channels[0].closed)
            assertArrayEquals(byteArrayOf(0x90.toByte(), 0), bridge.process(verify))
            assertEquals(2, handshakes)
            assertArrayEquals(select, commands[0])
            assertArrayEquals(verify, commands[1])
        }
        assertTrue(channels.all { it.closed })
    }
    @Test fun resetRebuildsAndPowerOffRequiresNewPace() {
        val channels = mutableListOf<Channel>()
        var handshakes = 0
        val card = RestartablePaceCard.open("123456", atr,
            { Channel().also { channels.add(it) } }, establish = { physical, can ->
                assertEquals("123456", can); handshakes++; physical
            })
        val bridge = ReadOnlyBridge(card)
        bridge.process(byteArrayOf(0, 0xA4.toByte(), 2, 4, 2, 0xCE.toByte(), 0x82.toByte()))
        assertArrayEquals(byteArrayOf(1), bridge.process(byteArrayOf(5)))
        assertEquals(1, channels[0].calls) // Heartbeat never touches NFC or selection.
        assertArrayEquals(atr, bridge.process(byteArrayOf(2)))
        assertTrue(channels[0].closed)
        assertEquals(2, handshakes)
        assertArrayEquals(byteArrayOf(0x69, 0x85.toByte()), bridge.process(byteArrayOf(0, 0xB0.toByte(), 0, 0, 0)))
        assertEquals(0, channels[1].calls) // Reset clears certificate selection.
        assertArrayEquals(byteArrayOf(1), bridge.process(byteArrayOf(0)))
        assertTrue(channels[1].closed)
        assertArrayEquals(atr, bridge.process(byteArrayOf(2)))
        assertEquals(3, handshakes)
        bridge.close()
        assertTrue(channels.all { it.closed })
        assertThrows(IllegalStateException::class.java) { card.reset() }
    }
    @Test fun failedReopenRetiresBothAdaptersAndTransport() {
        val channels = mutableListOf<Channel>()
        var attempts = 0
        val card = RestartablePaceCard.open("123456", atr,
            { Channel().also { channels.add(it) } }, establish = { physical, _ ->
                if (++attempts > 1) error("simulated lost tag")
                physical
            })
        val bridge = ReadOnlyBridge(card)
        assertThrows(IllegalStateException::class.java) { bridge.process(byteArrayOf(2)) }
        assertTrue(channels.all { it.closed })
        assertThrows(IllegalStateException::class.java) { bridge.process(byteArrayOf(4)) }
    }
    @Test fun knownPhysicalLossIsNotReportedAsHealthyHeartbeat() {
        val channel = Channel()
        val card = RestartablePaceCard.open("123456", atr, { channel }, establish = { physical, _ -> physical })
        val bridge = ReadOnlyBridge(card)
        channel.close()
        assertThrows(IllegalStateException::class.java) { bridge.process(byteArrayOf(5)) }
        assertThrows(IllegalStateException::class.java) { bridge.process(byteArrayOf(4)) }
    }
}
