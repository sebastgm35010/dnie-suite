package pe.gob.reniec.dnie.apdu

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.CommandApdu

class ExtendedApduTest {
    @Test fun encodingMatchesIndependentJdkImplementationAtLengthBoundaries() {
        // Reflection keeps java.smartcardio out of the Android-compatible compilation API.
        val referenceClass = Class.forName("javax.smartcardio.CommandAPDU")
        val intType = Int::class.javaPrimitiveType!!
        val fields = arrayOf(intType, intType, intType, intType, ByteArray::class.java)
        for (size in listOf(0, 1, 255, 256, 257, 65535)) {
            for (le in listOf(null, 1, 256, 257, 65536)) {
                val data = ByteArray(size) { it.toByte() }
                val ours = CommandApdu(0, 0x2A, 0x80.toByte(), 0x86.toByte(), data, le)
                val reference = if (le == null) referenceClass.getConstructor(*fields)
                    .newInstance(0, 0x2A, 0x80, 0x86, data)
                    else referenceClass.getConstructor(*(fields + intType))
                        .newInstance(0, 0x2A, 0x80, 0x86, data, le)
                val bytes = referenceClass.getMethod("getBytes").invoke(reference) as ByteArray
                assertArrayEquals("Nc=$size Ne=$le", bytes, ours.toByteArray())
                assertEquals(ours, CommandApdu.fromBytes(bytes))
            }
        }
    }

    @Test fun explicitExtendedSmallLeSurvivesRoundTrip() {
        val command = CommandApdu(0, 0x2A, 0x80.toByte(), 0x86.toByte(),
            byteArrayOf(0x81.toByte()), le = 256, extended = true)
        assertEquals("002A8086000001810100", command.toHexString(""))
        assertEquals(command, CommandApdu.fromBytes(command.toByteArray()))
        assertEquals(65536, CommandApdu.fromBytes(byteArrayOf(0, 0xC0.toByte(), 0, 0, 0, 0, 0)).le)
    }

    @Test fun rejectsTruncatedInconsistentAndTrailingData() {
        val malformed = listOf(
            byteArrayOf(0, 0x2A, 0, 0, 0, 1),
            byteArrayOf(0, 0x2A, 0, 0, 0, 0, 0, 1),
            byteArrayOf(0, 0x2A, 0, 0, 0, 1, 1, 1),
            byteArrayOf(0, 0x2A, 0, 0, 1, 1, 1, 1),
            byteArrayOf(0, 0x2A, 0, 0, 0, 0, 1, 1, 1)
        )
        malformed.forEach { bytes -> assertThrows(IllegalArgumentException::class.java) { CommandApdu.fromBytes(bytes) } }
        assertThrows(IllegalArgumentException::class.java) { CommandApdu(0, 0, 0, 0, ByteArray(65536)) }
        assertThrows(IllegalArgumentException::class.java) { CommandApdu(0, 0, 0, 0, le = 65537) }
    }
}
