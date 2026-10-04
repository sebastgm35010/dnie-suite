package pe.gob.reniec.dnie.ui.signer

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult
import pe.gob.reniec.dnie.pdf.PadesSignerEngine
import pe.gob.reniec.dnie.pdf.PadesSignerEngineInterface
import pe.gob.reniec.dnie.pdf.PdfSignResult
import pe.gob.reniec.dnie.pdf.SignaturePreset
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Estados de la máquina de estados del Firmador PAdES en 2 pasos.
 */
sealed interface SignerUiState {
    data object NoDocument : SignerUiState

    data class DocumentLoaded(
        val documentUri: Uri? = null,
        val documentName: String,
        val documentSizeBytes: Long,
        val pageCount: Int,
        val preset: SignaturePreset = SignaturePreset.BOTTOM_RIGHT,
        val reason: String = "Soy el autor del documento",
        val location: String = "Lima, Perú"
    ) : SignerUiState

    data class EnteringPin(
        val documentUri: Uri? = null,
        val documentName: String,
        val documentSizeBytes: Long,
        val pageCount: Int,
        val preset: SignaturePreset,
        val reason: String,
        val location: String,
        val initialCan: String = ""
    ) : SignerUiState

    data class AwaitingCard(
        val documentUri: Uri? = null,
        val documentName: String,
        val documentSizeBytes: Long,
        val pageCount: Int,
        val preset: SignaturePreset,
        val reason: String,
        val location: String,
        val pin: String,
        val can: String? = null
    ) : SignerUiState

    data class Signing(
        val message: String = "Verificando PIN y generando firma PAdES..."
    ) : SignerUiState

    data class SignedSuccess(
        val signedFileUri: Uri?,
        val signedFilePath: String?,
        val signerName: String,
        val signerDni: String,
        val timestamp: Long,
        val signatureSize: Int
    ) : SignerUiState

    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : SignerUiState
}

/**
 * ViewModel que orquesta el ciclo de firma digital PAdES con verificación de credenciales (CAN + PIN).
 */
class SignerViewModel(
    private val signerEngine: PadesSignerEngineInterface = PadesSignerEngine.defaultInstance
) : ViewModel() {

    private val _uiState = MutableStateFlow<SignerUiState>(SignerUiState.NoDocument)
    val uiState: StateFlow<SignerUiState> = _uiState.asStateFlow()

    var enteredCan: String? = null
        private set

    fun onDocumentSelected(
        uri: Uri? = null,
        name: String,
        sizeBytes: Long,
        pageCount: Int = 1
    ) {
        _uiState.value = SignerUiState.DocumentLoaded(
            documentUri = uri,
            documentName = name,
            documentSizeBytes = sizeBytes,
            pageCount = pageCount
        )
    }

    fun onPresetSelected(preset: SignaturePreset) {
        val current = _uiState.value
        if (current is SignerUiState.DocumentLoaded) {
            _uiState.value = current.copy(preset = preset)
        }
    }

    fun onReasonChanged(reason: String) {
        val current = _uiState.value
        if (current is SignerUiState.DocumentLoaded) {
            _uiState.value = current.copy(reason = reason)
        }
    }

    fun onLocationChanged(location: String) {
        val current = _uiState.value
        if (current is SignerUiState.DocumentLoaded) {
            _uiState.value = current.copy(location = location)
        }
    }

    /**
     * Paso 1: Abre el diálogo de captura segura del CAN y PIN.
     */
    fun startPinEntry() {
        val current = _uiState.value as? SignerUiState.DocumentLoaded ?: return
        _uiState.value = SignerUiState.EnteringPin(
            documentUri = current.documentUri,
            documentName = current.documentName,
            documentSizeBytes = current.documentSizeBytes,
            pageCount = current.pageCount,
            preset = current.preset,
            reason = current.reason,
            location = current.location,
            initialCan = enteredCan ?: ""
        )
    }

    fun cancelPinEntry() {
        val current = _uiState.value as? SignerUiState.EnteringPin ?: return
        _uiState.value = SignerUiState.DocumentLoaded(
            documentUri = current.documentUri,
            documentName = current.documentName,
            documentSizeBytes = current.documentSizeBytes,
            pageCount = current.pageCount,
            preset = current.preset,
            reason = current.reason,
            location = current.location
        )
    }

    /**
     * Confirma tanto el CAN como el PIN antes de la lectura NFC.
     */
    fun onCredentialsConfirmed(can: String, pin: String) {
        val current = _uiState.value as? SignerUiState.EnteringPin ?: return
        enteredCan = can
        _uiState.value = SignerUiState.AwaitingCard(
            documentUri = current.documentUri,
            documentName = current.documentName,
            documentSizeBytes = current.documentSizeBytes,
            pageCount = current.pageCount,
            preset = current.preset,
            reason = current.reason,
            location = current.location,
            pin = pin,
            can = can
        )
    }

    /**
     * Paso 2: Retrocompatibilidad cuando solo se provee PIN.
     */
    fun onPinConfirmed(pin: String) {
        onCredentialsConfirmed(can = enteredCan ?: "", pin = pin)
    }

    fun cancelAwaitingCard() {
        val current = _uiState.value as? SignerUiState.AwaitingCard ?: return
        _uiState.value = SignerUiState.DocumentLoaded(
            documentUri = current.documentUri,
            documentName = current.documentName,
            documentSizeBytes = current.documentSizeBytes,
            pageCount = current.pageCount,
            preset = current.preset,
            reason = current.reason,
            location = current.location
        )
    }

    /**
     * Ejecuta el proceso criptográfico de firma con streams explícitos (útil para pruebas unitarias).
     */
    fun onCardDetected(
        transceiver: DnieTransceiverInterface,
        inputStream: InputStream,
        outputStream: OutputStream
    ) {
        val awaiting = _uiState.value as? SignerUiState.AwaitingCard
        if (awaiting == null) {
            _uiState.value = SignerUiState.Error("Operación de firma no iniciada o estado inválido.")
            return
        }

        _uiState.value = SignerUiState.Signing("Verificando credenciales en el chip del DNIe...")

        if (transceiver is pe.gob.reniec.dnie.core.nfc.DnieTransceiver) {
            if (transceiver.can.isNullOrBlank() && !awaiting.can.isNullOrBlank()) {
                transceiver.can = awaiting.can
            }
            if (transceiver.pin.isNullOrBlank() && awaiting.pin.isNotBlank()) {
                transceiver.pin = awaiting.pin
            }
        }

        viewModelScope.launch {
            try {
                // 1. Seleccionar Applet PKI / ICAO
                transceiver.selectPkiApplet().getOrThrow()

                // 2. Verificar PIN en tarjeta
                val pinResult = transceiver.verifyPin(awaiting.pin).getOrThrow()
                when (pinResult) {
                    is PinVerificationResult.Success -> {
                        // PIN correcto, proceder a la firma
                    }
                    is PinVerificationResult.InvalidPin -> {
                        throw IllegalStateException(
                            "PIN incorrecto. Le quedan ${pinResult.attemptsRemaining} intento(s) antes del bloqueo."
                        )
                    }
                    is PinVerificationResult.CardBlocked -> {
                        throw IllegalStateException(
                            "El chip está bloqueado (error 69 83). Acuda a RENIEC con su PUK para desbloquearlo."
                        )
                    }
                }

                // 3. Extraer certificado de firma (EF 00 1D)
                _uiState.value = SignerUiState.Signing("Leyendo certificado de firma...")
                val certDer = transceiver.readCertificate(CertificateType.SIGNATURE).getOrThrow()

                // 4. Firmar documento PAdES
                _uiState.value = SignerUiState.Signing("Calculando firma digital PAdES en hardware...")
                val signResult = signerEngine.signPdf(
                    pdfInputStream = inputStream,
                    pdfOutputStream = outputStream,
                    signingCertificateDer = certDer,
                    cardSigner = { hash -> transceiver.signSha256Hash(hash) },
                    preset = awaiting.preset,
                    reason = awaiting.reason,
                    location = awaiting.location
                ).getOrThrow()

                _uiState.value = SignerUiState.SignedSuccess(
                    signedFileUri = null,
                    signedFilePath = null,
                    signerName = signResult.signerName,
                    signerDni = signResult.signerDni ?: "",
                    timestamp = signResult.timestamp,
                    signatureSize = signResult.signatureSize
                )
            } catch (e: Exception) {
                _uiState.value = SignerUiState.Error(
                    message = e.message ?: "Error desconocido durante la firma del documento.",
                    canRetry = true
                )
            }
        }
    }

    /**
     * Ejecuta el proceso criptográfico de firma en entorno Android usando ContentResolver y archivos de caché.
     */
    fun onCardDetected(
        transceiver: DnieTransceiverInterface,
        context: Context
    ) {
        val awaiting = _uiState.value as? SignerUiState.AwaitingCard ?: return
        val docUri = awaiting.documentUri
        if (docUri == null) {
            _uiState.value = SignerUiState.Error("No se ha especificado una URI válida para el documento.")
            return
        }

        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(docUri)
        if (inputStream == null) {
            _uiState.value = SignerUiState.Error("No se pudo abrir el documento fuente seleccionado.")
            return
        }

        // Crear archivo de destino en la carpeta de caché protegida de la app
        val signedDir = File(context.cacheDir, "signed_docs").apply { mkdirs() }
        val sanitizedName = awaiting.documentName.removeSuffix(".pdf")
        val outputFile = File(signedDir, "${sanitizedName}_firmado_${System.currentTimeMillis()}.pdf")
        val outputStream = FileOutputStream(outputFile)

        _uiState.value = SignerUiState.Signing("Verificando credenciales en el chip del DNIe...")

        if (transceiver is pe.gob.reniec.dnie.core.nfc.DnieTransceiver) {
            if (transceiver.can.isNullOrBlank() && !awaiting.can.isNullOrBlank()) {
                transceiver.can = awaiting.can
            }
            if (transceiver.pin.isNullOrBlank() && awaiting.pin.isNotBlank()) {
                transceiver.pin = awaiting.pin
            }
        }

        viewModelScope.launch {
            try {
                transceiver.selectPkiApplet().getOrThrow()

                val pinResult = transceiver.verifyPin(awaiting.pin).getOrThrow()
                when (pinResult) {
                    is PinVerificationResult.Success -> {
                        // Continuar
                    }
                    is PinVerificationResult.InvalidPin -> {
                        throw IllegalStateException(
                            "PIN incorrecto. Le quedan ${pinResult.attemptsRemaining} intento(s) antes del bloqueo."
                        )
                    }
                    is PinVerificationResult.CardBlocked -> {
                        throw IllegalStateException(
                            "El chip está bloqueado (error 69 83). Acuda a RENIEC con su PUK para desbloquearlo."
                        )
                    }
                }

                _uiState.value = SignerUiState.Signing("Leyendo certificado de firma...")
                val certDer = transceiver.readCertificate(CertificateType.SIGNATURE).getOrThrow()

                _uiState.value = SignerUiState.Signing("Calculando firma digital PAdES con DNIe...")
                val signResult = signerEngine.signPdf(
                    pdfInputStream = inputStream,
                    pdfOutputStream = outputStream,
                    signingCertificateDer = certDer,
                    cardSigner = { hash -> transceiver.signSha256Hash(hash) },
                    preset = awaiting.preset,
                    reason = awaiting.reason,
                    location = awaiting.location
                ).getOrThrow()

                val shareUri = try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        outputFile
                    )
                } catch (_: Exception) {
                    Uri.fromFile(outputFile)
                }

                _uiState.value = SignerUiState.SignedSuccess(
                    signedFileUri = shareUri,
                    signedFilePath = outputFile.absolutePath,
                    signerName = signResult.signerName,
                    signerDni = signResult.signerDni ?: "",
                    timestamp = signResult.timestamp,
                    signatureSize = signResult.signatureSize
                )
            } catch (e: Exception) {
                outputFile.delete()
                _uiState.value = SignerUiState.Error(
                    message = e.message ?: "Error desconocido durante la firma del documento.",
                    canRetry = true
                )
            } finally {
                try { inputStream.close() } catch (_: Exception) {}
                try { outputStream.close() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Reinicia el flujo al estado sin documento.
     */
    fun reset() {
        _uiState.value = SignerUiState.NoDocument
    }
}
