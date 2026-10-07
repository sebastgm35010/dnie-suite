package pe.gob.reniec.dnie.bridge
import org.junit.Assert.*
import org.junit.Test
class BridgeRuntimeSettingsTest {
    @Test fun refusesUnboundedOrTooShortTimeouts() {
        for (value in listOf(0, -1, 4_999, 30_001, Int.MAX_VALUE)) {
            assertTrue(runCatching { BridgeRuntimeSettings(connectTimeoutMs = value) }.isFailure)
            assertTrue(runCatching { BridgeRuntimeSettings(nfcTimeoutMs = value) }.isFailure)
        }
        for (value in listOf(0, 29_999, 300_001, Int.MAX_VALUE))
            assertTrue(runCatching { BridgeRuntimeSettings(readTimeoutMs = value) }.isFailure)
    }
    @Test fun recommendedSettingsMatchEstablishedNfcEngine() {
        assertEquals(10_000, BridgeRuntimeSettings().nfcTimeoutMs)
        assertEquals(120_000, BridgeRuntimeSettings().readTimeoutMs)
    }
}
