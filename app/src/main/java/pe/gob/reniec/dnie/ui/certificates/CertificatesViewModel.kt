package pe.gob.reniec.dnie.ui.certificates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    data class ReadingCard(
        val message: String = "Aproxime su DNIe al sensor NFC para leer los certificados..."
    ) : CertificatesUiState

    data class Loaded(
        val certificates: Map<CertificateType, CertificateInfo>,
        val selectedType: CertificateType = CertificateType.SIGNATURE
    ) : CertificatesUiState

    data class Error(
        val message: String
    ) : CertificatesUiState
}

/**
 * ViewModel para la lectura e inspección de certificados X.509 públicos del DNIe.
 */
class CertificatesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<CertificatesUiState>(CertificatesUiState.Idle)
    val uiState: StateFlow<CertificatesUiState> = _uiState.asStateFlow()

    /**
     * Inicia la solicitud de aproximación de tarjeta para lectura.
     */
    fun startReading() {
        _uiState.value = CertificatesUiState.ReadingCard()
    }

    /**
     * Cancela la espera de tarjeta y regresa a estado Idle o mantiene certificados si ya estaban cargados.
     */
    fun cancelReading() {
        if (_uiState.value is CertificatesUiState.ReadingCard) {
            _uiState.value = CertificatesUiState.Idle
        }
    }

    /**
     * Procesa la tarjeta detectada a través del transceptor APDU.
     */
    fun onCardDetected(transceiver: DnieTransceiverInterface) {
        _uiState.value = CertificatesUiState.ReadingCard("Conectando con Applet PKI de RENIEC...")

        viewModelScope.launch {
            try {
                // 1. Seleccionar Applet PKI de RENIEC
                val selectResult = transceiver.selectPkiApplet()
                selectResult.getOrThrow()

                // 2. Extraer certificados secuencialmente
                val certMap = mutableMapOf<CertificateType, CertificateInfo>()

                for (type in CertificateType.entries) {
                    _uiState.value = CertificatesUiState.ReadingCard("Leyendo certificado de ${type.displayName}...")
                    val readResult = transceiver.readCertificate(type)

                    readResult.onSuccess { bytes ->
                        val certInfo = X509CertificateParser.parse(bytes, type)
                        certMap[type] = certInfo
                    }.onFailure { ex ->
                        if (type.isMandatory) {
                            throw ex
                        }
                        // Certificados opcionales (ej. cifrado) no interrumpen el flujo
                    }
                }

                if (certMap.isEmpty()) {
                    _uiState.value = CertificatesUiState.Error("No se encontraron certificados digitales en el chip.")
                } else {
                    _uiState.value = CertificatesUiState.Loaded(
                        certificates = certMap,
                        selectedType = CertificateType.SIGNATURE
                    )
                }
            } catch (e: Exception) {
                _uiState.value = CertificatesUiState.Error(
                    e.message ?: "Error al leer certificados digitales del DNIe."
                )
            }
        }
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
