package pe.gob.reniec.dnie.apdu

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.encodeHex

class HexEncodingTest {
    @Test fun preservesDiagnosticFormatForEveryByteAndCustomSeparators() {
        val bytes = ByteArray(256) { it.toByte() }
        for (separator in listOf(" ", "", ":", "--")) {
            assertEquals(bytes.joinToString(separator) { "%02X".format(it) }, encodeHex(bytes, separator))
        }
        assertEquals("", encodeHex(byteArrayOf()))
        assertEquals("FF", encodeHex(byteArrayOf(0xFF.toByte())))
    }
}
