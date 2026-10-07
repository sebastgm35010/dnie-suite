package pe.gob.reniec.dnie.ui.certificates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ensureActive
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.crypto.CertificateExporter
import pe.gob.reniec.dnie.crypto.X509CertificateParser
import pe.gob.reniec.dnie.domain.model.CertificateInfo
import pe.gob.reniec.dnie.domain.model.CertificateType
import java.io.OutputStream

/**
 * Estados de interfaz reactivos para el Inspector de Certificados.
 */
sealed interface CertificatesUiState {
    data object Idle : CertificatesUiState

    data class EnteringCan(
        val initialCan: String = ""
    ) : CertificatesUiState

    data class ReadingCard(
        val message: String = "Aproxime su DNIe al sensor NFC para leer los certificados...",
        val can: String? = null
    ) : CertificatesUiState

    data class Loaded(
        val certificates: Map<CertificateType, CertificateInfo>,
        val selectedType: CertificateType = CertificateType.SIGNATURE,
        val scanMessage: String? = null
    ) : CertificatesUiState

    data class Error(
        val message: String
    ) : CertificatesUiState
}

/**
 * ViewModel para la lectura e inspección de certificados X.509 públicos del DNIe.
 */
class CertificatesViewModel(private val closeDispatcher: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private var readingJob: Job? = null
    @Volatile private var activeReader: DnieTransceiverInterface? = null
    val busy: Boolean get() = readingJob?.isActive == true || activeReader != null

    private val _uiState = MutableStateFlow<CertificatesUiState>(CertificatesUiState.Idle)
    val uiState: StateFlow<CertificatesUiState> = _uiState.asStateFlow()

    data class IdentitySnapshot(val generation: Long=0,val document: pe.gob.reniec.dnie.icao.IcaoDocument?=null,val message: String?=null,val reading: Boolean=false)
    private var activeScan: pe.gob.reniec.dnie.domain.scan.UnifiedDniScan?=null
    private val _identitySnapshot=MutableStateFlow(IdentitySnapshot())
    val identitySnapshot=_identitySnapshot.asStateFlow()

    var enteredCan: String? = null
        private set

    /**
     * Solicita en pantalla el ingreso del código CAN (6 dígitos) antes de activar NFC.
     */
    fun requestCanEntry() {
        _uiState.value = CertificatesUiState.EnteringCan(initialCan = enteredCan ?: "")
    }

    /**
     * Cancela el ingreso del CAN y regresa al estado Idle.
     */
    fun cancelCanEntry() {
        if (_uiState.value is CertificatesUiState.EnteringCan) {
            _uiState.value = CertificatesUiState.Idle
        }
    }

    /**
     * Confirma el CAN ingresado y procede a la espera del DNIe en el lector NFC.
     */
    fun onCanConfirmed(can: String) {
        enteredCan = can
        _uiState.value = CertificatesUiState.ReadingCard(can = can)
    }

    /**
     * Inicia directamente la solicitud de aproximación de tarjeta para lectura.
     */
    fun startReading(can: String? = null) {
        if (can != null) {
            enteredCan = can
        }
        _uiState.value = CertificatesUiState.ReadingCard(can = enteredCan)
    }

    /**
     * Cancela la espera de tarjeta y regresa a estado Idle o mantiene certificados si ya estaban cargados.
     */
    fun cancelReading() {
        readingJob?.cancel()
        activeScan?.let { scan -> viewModelScope.launch(closeDispatcher) { runCatching { scan.close() } } }
        if (_uiState.value is CertificatesUiState.ReadingCard) {
            _uiState.value = CertificatesUiState.Idle
        }
    }

    /**
     * Procesa la tarjeta detectada a través del transceptor APDU.
     */
    fun onCardDetected(transceiver: DnieTransceiverInterface, identityFactory: (() -> pe.gob.reniec.dnie.icao.IcaoReader)? = null) {
        if (busy) {
            viewModelScope.launch(closeDispatcher) { runCatching { transceiver.close() } }
            return
        }
        activeReader = transceiver
        val inspection=pe.gob.reniec.dnie.domain.scan.DniePublicCertificateReader(transceiver)
        val scan=pe.gob.reniec.dnie.domain.scan.UnifiedDniScan(inspection);activeScan=scan
        if (transceiver is pe.gob.reniec.dnie.core.nfc.DnieTransceiver && transceiver.can.isNullOrBlank() && !enteredCan.isNullOrBlank()) {
            transceiver.can = enteredCan
        }

        _uiState.value = CertificatesUiState.ReadingCard("Conectando con Applet del DNIe...")

        readingJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var retained: pe.gob.reniec.dnie.domain.scan.CertificateSnapshot?=null
            try {
                val generation=_identitySnapshot.value.generation+1
                _identitySnapshot.value=IdentitySnapshot(generation)
                val result=withContext(closeDispatcher) { scan.read(
                    { inspection },identityFactory,
                    progress={ message -> _uiState.value=CertificatesUiState.ReadingCard(message) },
                    onCertificates={ retained=it },onIdentityProgress={ document ->
                        _identitySnapshot.value=IdentitySnapshot(generation,document,"Leyendo identidad ICAO…",true)
                    }) }
                kotlin.coroutines.coroutineContext.ensureActive()
                _identitySnapshot.value=IdentitySnapshot(generation,result.identity,result.identityIssue)
                val certMap=result.certificates?.certificates.orEmpty()
                _uiState.value=if(certMap.isEmpty()) CertificatesUiState.Error(
                    result.certificateIssue ?: "No se encontraron certificados; revisa Identidad para los datos ICAO disponibles.")
                else CertificatesUiState.Loaded(certMap,
                    if(CertificateType.SIGNATURE in certMap) CertificateType.SIGNATURE else certMap.keys.first(),
                    if(identityFactory==null) null else result.identityIssue ?: "Certificados e identidad leídos. Consulta la pestaña Identidad.")
            } catch (cancelled: CancellationException) {
                retained?.certificates?.takeIf { it.isNotEmpty() }?.let { certs ->
                    _uiState.value=CertificatesUiState.Loaded(certs,
                        if(CertificateType.SIGNATURE in certs) CertificateType.SIGNATURE else certs.keys.first(),
                        "Lectura cancelada; se conservan los certificados completos.")
                }
                val previous=_identitySnapshot.value
                _identitySnapshot.value=previous.copy(reading=false,message="Lectura cancelada; se conservan los bloques completos.",
                    document=previous.document?.copy(completed=true,blocks=previous.document.blocks.mapValues { (_,block) ->
                        if(block.status in setOf(pe.gob.reniec.dnie.icao.BlockStatus.PENDING,pe.gob.reniec.dnie.icao.BlockStatus.READING))
                            block.copy(status=pe.gob.reniec.dnie.icao.BlockStatus.INTERRUPTED) else block
                    }))
                throw cancelled
            } catch (e: Exception) {
                if (!kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]!!.isActive) return@launch
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.failure("certificates", "read", e)
                _uiState.value = CertificatesUiState.Error(
                    e.message ?: "Error al leer certificados digitales del DNIe."
                )
            } finally {
                try {
                    withContext(NonCancellable + closeDispatcher) { runCatching { scan.close() } }
                } finally {
                    if (activeReader === transceiver) { activeReader = null;activeScan=null }
                }
            }
        }
    }

    override fun onCleared() {
        activeScan?.let { scan -> Thread({ scan.close() }, "certificates-close").start() }
        super.onCleared()
    }

    /**
     * Cambia la pestaña de certificado activo (Firma, Autenticación, Cifrado).
     */
    fun selectTab(type: CertificateType) {
        val current = _uiState.value
        if (current is CertificatesUiState.Loaded) {
            _uiState.value = current.copy(selectedType = type)
        }
    }

    /**
     * Exporta el certificado actualmente seleccionado a un flujo de salida (SAF).
     */
    fun exportCertificate(
        type: CertificateType,
        outputStream: OutputStream,
        asPem: Boolean
    ): Boolean {
        val current = _uiState.value as? CertificatesUiState.Loaded ?: return false
        val cert = current.certificates[type] ?: return false

        return try {
            outputStream.use { stream ->
                if (asPem) {
                    CertificateExporter.writePemToStream(cert.rawDerBytes, stream)
                } else {
                    CertificateExporter.writeCerToStream(cert.rawDerBytes, stream)
                }
            }
            true
        } catch (e: Exception) {
            pe.gob.reniec.dnie.diagnostics.DiagnosticLog.failure("certificates", "export", e)
            false
        }
    }

    /**
     * Reinicia el estado a Idle.
     */
    fun reset() {
        _uiState.value = CertificatesUiState.Idle
    }
}
