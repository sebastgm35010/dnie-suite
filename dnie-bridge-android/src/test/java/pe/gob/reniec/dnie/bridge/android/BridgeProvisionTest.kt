package pe.gob.reniec.dnie.bridge.android

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class BridgeProvisionTest {
    private val valid = """{"version":1,"host":"192.168.1.5","port":39443,"certificateSha256":"${"AB".repeat(32)}","pairingTokenBase64":"${"A".repeat(43)}=","expiresAt":"2030-01-01T00:00:00.1234567Z"}"""

    @Test fun windowsProvisionWithFractionalUtc() {
        BridgeProvision.read(ByteArrayInputStream(valid.toByteArray()), 0).use {
            assertEquals("192.168.1.5", it.host)
            assertEquals(1893456000123L, it.expiresAtEpochMillis)
            assertEquals(32, it.secret.size)
            assertEquals(0xab.toByte(), it.pin[0])
        }
    }
    @Test fun invalidAndExpiredHaveNoPayloadInErrors() {
        for (bad in listOf(valid.replace("2030-01-01", "2020-02-30"),
            valid.replace("39443", "80"), valid.replace("192.168.1.5", "https://evil.test"),
            valid.replace("\"version\":1", "\"version\":2"), valid.replace("ABAB", "XXXX"))) {
            val failure = assertThrows(IllegalArgumentException::class.java) { BridgeProvision.parse(bad, 0) }
            assertNull(failure.cause)
            assertEquals("Emparejamiento inválido o vencido", failure.message)
        }
        assertThrows(IllegalArgumentException::class.java) { BridgeProvision.parse(valid, Long.MAX_VALUE) }
    }
    @Test fun boundStopsAtLimit() {
        val stream = ByteArrayInputStream(ByteArray(100_000))
        assertThrows(IllegalArgumentException::class.java) { BridgeProvision.read(stream, 0) }
        assertEquals(100_000 - 8193, stream.available())
    }
}
