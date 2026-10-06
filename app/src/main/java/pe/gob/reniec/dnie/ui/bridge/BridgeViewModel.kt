package pe.gob.reniec.dnie.ui.bridge
import android.app.Application
import android.net.Uri
import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pe.gob.reniec.dnie.bridge.android.BridgeConnection
import pe.gob.reniec.dnie.bridge.android.BridgePairing
import pe.gob.reniec.dnie.bridge.BridgeMode
import pe.gob.reniec.dnie.data.bridge.BridgeAndroidAdapter
import pe.gob.reniec.dnie.data.preferences.CanStorage
import pe.gob.reniec.dnie.diagnostics.DiagnosticLog
data class BridgeUiState(
    val peer: String = "", val fingerprint: String = "", val can: String = "",
    val armed: Boolean = false, val busy: Boolean = false,
    val traceAvailable: Boolean = false, val mode: BridgeMode = BridgeMode.TRANSLATED_ANDROID_PACE,
    val tailscale: Boolean = false,
    val tailnetHost: String = "sebas-laptop.tail2752e9.ts.net",
    val message: String = "Escanea el QR mostrado en la PC o importa el emparejamiento."
) {
    val windowsPace get() = mode.windowsPace
    val translatePace get() = mode.translatePace
}
/** Coordinates import and exclusive foreground sessions; secrets never enter Compose state. */
class BridgeViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(BridgeUiState(can = CanStorage.getSavedCan(application)))
    val state = mutable.asStateFlow()
    private var pairing: BridgePairing? = null
    private val adapter = BridgeAndroidAdapter(application)
    init { mutable.update { it.copy(traceAvailable = adapter.hasTrace()) } }
    private var task: Job? = null
    @Volatile private var connection: BridgeConnection? = null
    @Volatile private var generation = 0
    fun translatePace(value: Boolean) { if (!mutable.value.busy && !mutable.value.armed && !mutable.value.windowsPace) mutable.update { it.copy(mode = if (value) BridgeMode.TRANSLATED_ANDROID_PACE else BridgeMode.LOGICAL_ANDROID_PACE) } }
    fun windowsPace(value: Boolean) { if (!mutable.value.busy && !mutable.value.armed) mutable.update { it.copy(mode = if (value) BridgeMode.DIRECT_WINDOWS_PACE else BridgeMode.TRANSLATED_ANDROID_PACE) } }
    fun can(value: String) { if (!mutable.value.busy) mutable.update { it.copy(can = value.filter(Char::isDigit).take(6)) } }
    fun tailnetHost(value: String) { if (!mutable.value.busy && !mutable.value.armed) mutable.update { it.copy(tailnetHost = value.trim().take(253)) } }
    fun connectTailnet() {
        if (mutable.value.busy || mutable.value.armed) return
        val next = try { adapter.tailnet(mutable.value.tailnetHost) } catch (_: Exception) {
            mutable.update { it.copy(message = "Introduce el nombre Tailscale de la PC o su IP 100.x.") }; return
        }
        pairing?.close(); pairing = next
        ++generation
        mutable.update { it.copy(peer = "${next.host}:${next.port}",fingerprint = "",tailscale = true,
            mode = BridgeMode.TRANSLATED_ANDROID_PACE, message = "Tailscale listo. Introduce el CAN y autoriza acercando el DNI.") }
        if (mutable.value.can.length == 6) authorize()
    }
    fun exportTrace(uri: Uri) = viewModelScope.launch {
        try { withContext(Dispatchers.IO) { adapter.exportTrace(uri) }; mutable.update { it.copy(message = "Registro APDU exportado.") } }
        catch (_: Exception) { mutable.update { it.copy(message = "No se pudo exportar el registro APDU.") } }
    }
    fun clearTrace() {
        if (mutable.value.busy || mutable.value.armed) return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { adapter.clearTrace() }; mutable.update { it.copy(traceAvailable = false, message = "Registro APDU borrado.") } }
            catch (_: Exception) { mutable.update { it.copy(message = "No se pudo borrar el registro APDU.") } }
        }
    }
    fun import(uri: Uri) = importPairing { adapter.import(uri) }
    fun importQr(text: String) = importPairing { adapter.importQr(text) }
    fun scanStatus(message: String) {
        if (!mutable.value.armed) mutable.update { it.copy(message = message) }
    }
    private fun importPairing(read: () -> BridgePairing) {
        if (mutable.value.busy) return
        pairing?.close(); pairing = null
        mutable.update { it.copy(peer = "", fingerprint = "", armed = false, busy = true, message = "Importando emparejamiento…") }
        val epoch = ++generation
        task = viewModelScope.launch {
            var imported: BridgePairing? = null
            try {
                withContext(Dispatchers.IO) {
                    imported = read()
                }
                if (epoch != generation) return@launch
                pairing = imported
                imported = null
                val p = checkNotNull(pairing)
                DiagnosticLog.event("bridge", "pairing_imported")
                mutable.update { it.copy(peer = "${p.host}:${p.port}",
                    fingerprint = p.fingerprint(), busy = false, tailscale = p.transport == "tailscale_tcp",
                    message = if (p.transport == "tailscale_tcp") "Tailscale directo listo. Activa Tailscale y autoriza el DNI." else "Comprueba esta huella con la PC antes de autorizar.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                DiagnosticLog.event("bridge", "pairing_import_failed")
                if (epoch == generation) mutable.update { it.copy(busy = false, message = "Emparejamiento inválido, vencido o inaccesible. Genera otro QR en la PC.") }
            } finally { imported?.close() }
        }
    }
    fun authorize() {
        val p = pairing ?: return
        if (mutable.value.busy || (!mutable.value.windowsPace && mutable.value.can.length != 6)) return
        if (System.currentTimeMillis() >= p.expiresAtEpochMillis) {
            mutable.update { it.copy(message = "Emparejamiento vencido. Genera otro en la PC.") }; return
        }
        mutable.update { it.copy(armed = true, message = if (it.windowsPace) "Acerca el DNI. Windows gestionará PACE; Android retransmitirá las APDU por TLS." else "Acerca el DNI y mantenlo apoyado. PACE se ejecutará en el teléfono; la PC podrá enviar instrucciones al DNI.") }
        DiagnosticLog.event("bridge", "nfc_authorized")
    }
    fun tag(tag: Tag) {
        val current = mutable.value
        val p = pairing ?: return
        if (!current.armed || current.busy) return
        val prepared = adapter.prepare(p, tag, current.mode)
        if (prepared == null) {
            mutable.update { it.copy(message = "Esta conexión NFC no ofrece los datos ISO 14443-A necesarios.") }; return
        }
        val session = prepared.connection
        connection = session
        mutable.update { it.copy(armed = false, busy = true, message = "Conectando con la PC…") }
        val epoch = ++generation
        val atr = prepared.atr
        task = viewModelScope.launch {
            var opened = false
            try {
                withContext(Dispatchers.IO) {
                    session.run(current.can, atr, mode = current.mode, onEvent = { event ->
                        DiagnosticLog.event("bridge", event)
                    }) { phase ->
                        if (phase == BridgeConnection.Phase.PACE_ACTIVE || phase == BridgeConnection.Phase.RAW_ACTIVE) opened = true
                        if (epoch == generation) mutable.update { it.copy(message = when (phase) {
                            BridgeConnection.Phase.AUTHENTICATING_PC -> if (current.tailscale) "Sincronizando con la PC por Tailscale…" else "Validando la PC por TLS…"
                            BridgeConnection.Phase.OPENING_PACE -> "Abriendo PACE con tu DNI…"
                            BridgeConnection.Phase.RAW_ACTIVE -> "Puente directo activo. Windows gestiona PACE; registro APDU activo."
                            BridgeConnection.Phase.PACE_ACTIVE -> if (current.translatePace) "Puente con traducción PACE activo. Sesiones PC y DNI independientes." else "Puente PACE activo. Instrucciones de la PC habilitadas; registro APDU activo."
                        }) }
                    }
                }
                if (epoch == generation) mutable.update { it.copy(message = "La PC cerró la sesión.") }
                DiagnosticLog.event("bridge", "session_closed", "state" to "peer_closed")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                DiagnosticLog.failure("bridge", "session", failure)
                if (!opened) withContext(Dispatchers.IO) { adapter.recordOpeningFailure(failure, current.windowsPace, current.translatePace) }
                DiagnosticLog.event("bridge", "session_closed", "state" to "transport_or_card_failed")
                if (epoch == generation) mutable.update { it.copy(message = "La conexión se cerró. Revisa el registro, el host de la PC y el contacto NFC.") }
            } finally {
                atr.fill(0)
                if (connection === session) connection = null
                if (epoch == generation) mutable.update { it.copy(busy = false, traceAvailable = adapter.hasTrace()) }
            }
        }
    }
    fun stop() {
        DiagnosticLog.event("bridge", "stop_requested", "state" to if (connection == null) "idle" else "active")
        val epoch = ++generation
        val active = connection
        val previous = task
        previous?.cancel(); task = null
        mutable.update { it.copy(armed = false, busy = true, message = "Cerrando sesión…") }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { active?.close() }
            previous?.join()
            if (connection === active) connection = null
            if (epoch == generation) mutable.update { it.copy(busy = false, traceAvailable = adapter.hasTrace(), message = "Sesión detenida. Autoriza de nuevo para conectar.") }
        }
    }
    override fun onCleared() {
        ++generation
        connection?.let { active -> Thread({ active.close() }, "bridge-close").start() }
        connection = null
        pairing?.close(); pairing = null
        super.onCleared()
    }
}
