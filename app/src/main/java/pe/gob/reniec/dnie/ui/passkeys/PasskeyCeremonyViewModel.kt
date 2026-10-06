package pe.gob.reniec.dnie.ui.passkeys

import android.app.Application
import android.content.Intent
import android.nfc.Tag
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.gob.reniec.dnie.core.nfc.*
import pe.gob.reniec.dnie.data.preferences.CanStorage
import pe.gob.reniec.dnie.passkeys.android.*

data class PasskeyUiState(val title: String = "Passkeys con DNIe", val description: String = "Validando solicitud…",
    val can: String = "", val pin: String = "", val ready: Boolean = false, val awaiting: Boolean = false,
    val busy: Boolean = false, val error: String? = null, val accounts: List<PasskeyAccount> = emptyList(),
    val result: Intent? = null, val managing: Boolean = false, val deleteId: String? = null,
    val biometricAvailable: Boolean = false, val biometricCipher: javax.crypto.Cipher? = null) {
    override fun toString() = "PasskeyUiState(ready=$ready, awaiting=$awaiting, busy=$busy, privateData=redacted)"
}

@RequiresApi(34)
class PasskeyCeremonyViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(PasskeyUiState())
    val state = mutable.asStateFlow()
    private var ceremony: PasskeyCeremony? = null
    private var job: Job? = null
    private var initialized = false
    private var generation = 0
    private val device = PasskeyDeviceVault(application)
    private val repository = EncryptedPasskeyRepository(application)
    private var biometricPending: PasskeyDeviceVault.Pending? = null
    private val diagnosticFlow = java.util.UUID.randomUUID().toString()
    @Volatile private var card: DnieTransceiver? = null
    fun initialize(intent: Intent) {
        if (initialized) return
        initialized = true
        job = viewModelScope.launch {
            pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "validation_started", "flow" to diagnosticFlow)
            try {
                val request = PasskeyRequestAdapter(getApplication()).prepare(intent)
                ceremony = request
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "validation_accepted", "flow" to diagnosticFlow)
                if (request is PasskeyCeremony.Get) {
                    mutable.value = mutable.value.copy(title = request.title, busy = true, description = "Completando la autorización del DNI…")
                    val continued = PasskeyOperations(getApplication()).continueGet(request)
                    if (continued != null) {
                        ensureActive()
                        mutable.value = PasskeyUiState(title = request.title, result = continued.response)
                        return@launch
                    }
                    pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "continuation_unavailable")
                }
                val can = withContext(Dispatchers.IO) { CanStorage.getSavedCan(getApplication()) }
                    .takeIf { it.length == 6 && it.all { character -> character in '0'..'9' } }.orEmpty()
                val biometricAvailable = withContext(Dispatchers.IO) {
                    (request is PasskeyCeremony.Get || request is PasskeyCeremony.Discover) && device.enabled()
                }
                mutable.value = PasskeyUiState(title = request.title, can = can, ready = true, biometricAvailable = biometricAvailable, managing = request == PasskeyCeremony.Manage,
                    description = when (request) {
                        is PasskeyCeremony.Create -> "Crear passkey para ${request.options.username}. La clave quedará cifrada para este DNI."
                        is PasskeyCeremony.Get -> "Autorizar el inicio de sesión con la passkey seleccionada."
                        is PasskeyCeremony.Discover -> "Autoriza con DNI y elige tu cuenta. La autorización sirve una sola vez y caduca en 60 segundos."
                        PasskeyCeremony.Manage -> "Ver tus cuentas o eliminar una passkey. Cada operación requiere DNI y PIN."
                    })
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) {
                intent.getStringExtra(PasskeyProviderIntents.AUTHORIZATION)?.let { PasskeyAuthorizations.continuation.cancel(it) }
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.failure("passkeys", "prepare", failure, diagnosticFlow)
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "validation_failed", "flow" to diagnosticFlow,
                    "code" to ((failure as? PasskeyValidationFailure)?.code?.name ?: "UNKNOWN"))
                mutable.value = mutable.value.copy(error = PasskeyValidationMessages.describe(failure), ready = false, busy = false)
            }
        }
    }
    fun can(value: String) { if (!state.value.busy && !state.value.awaiting) mutable.value = state.value.copy(can = value.filter { it in '0'..'9' }.take(6)) }
    fun requestBiometric() {
        if (!state.value.ready || !state.value.biometricAvailable || state.value.busy || state.value.awaiting) return
        mutable.value = state.value.copy(busy = true, pin = "", error = null)
        job = viewModelScope.launch {
            try {
                val pending = withContext(Dispatchers.IO) { device.prepare(repository.revision()) }
                biometricPending = pending
                mutable.value = state.value.copy(biometricCipher = pending.cipher)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { biometricPending = null; mutable.value = state.value.copy(busy = false, biometricAvailable = false,
                error = "La autorización biométrica ya no está disponible. Usa el DNI y vuelve a habilitarla en configuración.") }
        }
    }
    fun biometricCancelled() {
        biometricPending = null
        mutable.value = state.value.copy(busy = false, biometricCipher = null)
    }
    fun biometric(cipher: javax.crypto.Cipher) {
        val pending = biometricPending ?: return
        val request = ceremony ?: return
        biometricPending = null
        mutable.value = state.value.copy(biometricCipher = null)
        val current = ++generation
        job = viewModelScope.launch {
            var token: String? = null
            try {
                val result = withContext(Dispatchers.IO) {
                    device.open(pending, cipher, repository.revision()).use { catalog ->
                        PasskeyOperations(getApplication()).executeBiometric(request, catalog, pending.revision)
                    }
                }
                token = result.continuationToken
                ensureActive()
                if (current == generation) mutable.value = state.value.copy(busy = false, result = result.response)
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "biometric_completed", "flow" to diagnosticFlow)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.failure("passkeys", "biometric", e, diagnosticFlow)
                if (current == generation) mutable.value = state.value.copy(busy = false, error = "No se completó la autorización biométrica. Puedes usar el DNI.")
            } finally { if (state.value.result == null || current != generation) token?.let { PasskeyAuthorizations.continuation.cancel(it) } }
        }
    }
    fun pin(value: String) { if (!state.value.busy && !state.value.awaiting) mutable.value = state.value.copy(pin = value.filter { it in '0'..'9' }.take(8)) }
    fun request(deleteId: String? = null) {
        val value = state.value
        if (!value.ready || value.busy || value.awaiting || value.can.length != 6 || value.pin.length !in 4..8) return
        mutable.value = value.copy(awaiting = true, error = null, deleteId = deleteId, accounts = emptyList())
        pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "awaiting_card", "flow" to diagnosticFlow)
    }
    fun tag(tag: Tag) {
        val value = state.value
        if (!value.awaiting || value.busy) return
        val request = ceremony ?: return
        pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "card_detected", "flow" to diagnosticFlow)
        val channel = IsoDepCardChannel.fromTag(tag) ?: return
        val activeCard = DnieTransceiver(channel = channel, can = value.can,
            logger = pe.gob.reniec.dnie.core.logging.ApduLogger(enabled = false))
        card = activeCard
        mutable.value = value.copy(awaiting = false, busy = true, pin = "")
        val current = ++generation
        job = viewModelScope.launch {
            var continuationToken: String? = null
            try {
                val result = PasskeyOperations(getApplication()).execute(request, activeCard, value.pin, value.deleteId)
                continuationToken = result.continuationToken
                ensureActive()
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "operation_completed", "flow" to diagnosticFlow)
                if (current == generation) mutable.value = state.value.copy(busy = false, result = result.response,
                    accounts = result.accounts, deleteId = null)
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) {
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.failure("passkeys", "execute", failure, diagnosticFlow)
                if (current == generation) mutable.value = state.value.copy(busy = false, deleteId = null,
                    error = safeError(failure))
            } finally {
                if (state.value.result == null || current != generation) continuationToken?.let { PasskeyAuthorizations.continuation.cancel(it) }
                activeCard.close(); if (card === activeCard) card = null
            }
        }
    }
    fun background() {
        if (state.value.result == null) (ceremony as? PasskeyCeremony.Get)?.authorization?.let { PasskeyAuthorizations.continuation.cancel(it) }
        pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "background_cancel", "flow" to diagnosticFlow)
        ++generation; job?.cancel(); card?.close(); card = null
        biometricPending = null
        mutable.value = state.value.copy(pin = "", awaiting = false, busy = false, accounts = emptyList(), deleteId = null, biometricCipher = null)
        // Restart validation if Android paused us while the request was being verified.
        if (ceremony == null || (ceremony is PasskeyCeremony.Get && !state.value.ready && state.value.result == null)) {
            ceremony = null
            initialized = false
        }
    }
    override fun onCleared() { background(); super.onCleared() }
    private fun safeError(failure: Exception): String {
        val message = failure.message.orEmpty()
        return when {
            message.startsWith("PIN incorrecto.") -> message
            message == "PIN bloqueado" -> message
            message.startsWith("Estas passkeys requieren otro DNI") -> message
            message == "No hay passkeys registradas" -> message
            message == "La credencial ya está registrada" -> message
            else -> "No se completó la operación. Comprueba el DNI y mantenlo junto al lector. El PIN no se reintentó."
        }
    }
}
