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
/** Coordinates import and exclusive foreground sessions; secrets never enter Compose state. */
class BridgeViewModel(application: Application) : AndroidViewModel(application) {
    private val trustedPc = pe.gob.reniec.dnie.data.bridge.TrustedPcStorage(application)
    private val preferences = pe.gob.reniec.dnie.data.bridge.BridgePreferences(application)
    private val mutable = MutableStateFlow(BridgeUiState(can = CanStorage.getSavedCan(application),
        method = preferences.method(), settings = preferences.settings(),
        tailnetHost = preferences.host().ifEmpty { pe.gob.reniec.dnie.development.defaultTailnetHost() }))
    val state = mutable.asStateFlow()
    private var pairing: BridgePairing? = null
    private val adapter = BridgeAndroidAdapter(application)
    init { mutable.update { it.copy(traceAvailable = adapter.hasTrace(), localWifiAddress = runCatching { pe.gob.reniec.dnie.data.bridge.BridgeTransportAdapter.localAddresses().joinToString(", ") }.getOrDefault("")) } }
    private var task: Job? = null
    @Volatile private var awaiting: BridgeAndroidAdapter.Awaiting? = null
    @Volatile private var connection: BridgeConnection? = null
    @Volatile private var generation = 0
    init {
        viewModelScope.launch {
            val initialGeneration = generation
            val saved = withContext(Dispatchers.IO) { runCatching { trustedPc.load(mutable.value.method) }.getOrNull() }
            if (saved != null) {
                if (generation == initialGeneration && pairing == null && !mutable.value.busy && mutable.value.method.accepts(saved.transport)) {
                    pairing = saved
                    mutable.update { it.copy(peer = "${saved.host}:${saved.port}", fingerprint = saved.fingerprint(), message = "PC recordada. Autoriza el puente para conectar el DNI.") }
                } else saved.close()
            }
        }
    }
    fun forgetPc() {
        if (mutable.value.busy || mutable.value.armed) return
        ++generation; pairing?.close(); pairing = null
        viewModelScope.launch { withContext(Dispatchers.IO) {trustedPc.forget()} }
        mutable.update {it.copy(peer="",fingerprint="",message="PC olvidada. Escanea su QR para volver a autorizarla.")}
    }
    private fun savePreferences() { mutable.value.let { preferences.save(it.method, it.tailnetHost, it.settings) } }
    fun settings(value: pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings) {
        if (mutable.value.busy || mutable.value.armed) return
        mutable.update { it.copy(settings = value) }; savePreferences()
    }
    fun method(value: pe.gob.reniec.dnie.bridge.BridgeConnectionMethod) {
        if (mutable.value.busy || mutable.value.armed || value == mutable.value.method) return
        ++generation; task?.cancel(); task = null
        pairing?.close(); pairing = null
        mutable.update { it.copy(method = value, peer = "", fingerprint = "", tailscale = false,
            message = if (value.usesPairing) "En la PC selecciona la red correspondiente, inicia el host y escanea su QR." else "Activa Tailscale en ambos equipos y conecta con el nombre de la PC.") }
        savePreferences()
        val epoch=generation
        mutable.update {it.copy(busy=true)}
        task=viewModelScope.launch {
            var saved: BridgePairing?=null
            try {
                saved=withContext(Dispatchers.IO) {runCatching {trustedPc.load(value)}.getOrNull()}
                if(epoch==generation) {
                    pairing=saved;saved=null
                    val remembered=pairing
                    mutable.update {it.copy(busy=false, peer=remembered?.let {pc -> "${pc.host}:${pc.port}"} ?: "",
                        fingerprint=remembered?.fingerprint() ?: "", message=if(remembered!=null) "PC recordada para este transporte. Autoriza el puente." else it.message)}
                }
            } finally {saved?.close()}
        }
    }
    fun translatePace(value: Boolean) { if (pe.gob.reniec.dnie.BuildConfig.DEBUG && !mutable.value.busy && !mutable.value.armed && !mutable.value.windowsPace) mutable.update { it.copy(mode = if (value) BridgeMode.TRANSLATED_ANDROID_PACE else BridgeMode.LOGICAL_ANDROID_PACE) } }
    fun windowsPace(value: Boolean) { if (pe.gob.reniec.dnie.BuildConfig.DEBUG && !mutable.value.busy && !mutable.value.armed) mutable.update { it.copy(mode = if (value) BridgeMode.DIRECT_WINDOWS_PACE else BridgeMode.TRANSLATED_ANDROID_PACE) } }
    fun can(value: String) { if (!mutable.value.busy) mutable.update { it.copy(can = value.filter(Char::isDigit).take(6)) } }
    fun tailnetHost(value: String) { if (!mutable.value.busy && !mutable.value.armed) { mutable.update { it.copy(tailnetHost = value.trim().take(253)) }; savePreferences() } }
    fun connectTailnet() {
        if (mutable.value.busy || mutable.value.armed || mutable.value.method.usesPairing) return
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
                    val transport = checkNotNull(imported).transport
                    val method = when {
                        transport.startsWith("tailscale_") -> pe.gob.reniec.dnie.bridge.BridgeConnectionMethod.TAILSCALE
                        transport == "usb_accessory" -> pe.gob.reniec.dnie.bridge.BridgeConnectionMethod.USB_NETWORK
                        else -> pe.gob.reniec.dnie.bridge.BridgeConnectionMethod.LOCAL_NETWORK
                    }
                    mutable.update { it.copy(method = method) }
                }
                if (epoch != generation) return@launch
                pairing = imported
                imported = null
                val p = checkNotNull(pairing)
                savePreferences()
                DiagnosticLog.event("bridge", "pairing_imported")
                mutable.update { it.copy(peer = "${p.host}:${p.port}",
                    fingerprint = p.fingerprint(), busy = false, tailscale = p.transport.startsWith("tailscale_"),
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
        val current = mutable.value
        if (current.busy || current.armed || (!current.windowsPace && current.can.length != 6)) return
        if (System.currentTimeMillis() >= p.expiresAtEpochMillis) {
            mutable.update { it.copy(message = "Emparejamiento vencido. Genera otro en la PC.") }; return
        }
        val epoch = ++generation
        mutable.update { it.copy(busy = true, armed = false, sessionStage = BridgeSessionStage.PREPARING, message = "Preparando conexión…") }
        task = viewModelScope.launch {
            var opened = false
            var session: BridgeConnection? = null
            try {
                val prepared = withContext(Dispatchers.IO) {
                    adapter.arm(p)
                    trustedPc.save(p)
                    adapter.awaiting(p, current.mode, current.settings)
                }
                if (epoch != generation) { prepared.connection.close(); return@launch }
                awaiting = prepared
                session = prepared.connection; connection = session
                withContext(Dispatchers.IO) {
                    prepared.connection.runAwaitingCard(current.can, current.mode, prepared.awaitAtr,
                        onEvent = { event -> DiagnosticLog.event("bridge", event) }) { phase ->
                        if (phase == BridgeConnection.Phase.PACE_ACTIVE || phase == BridgeConnection.Phase.RAW_ACTIVE) opened = true
                        if (epoch == generation) mutable.update { it.copy(
                            armed = phase == BridgeConnection.Phase.WAITING_CARD,
                            busy = phase != BridgeConnection.Phase.WAITING_CARD,
                            sessionStage = when (phase) {
                                BridgeConnection.Phase.AUTHENTICATING_PC -> BridgeSessionStage.CONNECTING_PC
                                BridgeConnection.Phase.WAITING_CARD -> BridgeSessionStage.WAITING_CARD
                                BridgeConnection.Phase.OPENING_PACE -> BridgeSessionStage.OPENING_CARD
                                BridgeConnection.Phase.PACE_ACTIVE, BridgeConnection.Phase.RAW_ACTIVE -> BridgeSessionStage.ACTIVE
                            }, message = when (phase) {
                                BridgeConnection.Phase.AUTHENTICATING_PC -> "Conectando con la PC…"
                                BridgeConnection.Phase.WAITING_CARD -> "PC conectada. Acerca tu DNI."
                                BridgeConnection.Phase.OPENING_PACE -> "Abriendo tu DNI…"
                                else -> "Puente activo. Mantén el DNI junto al teléfono."
                            }) }
                    }
                }
                DiagnosticLog.event("bridge", "session_closed", "state" to "peer_closed")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                DiagnosticLog.failure("bridge", "session", failure)
                if (epoch == generation) mutable.update { it.copy(message = "Conexión cerrada. Comprueba la PC y vuelve a iniciar el puente.") }
            } finally {
                session?.close()
                if (connection === session) connection = null
                if (epoch == generation) {
                    awaiting = null
                    withContext(Dispatchers.IO) { adapter.closeTransport() }
                    mutable.update { it.copy(busy = false, armed = false, traceAvailable = adapter.hasTrace(),
                        sessionStage = if (opened) BridgeSessionStage.PREPARING else BridgeSessionStage.IDLE) }
                    // A fresh authenticated connection/card owner; no APDU is replayed.
                    if (opened) authorize()
                }
            }
        }
    }
    fun tag(tag: Tag) {
        if (!mutable.value.armed || mutable.value.busy) return
        val prepared = awaiting ?: return
        mutable.update { it.copy(armed = false, busy = true, sessionStage = BridgeSessionStage.OPENING_CARD) }
        prepared.offer(tag)
    }
    fun stop() {
        DiagnosticLog.event("bridge", "stop_requested", "state" to if (connection == null) "idle" else "active")
        val epoch = ++generation
        awaiting = null
        val active = connection
        val previous = task
        previous?.cancel(); task = null
        mutable.update { it.copy(armed = false, busy = true, sessionStage = if(it.sessionStage.visible) BridgeSessionStage.STOPPING else BridgeSessionStage.IDLE, message = "Cerrando sesión…") }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { adapter.closeTransport(); active?.close() }
            previous?.join()
            if (connection === active) connection = null
            if (epoch == generation) mutable.update { it.copy(busy = false, sessionStage = BridgeSessionStage.IDLE, traceAvailable = adapter.hasTrace(), message = "Sesión detenida. Autoriza de nuevo para conectar.") }
        }
    }
    override fun onCleared() {
        ++generation
        connection?.let { active -> Thread({ active.close() }, "bridge-close").start() }
        connection = null
        adapter.closeTransport()
        pairing?.close(); pairing = null
        super.onCleared()
    }
}
