package pe.gob.reniec.dnie.ui.suite

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
    val count: Int = 0, val busy: Boolean = false, val armed: Boolean = false,
    val message: String = "Importa la solicitud de la suite PC. El puente para ReFirma sigue siendo independiente.")

/** Owns one foreground job. Socket/card/PIN never enter Compose state or another ViewModel. */
class RemoteSigningViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(RemoteSigningState(can = CanStorage.getSavedCan(application)))
    val state = mutable.asStateFlow()
    private val adapter = pe.gob.reniec.dnie.data.suite.RemoteSigningAndroidAdapter(application)
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
        val next = runCatching { SigningProvision.parse(text) }.getOrNull()
        if (next == null) { status("Solicitud inválida o vencida. Genera otra desde la suite PC."); return }
        ++epoch
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
        val link = PhoneSigningTransport(pair)
        transport = link
        mutable.update { it.copy(busy = true, message = "Esperando y autenticando la suite PC por TLS…") }
        task = viewModelScope.launch {
            try {
                val next = withContext(Dispatchers.IO) { link.accept() }
                if (generation != epoch) { next.close(); return@launch }
                channel = next
                mutable.update { it.copy(busy = false, armed = true, message = "PC autenticada. Acerca y mantén tu DNI hasta completar el lote.") }
                DiagnosticLog.event("suite", "peer_authorized", "count" to pair.count)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == epoch) { release(); mutable.update { it.copy(busy = false, armed = false, message = "No se conectó la suite. Revisa las IP y genera una solicitud nueva.") } } }
        }
    }
    fun tag(tag: Tag) {
        if (!mutable.value.armed || mutable.value.busy) return
        val pair = provision ?: return
        val stream = channel ?: return
        val pin = credentials ?: return
        if (System.currentTimeMillis() >= pair.expiresAt) { stop(); status("Solicitud vencida. Genera otra en la PC."); return }
        val card = adapter.card(tag, mutable.value.can) ?: return
        val endpoint = AuthorizedSigningEndpoint(pair.sessionId, card, pin, pair.count)
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
        val generation = epoch
        mutable.update { it.copy(armed = false, busy = true, message = "Abriendo PACE y firmando el lote. Mantén el DNI junto al teléfono.") }
        task = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { session.run() }
                if (generation == epoch) mutable.update { it.copy(message = rejected ?: "Sesión finalizada. Comprueba los resultados en la suite PC.") }
                DiagnosticLog.event("suite", "session_ended")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == epoch) mutable.update { it.copy(message = "Sesión interrumpida. Conserva los resultados de PC; no se repite ninguna firma.") } }
            finally { session.close(); if (generation == epoch) { release(); mutable.update { it.copy(busy = false, armed = false, pc = "", fingerprint = "", count = 0) } } }
        }
    }
    private fun release() {
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
        mutable.update { it.copy(busy = false, armed = false, pc = "", fingerprint = "", count = 0,
            message = "Sesión detenida. Importa una nueva solicitud para volver a firmar.") }
    }
    override fun onCleared() { stop(); super.onCleared() }
}
