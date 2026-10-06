package pe.gob.reniec.dnie.bridge.android

import org.bouncycastle.util.encoders.Base64
import org.junit.Assert.*
import org.junit.Test

class BridgeQrProvisionTest {
    private val now = 1_893_455_400_000L
    private val json = """{"version":1,"host":"127.0.0.1","port":39443,"certificateSha256":"${"AB".repeat(32)}","pairingTokenBase64":"${"A".repeat(43)}=","expiresAt":"2030-01-01T00:00:00Z"}"""
    private fun encode(value: String) = "DNIEBRIDGE1:" + Base64.toBase64String(value.toByteArray())
        .trimEnd('=').replace('+', '-').replace('/', '_')

    @Test fun acceptsWindowsQrAndWipesPairingOnClose() {
        val p = BridgeProvision.parseQr(encode(json), now)
        assertEquals("127.0.0.1", p.host)
        assertEquals(39443, p.port)
        assertEquals(1_893_456_000_000L, p.expiresAtEpochMillis)
        p.close()
        assertTrue(p.secret.all { it == 0.toByte() })
    }
    @Test fun rejectsWrongProtocolOversizeAndMalformedWithoutLeakingPayload() {
        for (payload in listOf(json, "FIDO:/1234", "https://example.test", "DNIEBRIDGE1:!", "DNIEBRIDGE1:" + "A".repeat(2048), encode("{}"))) {
            val e = assertThrows(IllegalArgumentException::class.java) { BridgeProvision.parseQr(payload, now) }
            assertEquals("QR del puente inválido o vencido", e.message)
            assertNull(e.cause)
        }
    }
    @Test fun rejectsExpiredOrLongLivedQr() {
        assertThrows(IllegalArgumentException::class.java) { BridgeProvision.parseQr(encode(json), now + 600_000L) }
        assertThrows(IllegalArgumentException::class.java) { BridgeProvision.parseQr(encode(json), now - 400_000L) }
    }
}
