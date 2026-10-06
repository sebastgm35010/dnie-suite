package pe.gob.reniec.dnie.ui

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.passkeys.android.*
import pe.gob.reniec.dnie.ui.passkeys.PasskeyValidationMessages

class PasskeyValidationMessagesTest {
    @Test fun everyValidationStageHasDistinctCodeWithoutPrivateCause() {
        val messages = PasskeyValidationCode.entries.map { code ->
            PasskeyValidationMessages.describe(PasskeyValidationFailure(code, IllegalArgumentException("private-test-can-pin-challenge"))).also {
                assertTrue(it.endsWith("[PK_${code.name}]"))
                assertFalse(it.contains("private-test-can-pin-challenge"))
            }
        }
        assertEquals(PasskeyValidationCode.entries.size, messages.toSet().size)
    }
    @Test fun unknownErrorDoesNotAccuseSiteOfDenyingAuthorization() {
        val message = PasskeyValidationMessages.describe(IllegalStateException("private data"))
        assertTrue(message.contains("PK_UNKNOWN")); assertFalse(message.contains("private data"))
        assertFalse(message.contains("no delega"))
    }
}
