package pe.gob.reniec.dnie.signing.link

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class SigningProvisionTest {
    @Test fun strictRoundtripAndExpiry() {
        val pair = SigningProvision(UUID.randomUUID(), "192.168.1.5", 20_000, 3, ByteArray(32) { 1 }, ByteArray(32) { 2 })
        val parsed = SigningProvision.parse(pair.encode(), 10_000)
        assertEquals(pair.sessionId, parsed.sessionId); assertEquals(3, parsed.count)
        assertArrayEquals(pair.token, parsed.token)
        assertFalse(pair.toString().contains("020202"))
        assertTrue(runCatching { SigningProvision.parse(pair.encode(), 20_000) }.isFailure)
        parsed.close(); assertTrue(parsed.token.all { it == 0.toByte() })
    }
    @Test fun rejectsPublicAddressesAmbiguousVersionsAndOversizedRequests() {
        listOf("127.0.0.1", "8.8.8.8", "192.168.1.999", "localhost", "192.168.1.5.evil").forEach {
            assertFalse(SigningProvision.privateAddress(it))
        }
        val encoded = SigningProvision(UUID.randomUUID(), "100.104.106.125", 20_000, 1, ByteArray(32), ByteArray(32)).encode()
        assertTrue(runCatching { SigningProvision.parse(encoded.replace("DNISUITE1", "DNIBRDG1"), 10_000) }.isFailure)
        assertTrue(runCatching { SigningProvision.parse(encoded + "|extra", 10_000) }.isFailure)
        assertTrue(runCatching { SigningProvision.parse("x".repeat(1025), 10_000) }.isFailure)
    }
}
