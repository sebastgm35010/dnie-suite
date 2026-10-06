package pe.gob.reniec.dnie.bridge

import org.junit.Assert.*
import org.junit.Test

class ContactlessAtrTest {
    @Test fun realHistoricalBytesAndChecksum() {
        val history = byteArrayOf(0x80.toByte(), 0x31, 0x42)
        val atr = ContactlessAtr.fromTypeAHistory(history)
        assertArrayEquals(history, atr.copyOfRange(4, 7))
        assertEquals(0x83.toByte(), atr[1])
        val checksum: Int = atr.drop(1).fold(0) { acc, byte -> acc xor (byte.toInt() and 255) }
        assertEquals(0, checksum)
        history.fill(0)
        assertEquals(0x31.toByte(), atr[5])
    }
    @Test fun emptyAndMaximumHistory() {
        assertArrayEquals(byteArrayOf(0x3b, 0x80.toByte(), 0x80.toByte(), 1, 1), ContactlessAtr.fromTypeAHistory(byteArrayOf()))
        assertEquals(20, ContactlessAtr.fromTypeAHistory(ByteArray(15)).size)
        assertThrows(IllegalArgumentException::class.java) { ContactlessAtr.fromTypeAHistory(ByteArray(16)) }
    }
}
