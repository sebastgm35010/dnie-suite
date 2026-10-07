package pe.gob.reniec.dnie.nfc
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.logging.ApduLogger
class ProductionLoggerTest {
    @Test fun quietDefaultRetainsNoHistoryOrReplay() {
        try {
            ApduLogger.configureDefault(false)
            ApduLogger.default.logInfo("synthetic event")
            assertTrue(ApduLogger.default.getHistory().isEmpty())
            assertTrue(ApduLogger.default.events.replayCache.isEmpty())
        } finally { ApduLogger.configureDefault(true) }
    }
}
