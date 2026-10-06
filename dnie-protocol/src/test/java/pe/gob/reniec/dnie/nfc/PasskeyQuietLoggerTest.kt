package pe.gob.reniec.dnie.nfc

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertEquals
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.diagnostics.DiagnosticEvent
import pe.gob.reniec.dnie.diagnostics.DiagnosticLog
import pe.gob.reniec.dnie.diagnostics.DiagnosticSink
import pe.gob.reniec.dnie.core.logging.ApduLogger

class PasskeyQuietLoggerTest {
    @Test fun persistentCaptureKeepsOnlyApduMetadataEvenWhenConsoleIsDisabled() {
        val captured = mutableListOf<DiagnosticEvent>()
        DiagnosticLog.install(DiagnosticSink { captured.add(it) })
        try {
            val logger = ApduLogger(enabled = false)
            logger.logTx(CommandApdu(0x00, 0x20, 0x00, 0x03, byteArrayOf(1, 2, 3, 4)), "private description")
            logger.logRx(ResponseApdu(byteArrayOf(5, 6, 7), 0x90, 0x00), 123, "private response")
            assertEquals(mapOf("header" to "00200003"), captured[0].fields)
            assertEquals(mapOf("status" to "9000", "durationMs" to "123"), captured[1].fields)
            assertTrue(logger.getHistory().isEmpty())
            assertTrue(captured.none { it.toString().contains("private") })
        } finally { DiagnosticLog.install(null) }
    }
    @Test fun disabledLoggerDoesNotKeepSensitiveHistoryOrReplay() {
        val logger = ApduLogger(enabled = false)
        logger.logInfo("synthetic sensitive text")
        assertTrue(logger.getHistory().isEmpty())
        assertTrue(logger.events.replayCache.isEmpty())
    }
}
