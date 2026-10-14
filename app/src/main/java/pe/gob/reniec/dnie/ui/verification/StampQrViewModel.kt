package pe.gob.reniec.dnie.ui.verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.gob.reniec.dnie.crypto.X509CertificateParser
import pe.gob.reniec.dnie.data.qr.StampQrRepository
import pe.gob.reniec.dnie.domain.model.CertificateInfo
import pe.gob.reniec.dnie.pdf.*

data class StampQrUiState(val record: StampQrRecord? = null, val original: StampQrMatch? = null,
    val certificateMatch: StampQrMatch? = null, val certificate: CertificateInfo? = null,
    val busy: Boolean = false, val message: String? = null, val identityMatches: Boolean? = null)

/** Cancels stale comparisons whenever a different QR is scanned; never merges evidence across QR. */
class StampQrViewModel(private val repository: StampQrRepository): ViewModel() {
    private val mutable=MutableStateFlow(StampQrUiState())
    val state=mutable.asStateFlow()
    private var job: Job?=null
    private var generation=0
    fun clear() { generation++;job?.cancel();mutable.value=StampQrUiState() }
    fun scan(payload: String) {
        clear()
        mutable.value=try { StampQrUiState(record=StampQrCodec.decode(payload)) }
        catch(_: Exception) { StampQrUiState(message="QR de sello no compatible o dañado. No se abrirá ningún enlace.") }
    }
    fun status(message: String) { mutable.value=mutable.value.copy(message=message) }
    fun original(uri: String) = compare({ it.copy(original=null) }) {
        val digest=repository.original(uri)
        mutable.value.copy(original=StampQrCodec.compareOriginal(requireNotNull(mutable.value.record),digest))
    }
    fun certificate(uri: String) = compare({ it.copy(certificate=null,certificateMatch=null,identityMatches=null) }) {
        val cert=repository.certificate(uri)
        val record=requireNotNull(mutable.value.record)
        val match=StampQrCodec.compareCertificate(record,X509CertificateParser.toX509Certificate(cert.rawDerBytes))
        mutable.value.copy(certificate=cert,certificateMatch=match,identityMatches=
            record.name==cert.citizenName.trim() && (record.dni==null || record.dni==cert.citizenDni))
    }
    private fun compare(reset: (StampQrUiState) -> StampQrUiState, operation: suspend () -> StampQrUiState) {
        if(mutable.value.record==null || mutable.value.busy || mutable.value.record?.metadata?.preview==true) return
        job?.cancel();val current=++generation
        mutable.value=reset(mutable.value).copy(busy=true,message=null)
        job=viewModelScope.launch {
            try {
                val next=operation()
                if(current==generation) mutable.value=next.copy(busy=false)
            } catch(cancel: CancellationException) { throw cancel }
            catch(_: Exception) { if(current==generation) mutable.value=mutable.value.copy(busy=false,
                message="No se pudo leer el archivo. Original máximo: 256 MiB; certificado CER o PEM: 128 KiB.") }
        }
    }
}
