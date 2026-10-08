package pe.gob.reniec.dnie.passkeys.android

import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.*
import androidx.credentials.provider.*
import pe.gob.reniec.dnie.diagnostics.DiagnosticLog

@RequiresApi(34)
class DnieCredentialProviderService : CredentialProviderService() {
    override fun onBeginCreateCredentialRequest(request: BeginCreateCredentialRequest, cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>) {
        if (cancellationSignal.isCanceled) return
        DiagnosticLog.event("passkeys", "provider_create_query", "state" to if (request is BeginCreatePublicKeyCredentialRequest) "public_key" else "unsupported_type")
        callback.onResult(if (request is BeginCreatePublicKeyCredentialRequest)
            BeginCreateCredentialResponse(listOf(CreateEntry(if (IndependentPasskeyVault(this).exists()) "DNIe · almacén independiente con huella" else "DNIe · autorizar con NFC y PIN", PasskeyProviderIntents.pending(this, "create"))))
            else BeginCreateCredentialResponse())
    }
    override fun onBeginGetCredentialRequest(request: BeginGetCredentialRequest, cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>) {
        if (cancellationSignal.isCanceled) return
        DiagnosticLog.event("passkeys", "provider_get_query", "count" to request.beginGetCredentialOptions.size)
        val available = request.beginGetCredentialOptions.any { it is BeginGetPublicKeyCredentialOption } && (IndependentPasskeyVault(this).exists() || EncryptedPasskeyRepository(this).exists())
        callback.onResult(if (available) BeginGetCredentialResponse(authenticationActions = listOf(
            AuthenticationAction("Desbloquear passkeys con DNIe", PasskeyProviderIntents.pending(this, "discover")))) else BeginGetCredentialResponse())
    }
    override fun onClearCredentialStateRequest(request: ProviderClearCredentialStateRequest, cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>) {
        // Nothing is cached across ceremonies. Signing out must never delete stored credentials.
        DiagnosticLog.event("passkeys", "provider_clear_state")
        PasskeyAuthorizations.continuation.cancel()
        PasskeyOriginVerifier.clearDelegations()
        if (!cancellationSignal.isCanceled) callback.onResult(null)
    }
}
