package pe.gob.reniec.dnie.ui.passkeys

import android.app.Application
import android.net.Uri
import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import pe.gob.reniec.dnie.passkeys.android.*
import pe.gob.reniec.dnie.core.nfc.*
import pe.gob.reniec.dnie.data.preferences.CanStorage
import javax.crypto.Cipher

data class PasskeyToolsState(val can: String = "", val pin: String = "", val busy: Boolean = false,
    val awaiting: Boolean = false, val message: String = "Respaldo, migración y autorización de este teléfono.",
    val action: String? = null, val biometric: Boolean = false, val cipher: Cipher? = null,
    val newCard: Boolean = false, val previous: Boolean = false) {
    override fun toString() = "PasskeyToolsState(busy=$busy, privateData=redacted)"
}

class PasskeyToolsViewModel(app: Application) : AndroidViewModel(app) {
    private val maintenance = PasskeyMaintenance(app)
    private val device = PasskeyDeviceVault(app)
    private val repository = EncryptedPasskeyRepository(app)
    private val mutable = MutableStateFlow(PasskeyToolsState())
    val state = mutable.asStateFlow()
    private var staged: String? = null
    private var exportTarget: Uri? = null
    private var pending: ByteArray? = null
    private var revision: ByteArray? = null
    private var card: DnieTransceiver? = null
    private var job: Job? = null
    private var expiry: Job? = null
    private val diagnosticFlow = java.util.UUID.randomUUID().toString()
    init { viewModelScope.launch { mutable.value = state.value.copy(can = withContext(Dispatchers.IO) { CanStorage.getSavedCan(app) }, biometric = device.enabled(), previous = maintenance.hasPrevious()) } }
    fun can(value: String) { if (!state.value.busy) mutable.value = state.value.copy(can = value.filter { it in '0'..'9' }.take(6)) }
    fun pin(value: String) { if (!state.value.busy) mutable.value = state.value.copy(pin = value.filter { it in '0'..'9' }.take(8)) }
    fun export(uri: Uri) {
        cancel(); exportTarget = uri
        mutable.value = state.value.copy(action = "export", message = "Autoriza con DNI para exportar las passkeys creadas desde 1.7. Las anteriores no son exportables; se conservarán en este teléfono.")
    }
    fun import(uri: Uri) = work {
        staged?.let { maintenance.discard(it) }; staged = maintenance.stage(uri)
        mutable.value = state.value.copy(action = "restore")
        "Respaldo preparado. Restaurar reemplaza el catálogo actual; se conservará una copia cifrada anterior. Autoriza con el DNI del respaldo."
    }
    fun migration() { cancel(); mutable.value = state.value.copy(action = "migration", message = "Primero autoriza con el DNI que abre tus passkeys actuales.") }
    fun previous() { cancel(); mutable.value = state.value.copy(action = "previous", message = "Reemplazar el catálogo actual por el anterior guardado en este teléfono. Autoriza con el DNI que lo protegía.") }
    fun enrollment() { cancel(); mutable.value = state.value.copy(action = "enroll", message = "Autoriza con DNI y PIN para habilitar la huella en este teléfono. Luego podrás usar las passkeys sin presentar el DNI.") }
    fun disable() = work { device.disable(); PasskeyAuthorizations.continuation.cancel(); mutable.value = state.value.copy(biometric = false); "Acceso biométrico desactivado." }
    fun request() {
        val s = state.value
        if (s.busy || s.action == null || s.can.length != 6 || s.pin.length !in 4..8) return
        mutable.value = s.copy(awaiting = true, message = if (s.newCard) "Acerca el nuevo DNI." else "Acerca el DNI.")
        pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "maintenance_awaiting_card", "stage" to s.action, "mode" to if (s.newCard) "destination" else "source", "flow" to diagnosticFlow)
    }
    fun tag(tag: Tag) {
        val s = state.value; if (!s.awaiting || s.busy) return
        val channel = IsoDepCardChannel.fromTag(tag) ?: return
        val detected = DnieTransceiver(channel = channel, can = s.can, logger = pe.gob.reniec.dnie.core.logging.ApduLogger(enabled = false))
        card = detected
        mutable.value = s.copy(pin = "", busy = true, awaiting = false)
        job = viewModelScope.launch {
            val started = android.os.SystemClock.elapsedRealtime()
            pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "maintenance_started", "stage" to s.action, "flow" to diagnosticFlow)
            try {
                when (s.action) {
                    "previous" -> {
                        maintenance.restorePrevious(detected, s.pin)
                        mutable.value = state.value.copy(action = null, biometric = false, message = "Catálogo anterior recuperado en este teléfono.")
                    }
                    "export" -> {
                        val excluded = maintenance.export(requireNotNull(exportTarget), detected, s.pin)
                        exportTarget = null
                        mutable.value = state.value.copy(action = null, biometric = false,
                            message = "Respaldo cifrado guardado. Credenciales antiguas excluidas: $excluded. Necesitas el DNI de este respaldo para restaurarlo.")
                    }
                    "restore" -> {
                        maintenance.restore(detected, s.pin, requireNotNull(staged)); staged = null
                        mutable.value = state.value.copy(action = null, biometric = false, message = "Passkeys restauradas y autenticadas con el DNI.")
                    }
                    "migration" -> if (!s.newCard) {
                        val snapshot = maintenance.open(detected, s.pin)
                        pending = snapshot.bytes; revision = snapshot.revision
                        armExpiry()
                        mutable.value = state.value.copy(can = "", newCard = true, message = "Ahora ingresa CAN/PIN del nuevo DNI. Tienes 2 minutos para autorizar su clave vigente.")
                    } else {
                        val bytes = requireNotNull(pending).copyOf()
                        val version = requireNotNull(revision).copyOf()
                        try { maintenance.migrate(detected, s.pin, bytes, version) } finally { bytes.fill(0) }
                        wipe()
                        mutable.value = state.value.copy(action = null, newCard = false, biometric = false, message = "Migración completada. Las passkeys conservan sus claves; ahora las protege el nuevo DNI.")
                    }
                    "enroll" -> {
                        val snapshot = maintenance.open(detected, s.pin, enrollment = true)
                        pending = snapshot.bytes; revision = snapshot.revision
                        armExpiry()
                        val cipher = withContext(Dispatchers.IO) { device.enrollmentCipher() }
                        mutable.value = state.value.copy(cipher = cipher, message = "Confirma tu huella para habilitar este teléfono.")
                        pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "biometric_enrollment_prompt", "flow" to diagnosticFlow)
                    }
                }
                ensureActive()
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "maintenance_card_completed", "stage" to s.action,
                    "state" to if (state.value.action == null) "committed" else "awaiting_next_step",
                    "durationMs" to android.os.SystemClock.elapsedRealtime() - started, "flow" to diagnosticFlow)
            } catch (e: CancellationException) {
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "maintenance_cancelled", "stage" to s.action, "flow" to diagnosticFlow)
                throw e
            }
            catch (e: Exception) {
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.failure("passkeys", "maintenance", e)
                wipe(); mutable.value = state.value.copy(action = null, cipher = null, newCard = false,
                    message = if (e.message?.startsWith("No hay passkeys exportables.") == true) requireNotNull(e.message) else "No se completó la operación. Revisa el DNI, certificado y respaldo. No se reintentó el PIN.")
            } finally { detected.close(); if (card === detected) { card = null; mutable.value = state.value.copy(busy = false, previous = maintenance.hasPrevious()) } }
        }
    }
    fun biometric(cipher: Cipher) = work {
        require(cipher === state.value.cipher)
        val bytes = requireNotNull(pending).copyOf()
        val version = requireNotNull(revision).copyOf()
        try { withContext(Dispatchers.IO) { PasskeyCardAccess.serialized {
            require(java.security.MessageDigest.isEqual(version, repository.revision()))
            currentCoroutineContext().ensureActive()
            device.enroll(cipher, bytes, version)
        } } } finally { bytes.fill(0) }
        wipe(); mutable.value = state.value.copy(cipher = null, action = null, biometric = true)
        pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "biometric_enrollment_completed", "flow" to diagnosticFlow)
        "Teléfono habilitado. Cada uso requiere huella; crear, restaurar o migrar sigue requiriendo DNI."
    }
    fun biometricCancelled() {
        pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "biometric_enrollment_cancelled", "flow" to diagnosticFlow)
        wipe(); mutable.value = state.value.copy(cipher = null, action = null, message = "Habilitación cancelada.")
    }
    private fun armExpiry() { expiry?.cancel(); expiry = viewModelScope.launch { delay(120_000); cancel(); mutable.value = state.value.copy(message = "La autorización caducó. Vuelve a comenzar.") } }
    private fun wipe() { expiry?.cancel(); expiry = null; pending?.fill(0); pending = null; revision = null }
    fun cancel() { job?.cancel(); card?.close(); card = null; wipe(); mutable.value = state.value.copy(pin = "", action = null, awaiting = false, busy = false, cipher = null, newCard = false) }
    private fun work(action: suspend () -> String) {
        if (state.value.busy) return
        mutable.value = state.value.copy(busy = true)
        job = viewModelScope.launch {
            try { val message = action(); ensureActive(); mutable.value = state.value.copy(message = message) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { pe.gob.reniec.dnie.diagnostics.DiagnosticLog.failure("passkeys", "maintenance_io", e); wipe(); mutable.value = state.value.copy(cipher = null, message = "No se pudo completar la operación.") }
            finally { mutable.value = state.value.copy(busy = false) }
        }
    }
    override fun onCleared() { cancel(); super.onCleared() }
}
