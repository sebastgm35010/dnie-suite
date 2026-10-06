package pe.gob.reniec.dnie.passkeys.android

enum class PasskeyValidationCode {
    REQUEST_MISSING, CREATE_OPTIONS, GET_OPTIONS, DISCOVERY_OPTIONS, CALLER_MISSING,
    BROWSER_IDENTITY, BROWSER_HASH, ORIGIN_INVALID, NATIVE_HASH, NATIVE_SIGNER,
    DAL_NETWORK, DAL_DENIED, SELECTION, UNKNOWN
}

/** Stable diagnostics contain no JSON, challenge, account name, CAN or PIN. */
class PasskeyValidationFailure(val code: PasskeyValidationCode, cause: Exception? = null) : Exception(code.name, cause)

internal suspend fun <T> validation(code: PasskeyValidationCode, block: suspend () -> T): T = try {
    block()
} catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
catch (failure: PasskeyValidationFailure) { throw failure }
catch (failure: Exception) {
    pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "validation_rejected", "code" to code.name,
        "rule" to ((failure as? pe.gob.reniec.dnie.passkeys.PasskeyOptionFailure)?.rule ?: "unspecified"))
    pe.gob.reniec.dnie.diagnostics.DiagnosticLog.failure("passkeys", code.name, failure)
    throw PasskeyValidationFailure(code, failure)
}
