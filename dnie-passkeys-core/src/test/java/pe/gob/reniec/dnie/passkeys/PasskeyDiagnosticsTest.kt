package pe.gob.reniec.dnie.passkeys

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.diagnostics.*

class PasskeyDiagnosticsTest {
    @After fun cleanup() { DiagnosticLog.install(null) }
    @Test fun logsOptionsAndLengthsWithoutAccountsChallengesOrCustomExtensionNames() {
        val events = mutableListOf<DiagnosticEvent>(); DiagnosticLog.install { events += it }
        PasskeyRequestDiagnostics.capture("""{"user":{"name":"private-account","displayName":"private-owner","id":"private-handle"},"challenge":"private-challenge","pubKeyCredParams":[{"type":"public-key","alg":-257}],"authenticatorSelection":{"authenticatorAttachment":"cross-platform"},"extensions":{"private-extension":{},"prf":{}}}""")
        val event = events.single()
        assertEquals("-257", event.fields["algorithm"]); assertEquals("cross-platform", event.fields["attachment"])
        assertEquals("prf", event.fields["extensions"]); assertEquals("1", event.fields["unknownExtensions"])
        assertFalse(event.toString().contains("private-"))
    }
    @Test fun reportsExactRejectedFieldWithoutItsValue() {
        val events = mutableListOf<DiagnosticEvent>(); DiagnosticLog.install { events += it }
        try { PasskeyJson.text(PasskeyJson.parse("""{"displayName":""}"""), "displayName", 256); fail("Unexpected success") }
        catch (failure: PasskeyOptionFailure) { assertEquals("FIELD_displayName_LENGTH", failure.rule) }
        assertEquals("FIELD_displayName_LENGTH", events.single().fields["rule"])
    }
}
