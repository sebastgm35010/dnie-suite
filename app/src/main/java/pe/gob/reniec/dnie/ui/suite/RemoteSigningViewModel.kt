package pe.gob.reniec.dnie.ui.suite

import pe.gob.reniec.dnie.domain.feedback.*
import android.app.Application
import android.net.Uri
import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pe.gob.reniec.dnie.data.preferences.CanStorage
import pe.gob.reniec.dnie.diagnostics.DiagnosticLog
import pe.gob.reniec.dnie.signing.link.*

data class RemoteSigningState(val can: String = "", val pc: String = "", val fingerprint: String = "",
    val cardState: SuiteCardState = SuiteCardState.ABSENT, val count: Int = 0, val continuous: Boolean = false, val connected: Boolean = false, val remembered: Boolean = false, val busy: Boolean = false, val armed: Boolean = false,
    val sessionVisible: Boolean = false,
    val failureCode: String = "",
    val message: String = "Importa la solicitud de la suite PC. El puente para ReFirma sigue siendo independiente.")

/** Owns one foreground job. Socket/card/PIN never enter Compose state or another ViewModel. */
class RemoteSigningViewModel(application: Application) : AndroidViewModel(application) {
    private val feedbackEvents=ReadingFeedbackEvents()
    val feedback=feedbackEvents.flow

    private val mutable = MutableStateFlow(RemoteSigningState(can = CanStorage.getSavedCan(application)))
    val state = mutable.asStateFlow()
    private val adapter = pe.gob.reniec.dnie.data.suite.RemoteSigningAndroidAdapter(application)
    private val trust = pe.gob.reniec.dnie.data.suite.SuiteTrustStorage(application)
    private var pairing: SuitePairing? = null
    @Volatile private var continuousConnection: ContinuousSigningConnection? = null
    private var presenceTask: Job? = null
    private var cardStateTask: Job? = null
    @Volatile private var slot: SuiteCardSlot? = null
    init {
        viewModelScope.launch {
            val generation = epoch
            val saved = withContext(Dispatchers.IO) { runCatching { trust.load() }.getOrNull() }
            if (generation != epoch) saved?.close()
            else if (saved != null) { pairing = saved; showPair(saved, true) }
        }
    }
    private fun showPair(pair: SuitePairing, remembered: Boolean) {
        mutable.update { it.copy(pc = pair.pc, fingerprint = pair.fingerprintText(), continuous = true,
            remembered = remembered, count = 0, message = "PC permanente. Conectar habilita solicitudes privadas mientras esta pantalla permanezca abierta. El PIN se pide en Windows por lote.") }
    }
    fun connectRemembered() {
        val pair = pairing ?: return
        if (mutable.value.busy || mutable.value.armed || mutable.value.can.length != 6) return
        val generation = ++epoch
        val cardSlot = SuiteCardSlot(); slot = cardSlot
        val request = pair.provision(); provision = request
        val link = PhoneSigningTransport(request, continuous = true, presenceFactory = { p ->
            pe.gob.reniec.dnie.data.network.AndroidNetworkPresence(getApplication()).offer(pe.gob.reniec.dnie.network.DiscoveryService.SUITE,p.fingerprint,p.token)
        }); transport = link
        mutable.update { it.copy(busy = true, connected = false, sessionVisible = true, failureCode = "", message = "Esperando PC y autenticando Suite continua…") }
        DiagnosticLog.event("suite", "connection_requested")
        task = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { trust.save(pair) }
                val stream = withContext(Dispatchers.IO) { link.accept(onPeerAddress = { address ->
                    if (generation == epoch) mutable.update { it.copy(pc = address) }
                }) { event -> DiagnosticLog.event("suite", event) } }
                if (generation != epoch) { stream.close(); return@launch }
                channel = stream
                mutable.update { it.copy(busy = false, connected = true, armed = true, remembered = true,
                    message = "Suite conectada. Puedes acercar el DNI; envía archivos y PIN desde Windows.") }
                cardStateTask = viewModelScope.launch {
                    cardSlot.state.collect { state -> if (generation == epoch) {
                        val previous=mutable.value.cardState
                        if(state==SuiteCardState.READY && previous!=state) feedbackEvents.report(ReadingCue.PROGRESS)
                        if(state==SuiteCardState.FAILED && previous!=state) feedbackEvents.report(ReadingCue.ERROR)
                        mutable.update { it.copy(cardState = state) }
                        DiagnosticLog.event("suite", "card_state", "state" to state.name)
                    } }
                }
                presenceTask = viewModelScope.launch {
                    while (isActive && generation == epoch) {
                        withContext(Dispatchers.IO) { cardSlot.pollPresence() }
                        delay(750)
                    }
                }
                var jobSigned=0;var jobExpected=0
                var jobFailed = false
                val session = ContinuousSigningConnection(stream, cardSlot::acquire, cardSlot::invalidate,
                    onJob = { count -> jobSigned=0;jobExpected=count;jobFailed = false; DiagnosticLog.event("suite", "job_requested", "count" to count); if (generation == epoch) mutable.update { it.copy(count = count, busy = true, message = "Windows solicitó $count firma(s). Si no hay DNI conectado, acércalo; luego mantenlo junto al teléfono.") } },
                    onStatus = { status -> if (status != SigningStatus.OK) jobFailed = true; if (generation == epoch && status != SigningStatus.OK) mutable.update { it.copy(message = "El lote fue rechazado o interrumpido. No se reintenta. Revisa Windows y vuelve a presentar el DNI.") } },
                    onIdle = { if(generation==epoch && jobExpected>0) {feedbackEvents.report(if(jobFailed) ReadingCue.ERROR else ReadingOutcome.batch(jobSigned,jobExpected));jobExpected=0}; if (generation == epoch) mutable.update { it.copy(busy = false, count = 0, message = if (jobFailed) it.message else "Lote terminado. Suite permanece conectada para la siguiente solicitud desde Windows.") } }, finishJob = cardSlot::finishJob,
                    onOperation = { operation, result ->
                        if(generation==epoch && operation==SigningOperation.SIGN_SHA256 && result==SigningStatus.OK) {jobSigned++;if(jobExpected>1)feedbackEvents.report(ReadingCue.PROGRESS)}
                        DiagnosticLog.event("suite", "operation_result", "operation" to operation.name, "status" to result.name)
                        if (generation == epoch && result != SigningStatus.OK) mutable.update { it.copy(message = when {
                            result == SigningStatus.INVALID_PIN -> "PIN rechazado. Lote detenido sin reintentar; Suite permanece conectada."
                            result == SigningStatus.BLOCKED -> "El DNI informa PIN bloqueado. No se realizan más intentos."
                            operation == SigningOperation.OPEN -> "Falló la apertura NFC/PACE. Retira y acerca el DNI; revisa el lote incompleto en Windows."
                            operation == SigningOperation.SIGN_SHA256 -> "Respuesta de firma fallida. Conserva los resultados de Windows; no se repite la operación."
                            else -> "La sesión del DNI falló en ${operation.name}. Suite permanece conectada; revisa Windows antes de enviar otro lote."
                        }) }
                    },
                    onUnavailable = { if (generation == epoch) mutable.update { it.copy(message = "No se presentó un DNI conectado a tiempo. No se inició ninguna firma y Suite sigue conectada.") } })
                continuousConnection = session
                withContext(Dispatchers.IO) { session.run() }
            } catch (_: TimeoutCancellationException) {
                if (generation == epoch) mutable.update { it.copy(message = "No se presentó el DNI a tiempo. Conecta de nuevo; la PC permanece recordada y no se repite el lote.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                DiagnosticLog.failure("suite", "connection", failure)
                if (generation == epoch) mutable.update { it.copy(failureCode = failure.javaClass.simpleName, message = "Conexión Suite cerrada. La PC permanece recordada; conecta de nuevo sin QR. No se repite ninguna firma.") } }
            finally {
                if (generation == epoch) { release(); mutable.update { it.copy(busy = false, armed = false, connected = false, count = 0) } }
            }
        }
    }
    fun forgetPc() {
        stop(); pairing?.close(); pairing = null
        viewModelScope.launch { withContext(Dispatchers.IO) { trust.forget() } }
        mutable.update { it.copy(pc = "", fingerprint = "", remembered = false, continuous = false, message = "PC olvidada. Escanea un nuevo emparejamiento para conectarte.") }
    }
    private var provision: SigningProvision? = null
    private var credentials: CharArray? = null
    private var task: Job? = null
    private var epoch = 0
    @Volatile private var transport: PhoneSigningTransport? = null
    @Volatile private var channel: SigningDuplex? = null
    @Volatile private var connection: AuthorizedSigningConnection? = null

    fun can(value: String) { if (!mutable.value.busy && !mutable.value.armed) mutable.update { it.copy(can = value.filter { c -> c in '0'..'9' }.take(6)) } }
    fun status(value: String) { if (!mutable.value.busy && !mutable.value.armed) mutable.update { it.copy(message = value) } }
    fun import(text: String) {
        if (mutable.value.busy || mutable.value.armed) return
        if (text.trim().startsWith("DNISUITE2|")) {
            val next = runCatching { SuitePairing.parse(text) }.getOrNull()
            if (next == null) { status("Emparejamiento inválido."); return }
            ++epoch; pairing?.close(); pairing = next; provision?.close(); provision = null
            showPair(next, false); return
        }
        val next = runCatching { SigningProvision.parse(text) }.getOrNull()
        if (next == null) { status("Solicitud inválida o vencida. Genera otra desde la suite PC."); return }
        ++epoch
        pairing?.close(); pairing = null
        mutable.update { it.copy(continuous = false, remembered = false, connected = false) }
        provision?.close(); provision = next
        mutable.update { it.copy(pc = next.pcAddress, fingerprint = next.fingerprintText(), count = next.count,
            message = "Compara la huella con la PC. Autorizar permite firmar hasta ${next.count} documento(s) de ese lote.") }
    }
    fun import(uri: Uri) {
        if (mutable.value.busy || mutable.value.armed) return
        val generation = epoch
        viewModelScope.launch {
            val payload = withContext(Dispatchers.IO) { runCatching {
                adapter.read(uri)
            }.getOrNull() }
            if (generation == epoch) { if (payload == null) status("No se pudo importar la solicitud.") else import(payload) }
        }
    }
    fun authorize(pin: String) {
        val pair = provision ?: return
        if (mutable.value.busy || mutable.value.armed || mutable.value.can.length != 6 ||
            pin.length !in 4..8 || pin.any { it !in '0'..'9' }) return
        if (System.currentTimeMillis() >= pair.expiresAt) { status("Solicitud vencida. Genera otra en la PC."); return }
        credentials?.fill('\u0000'); credentials = pin.toCharArray()
        val generation = ++epoch
        val link = PhoneSigningTransport(pair, presenceFactory = { p ->
            pe.gob.reniec.dnie.data.network.AndroidNetworkPresence(getApplication()).offer(pe.gob.reniec.dnie.network.DiscoveryService.SUITE,p.fingerprint,p.token)
        })
        transport = link
        mutable.update { it.copy(busy = true, sessionVisible = true, failureCode = "", message = "Esperando y autenticando la suite PC por TLS…") }
        task = viewModelScope.launch {
            try {
                val next = withContext(Dispatchers.IO) { link.accept(onPeerAddress = { address ->
                    if (generation == epoch) mutable.update { it.copy(pc = address) }
                }) { event -> DiagnosticLog.event("suite", event) } }
                if (generation != epoch) { next.close(); return@launch }
                channel = next
                mutable.update { it.copy(busy = false, armed = true, connected = true, message = "PC autenticada. Acerca y mantén tu DNI hasta completar el lote.") }
                DiagnosticLog.event("suite", "peer_authorized", "count" to pair.count)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                DiagnosticLog.failure("suite", "connection", failure)
                if (generation == epoch) { release(); mutable.update { it.copy(busy = false, armed = false, connected = false, failureCode = failure.javaClass.simpleName, message = "No se conectó la suite. Revisa las IP y genera una solicitud nueva.") } } }
        }
    }
    fun tag(tag: Tag) {
        if (mutable.value.continuous && mutable.value.connected) {
            val generation = epoch
            val current = slot ?: return
            val can = mutable.value.can
            viewModelScope.launch {
                val outcome = withContext(Dispatchers.IO) { runCatching { current.offer { adapter.continuousCard(tag, can) } } }
                if (generation == epoch && outcome.isFailure) {
                    DiagnosticLog.event("suite", "nfc_connect_failed")
                    mutable.update { it.copy(message = "No se pudo estabilizar la conexión NFC. Retira y vuelve a acercar el DNI; Suite sigue conectada.") }
                }
            }
            return
        }
        if (!mutable.value.armed || mutable.value.busy) return
        val pair = provision ?: return
        val stream = channel ?: return
        val pin = credentials ?: return
        if (System.currentTimeMillis() >= pair.expiresAt) { stop(); status("Solicitud vencida. Genera otra en la PC."); return }
        val card = adapter.card(tag, mutable.value.can) ?: return
        val generation = epoch
        var signedCount=0
        val observedCard=object: pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface by card {
            override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> = card.signSha256Hash(hash32Bytes).also {result->
                if(result.isSuccess && generation==epoch) {signedCount++;if(pair.count>1)feedbackEvents.report(ReadingCue.PROGRESS)}
            }
        }
        val endpoint = AuthorizedSigningEndpoint(pair.sessionId, observedCard, pin, pair.count)
        credentials?.fill('\u0000'); credentials = null
        var rejected: String? = null
        val session = AuthorizedSigningConnection(stream, endpoint) { status ->
            rejected = when (status) {
                SigningStatus.INVALID_PIN -> "PIN rechazado por el DNI. La sesión terminó sin reintentar."
                SigningStatus.BLOCKED -> "El DNI informa PIN bloqueado. No se realizaron más intentos."
                SigningStatus.FAILED -> "La operación falló y se cerró la sesión sin reintentar. Revisa los resultados de PC."
                else -> rejected
            }
        }
        connection = session
        mutable.update { it.copy(armed = false, busy = true, cardState = SuiteCardState.IN_USE, message = "Abriendo PACE y firmando el lote. Mantén el DNI junto al teléfono.") }
        task = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { session.run() }
                if(generation==epoch) feedbackEvents.report(if(rejected==null) ReadingOutcome.batch(signedCount,pair.count) else ReadingCue.ERROR)
                if (generation == epoch) mutable.update { it.copy(message = rejected ?: "Sesión finalizada. Comprueba los resultados en la suite PC.") }
                DiagnosticLog.event("suite", "session_ended")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if(generation==epoch) feedbackEvents.report(ReadingCue.ERROR); if (generation == epoch) mutable.update { it.copy(message = "Sesión interrumpida. Conserva los resultados de PC; no se repite ninguna firma.") } }
            finally { session.close(); if (generation == epoch) { release(); mutable.update { it.copy(busy = false, armed = false, connected = false, pc = "", fingerprint = "", count = 0) } } }
        }
    }
    private fun release() {
        presenceTask?.cancel(); presenceTask = null
        cardStateTask?.cancel(); cardStateTask = null
        mutable.update { it.copy(cardState = SuiteCardState.ABSENT) }
        continuousConnection?.close(); continuousConnection = null
        slot?.close(); slot = null
        transport?.close(); transport = null
        channel?.close(); channel = null
        connection?.close(); connection = null
        credentials?.fill('\u0000'); credentials = null
        provision?.close(); provision = null
    }
    fun stop() {
        ++epoch
        release() // Close socket/card first to unblock IO; then cancel coroutine.
        task?.cancel(); task = null
        mutable.update { it.copy(busy = false, armed = false, connected = false, sessionVisible = false, failureCode = "", pc = pairing?.pc ?: "",
            fingerprint = pairing?.fingerprintText() ?: "", count = 0,
            message = if (pairing != null) "Suite detenida. La PC queda recordada; conecta de nuevo sin QR." else "Sesión detenida. Importa una nueva solicitud para volver a firmar.") }
    }
    override fun onCleared() { stop(); pairing?.close(); pairing = null; super.onCleared() }
}
