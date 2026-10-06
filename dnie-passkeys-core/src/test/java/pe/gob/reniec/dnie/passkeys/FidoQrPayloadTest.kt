package pe.gob.reniec.dnie.passkeys

import org.junit.Assert.*
import org.junit.Test

class FidoQrPayloadTest {
    @Test fun acceptsOnlyBoundedNumericFidoEnvelope() {
        assertEquals("FIDO:/0123456789", FidoQrPayload.validate("FIDO:/0123456789"))
        listOf("FIDO:/", "fido:/123", "FIDO://123", "FIDO:/123?secret=4", "https://example.com", "FIDO:/１２３", "FIDO:/123\n", "FIDO:/" + "1".repeat(4096)).forEach {
            try { FidoQrPayload.validate(it); fail("Unexpected accepted envelope") } catch (_: IllegalArgumentException) {}
        }
    }
}
