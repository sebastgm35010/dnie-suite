package pe.gob.reniec.dnie.diagnostics

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class DiagnosticLogTest {
    @After fun cleanup() { DiagnosticLog.install(null) }
    @Test fun dropsSecretFieldsAndDoesNotSerializeThrowableMessages() {
        val events = mutableListOf<DiagnosticEvent>(); DiagnosticLog.install { events += it }
        DiagnosticLog.event("vault", "test", "stage" to "create", "pin" to "private-pin", "can" to "private-can", "requestJson" to "private-request")
        DiagnosticLog.failure("vault", "create", IllegalArgumentException("private-secret-message"))
        assertEquals(mapOf("stage" to "create"), events.first().fields)
        assertFalse(events.toString().contains("private-"))
    }
    @Test fun captureFailureCannotBreakBusinessOperation() {
        DiagnosticLog.install { throw IllegalStateException("Storage failure") }
        DiagnosticLog.event("app", "test", "stage" to "running")
    }
    @Test fun multilineValuesAreBoundedAndCannotCreateFakeRecords() {
        var event: DiagnosticEvent? = null; DiagnosticLog.install { event = it }
        DiagnosticLog.event("app", "test", "state" to ("x".repeat(3000) + "\nFAKE"))
        assertEquals(2048, event!!.fields.getValue("state").length)
        DiagnosticLog.event("app", "test", "state" to "running\nFAKE")
        assertFalse(event!!.fields.getValue("state").contains('\n'))
    }
}
