package pe.gob.reniec.dnie.passkeys.android

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PasskeyValidationFailureTest {
    @Test fun recordsStageAndDoesNotExposePrivateCauseInMessage() = runBlocking {
        try {
            validation(PasskeyValidationCode.CREATE_OPTIONS) { throw IllegalArgumentException("private JSON") }
            fail("Unexpected success")
        } catch (failure: PasskeyValidationFailure) {
            assertEquals(PasskeyValidationCode.CREATE_OPTIONS, failure.code)
            assertEquals("CREATE_OPTIONS", failure.message)
        }
    }
    @Test fun nestedIdentityFailureKeepsSpecificCode() = runBlocking {
        try {
            validation(PasskeyValidationCode.CREATE_OPTIONS) { throw PasskeyValidationFailure(PasskeyValidationCode.BROWSER_IDENTITY) }
            fail("Unexpected success")
        } catch (failure: PasskeyValidationFailure) { assertEquals(PasskeyValidationCode.BROWSER_IDENTITY, failure.code) }
    }
    @Test fun cancellationRemainsCancellation() = runBlocking {
        try {
            validation(PasskeyValidationCode.CREATE_OPTIONS) { throw CancellationException("Cancelled") }
            fail("Unexpected success")
        } catch (_: CancellationException) {}
    }
}
