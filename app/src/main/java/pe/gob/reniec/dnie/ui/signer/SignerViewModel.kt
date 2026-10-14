package pe.gob.reniec.dnie.ui.signer

import pe.gob.reniec.dnie.domain.feedback.*
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
import pe.gob.reniec.dnie.domain.signing.*
import pe.gob.reniec.dnie.data.signing.DocumentImportService
import pe.gob.reniec.dnie.pdf.PdfSigningOptions
import pe.gob.reniec.dnie.core.nfc.DnieTransceiver
import pe.gob.reniec.dnie.crypto.CertificateExporter
import pe.gob.reniec.dnie.crypto.X509CertificateParser


/**
 * ViewModel que orquesta el ciclo de firma digital PAdES con verificación de credenciales (CAN + PIN).
 */
class SignerViewModel(private val signerEngine: PadesSignerEngineInterface? = null) : ViewModel() {
    private val feedbackEvents=ReadingFeedbackEvents()
    val feedback=feedbackEvents.flow

    private val _uiState = MutableStateFlow<SignerUiState>(SignerUiState.NoDocument)
    val uiState = _uiState.asStateFlow()
    private val _workspace = MutableStateFlow(SigningWorkspace())
    val workspace = _workspace.asStateFlow()
    var enteredCan: String? = null
        private set
    private var editor: SignerUiState.DocumentLoaded? = null
    private var approved = emptyList<SigningDocument>()
    private var approvedMark: HandwrittenMark? = null
    private var signingJob: Job? = null
    private var importJob: Job? = null
    private var activeCard: DnieTransceiverInterface? = null
    private var stampPreferences: pe.gob.reniec.dnie.data.preferences.AndroidStampPreferences?=null
    private var operation = 0
    private var importOperation = 0
    val busy: Boolean get() = _uiState.value is SignerUiState.Signing || _uiState.value is SignerUiState.AwaitingCard || _uiState.value is SignerUiState.EnteringPin

    fun importDocuments(context: Context, uris: List<Uri>, singlePdf: Boolean = false) {
        if (busy || _workspace.value.importing || _workspace.value.exporting || uris.isEmpty()) return
        val token = ++importOperation
        val app = context.applicationContext
        _workspace.update { it.copy(importing = true, importErrors = emptyList()) }
        importJob = viewModelScope.launch {
            val preferences=(app as? pe.gob.reniec.dnie.DnieApplication)?.stampPreferences ?: pe.gob.reniec.dnie.data.preferences.AndroidStampPreferences(app)
            stampPreferences=preferences
            val design=runCatching {preferences.load()}.getOrDefault(PdfSigningOptions())
            val imported = mutableListOf<SigningDocument>()
            val errors = mutableListOf<String>()
            try {
                val result = ImportSigningDocuments().run(uris, _workspace.value.documents, singlePdf,
                    import = { DocumentImportService.import(app, it) },
                    discard = { DocumentImportService.discard(it) },
                    failed = { pe.gob.reniec.dnie.diagnostics.DiagnosticLog.failure("signer", "import", it) })
                imported += result.documents.map {doc->doc.copy(options=design.copy(pageNumber=design.pageNumber.coerceIn(1,doc.pages.size.coerceAtLeast(1))))}
                errors += result.errors
                if (token == importOperation) {
                    val replacing = singlePdf && imported.isNotEmpty()
                    val previous = if (replacing) _workspace.value else null
                    if (replacing) editor = null
                    _workspace.update { it.copy(documents = if (singlePdf && imported.isNotEmpty()) imported.toList() else it.documents + imported,
                        artifacts = if (singlePdf && imported.isNotEmpty()) emptyList() else it.artifacts,
                        importing = false, importErrors = errors,
                        selectedId = if (singlePdf && imported.isNotEmpty()) imported.first().id else it.selectedId ?: imported.firstOrNull()?.id) }
                    imported.clear() // Ownership transferred to the workspace before any suspension.
                    selectDocument(_workspace.value.selectedId)
                    if (previous != null) withContext(NonCancellable + Dispatchers.IO) {
                        previous.documents.forEach { it.file.delete(); it.file.parentFile?.delete() }
                        previous.artifacts.forEach { it.output?.delete(); it.output?.parentFile?.delete() }
                    }
                } else imported.forEach { DocumentImportService.discard(it) }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { imported.forEach { DocumentImportService.discard(it) } }
                throw e
            }
            finally { if (token == importOperation) _workspace.update { it.copy(importing = false) } }
        }
    }

    fun selectDocument(id: String?) {
        if (busy) return
        val doc = _workspace.value.documents.firstOrNull { it.id == id } ?: return
        _workspace.update { it.copy(selectedId = doc.id) }
        _uiState.value = SignerUiState.DocumentLoaded(Uri.fromFile(doc.file), doc.name, doc.size, doc.pages.size,
            if (doc.options.visible) SignaturePreset.CUSTOM else SignaturePreset.INVISIBLE, doc.options.reason, doc.options.location)
    }
    fun removeDocument(id: String) {
        if (busy || _workspace.value.importing || _workspace.value.exporting) return
        val doc = _workspace.value.documents.firstOrNull { it.id == id } ?: return
        _workspace.value.artifacts.filter { it.document.id == id }.forEach { it.output?.delete(); it.output?.parentFile?.delete() }
        doc.file.delete(); doc.file.parentFile?.delete()
        _workspace.update { w -> w.copy(documents = w.documents.filter { it.id != id }, artifacts = w.artifacts.filter { it.document.id != id },
            selectedId = if (w.selectedId == id) w.documents.firstOrNull { it.id != id }?.id else w.selectedId) }
        if (_workspace.value.documents.isEmpty()) _uiState.value = SignerUiState.NoDocument else selectDocument(_workspace.value.selectedId)
    }
    fun updateOptions(transform: (PdfSigningOptions) -> PdfSigningOptions) {
        if (busy) return
        val selected = _workspace.value.selected ?: return
        if (_workspace.value.artifacts.any { it.document.id == selected.id && it.status == DocumentSignStatus.SIGNED }) return
        _workspace.update { w -> w.copy(documents = w.documents.map { if (it.id == selected.id) it.copy(options = transform(it.options)) else it }) }
        val options = _workspace.value.selected!!.options
        stampPreferences?.save(options)
        (_uiState.value as? SignerUiState.DocumentLoaded)?.let { _uiState.value = it.copy(reason = options.reason, location = options.location) }
    }
    fun restoreDefaultDesign() {updateOptions {pe.gob.reniec.dnie.pdf.PdfSigningOptions()}}
    fun applyOptionsToAll() {
        if (busy) return
        val options = _workspace.value.selected?.options ?: return
        val signed = _workspace.value.artifacts.filter { it.status == DocumentSignStatus.SIGNED }.map { it.document.id }.toSet()
        _workspace.update { w -> w.copy(documents = w.documents.map { doc ->
            if (!doc.isPdf || doc.id in signed) doc else doc.copy(options = options.copy(pageNumber = options.pageNumber.coerceIn(1, doc.pages.size)))
        }) }
    }
    fun setDetached(value: Boolean) {
        if (busy) return
        val doc = _workspace.value.selected ?: return
        if (_workspace.value.artifacts.any { it.document.id == doc.id && it.status == DocumentSignStatus.SIGNED }) return
        _workspace.update { w -> w.copy(documents = w.documents.map { if (it.id == doc.id) it.copy(detached = value) else it }) }
    }
    fun setHandwrittenMark(mark: HandwrittenMark?, error: String? = null) {
        _workspace.update { w -> if (w.mark?.png?.contentEquals(mark?.png ?: byteArrayOf()) == true && w.mark?.personalNumber == mark?.personalNumber && w.mark?.sourceName == mark?.sourceName && w.markError == error) w
            else w.copy(mark = mark, markError = error, markConfirmed = false) }
    }
    fun confirmMark(value: Boolean) { if (!busy) _workspace.update { it.copy(markConfirmed = value) } }
    fun setPublicCertificate(bytes: ByteArray?) {
        if (bytes == null) { _workspace.update {it.copy(certificate=null,previewName="NOMBRE DEL TITULAR",previewDni="00000000")};return }
        val citizen = runCatching { X509CertificateParser.extractCitizenData(X509CertificateParser.toX509Certificate(bytes)) }.getOrNull() ?: return
        _workspace.update { it.copy(certificate = bytes, previewName = citizen.fullName, previewDni = citizen.dniNumber) }
    }

    fun onDocumentSelected(uri: Uri? = null, name: String, sizeBytes: Long, pageCount: Int = 1) {
        _uiState.value = SignerUiState.DocumentLoaded(documentUri = uri, documentName = name, documentSizeBytes = sizeBytes, pageCount = pageCount)
    }
    fun onPresetSelected(preset: SignaturePreset) {
        (_uiState.value as? SignerUiState.DocumentLoaded)?.let { _uiState.value = it.copy(preset = preset) }
    }
    fun onReasonChanged(reason: String) {
        (_uiState.value as? SignerUiState.DocumentLoaded)?.let { _uiState.value = it.copy(reason = reason) }
        updateOptions { it.copy(reason = reason.take(200)) }
    }
    fun onLocationChanged(location: String) {
        (_uiState.value as? SignerUiState.DocumentLoaded)?.let { _uiState.value = it.copy(location = location) }
        updateOptions { it.copy(location = location.take(120)) }
    }
    fun startPinEntry() {
        if (_workspace.value.importing || _workspace.value.exporting) return
        val current = _uiState.value as? SignerUiState.DocumentLoaded ?: return
        approved = _workspace.value.pending.toList()
        approvedMark = _workspace.value.mark
        if (approved.any { it.isPdf && it.options.visible && it.options.appearance.requiresHandwritten } && (approvedMark == null || !_workspace.value.markConfirmed)) {
            _workspace.update { it.copy(exportMessage = "Lea la imagen manuscrita y confirme que corresponde al titular.") }; return
        }
        editor = current
        _uiState.value = SignerUiState.EnteringPin(current.documentUri, current.documentName, current.documentSizeBytes,
            current.pageCount, current.preset, current.reason, current.location, enteredCan ?: "")
    }
    fun cancelPinEntry() { if (_uiState.value is SignerUiState.EnteringPin) restoreEditor() }
    fun onCredentialsConfirmed(can: String, pin: String) {
        val current = _uiState.value as? SignerUiState.EnteringPin ?: return
        enteredCan = can
        _uiState.value = SignerUiState.AwaitingCard(current.documentUri, current.documentName, current.documentSizeBytes,
            current.pageCount, current.preset, current.reason, current.location, pin, can)
    }
    fun onPinConfirmed(pin: String) = onCredentialsConfirmed(enteredCan ?: "", pin)
    fun cancelAwaitingCard() { if (_uiState.value is SignerUiState.AwaitingCard) restoreEditor() }
    fun restoreEditor() {
        _uiState.value = editor ?: _workspace.value.selected?.let {
            SignerUiState.DocumentLoaded(documentName = it.name, documentSizeBytes = it.size, pageCount = it.pages.size)
        } ?: SignerUiState.NoDocument
    }

    fun onCardDetected(transceiver: DnieTransceiverInterface, context: Context) {
        val awaiting = _uiState.value as? SignerUiState.AwaitingCard ?: run { transceiver.close(); return }
        if (approved.isEmpty()) { transceiver.close(); _uiState.value = SignerUiState.Error("No hay archivos preparados para firmar."); return }
        val token = ++operation
        val app = context.applicationContext
        var lastTick=0
        val documents = approved
        val mark = approvedMark
        if (transceiver is DnieTransceiver) { transceiver.can = awaiting.can; transceiver.pin = awaiting.pin }
        activeCard = transceiver
        _uiState.value = SignerUiState.Signing("Abriendo sesión y verificando PIN…", 0, documents.size)
        signingJob = viewModelScope.launch {
            try {
                val ids = documents.map { it.id }.toSet()
                val previous = _workspace.value.artifacts.filter { it.document.id !in ids }
                val result = withContext(Dispatchers.IO) {
                    val timestampService = pe.gob.reniec.dnie.data.signing.AndroidTimestampServices.create(app)
                    val engine = signerEngine ?: PadesSignerEngine(pe.gob.reniec.dnie.timestamp.CmsTimestampService { cms ->
                        _uiState.update { current ->
                            if (token == operation && current is SignerUiState.Signing)
                                current.copy(message = "Solicitando y verificando sello de tiempo RENIEC…") else current
                        }
                        timestampService.stamp(cms)
                    })
                    BatchSigner(engine).sign(documents, transceiver, awaiting.pin, File(app.cacheDir, "signed_docs"), mark) { count, name, artifacts, cert ->
                        if (token == operation) {
                            _uiState.value = SignerUiState.Signing("$count de ${documents.size} · $name", count, documents.size)
                            _workspace.update { it.copy(artifacts = previous + artifacts) }
                            val signed=artifacts.count {it.status==DocumentSignStatus.SIGNED}
                            if(documents.size>1 && signed>lastTick) {lastTick=signed;feedbackEvents.report(ReadingCue.PROGRESS)}
                            if (cert != null) setPublicCertificate(cert)
                        }
                    }
                }
                if (token != operation) return@launch
                _workspace.update { it.copy(artifacts = previous + result.artifacts) }
                feedbackEvents.report(ReadingOutcome.batch(result.artifacts.count {it.status==DocumentSignStatus.SIGNED},documents.size))
                val success = result.artifacts.firstOrNull { it.status == DocumentSignStatus.SIGNED }
                if (success == null) _uiState.value = SignerUiState.Error(result.sessionError ?: result.artifacts.firstOrNull()?.message ?: "No se firmó ningún archivo")
                else {
                    val output = requireNotNull(success.output)
                    val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", output)
                    _uiState.value = SignerUiState.SignedSuccess(uri, output.absolutePath, success.signerName, success.signerDni, success.timestamp, success.signatureSize)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { pe.gob.reniec.dnie.diagnostics.DiagnosticLog.failure("signer", "sign_batch", e); if (token == operation) {feedbackEvents.report(ReadingCue.ERROR);_uiState.value = SignerUiState.Error(e.message ?: "Error en el lote")} }
            finally { if (token == operation) { activeCard = null; enteredCan = null; approved = emptyList(); approvedMark = null } }
        }
    }
    fun cancelSigning() {
        ++operation; signingJob?.cancel(); activeCard?.close(); activeCard = null
        enteredCan = null; approved = emptyList(); approvedMark = null
        if (busy) restoreEditor()
    }
    fun export(context: Context, uri: Uri, artifact: SignedArtifact? = null, certificateAsPem: Boolean? = null) {
        if (_workspace.value.exporting) return
        val artifacts = _workspace.value.artifacts.toList()
        val cert = _workspace.value.certificate
        _workspace.update { it.copy(exporting = true, exportMessage = null) }
        viewModelScope.launch {
            val outcome = runCatching { withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openOutputStream(uri)).use { out ->
                    when {
                        certificateAsPem != null -> {
                            val bytes = requireNotNull(cert) { "Extraiga primero el certificado del DNI" }
                            if (certificateAsPem) CertificateExporter.writePemToStream(bytes, out) else CertificateExporter.writeCerToStream(bytes, out)
                        }
                        artifact != null -> requireNotNull(artifact.output).inputStream().use { it.copyTo(out) }
                        else -> SignedBatchExporter.write(artifacts, out)
                    }
                }
            } }
            _workspace.update { it.copy(exporting = false, exportMessage = if (outcome.isSuccess) "Archivo guardado" else outcome.exceptionOrNull()?.message ?: "No se pudo guardar") }
        }
    }
    fun reset() {
        if (_workspace.value.exporting) return
        cancelSigning(); ++importOperation; importJob?.cancel()
        _workspace.value.artifacts.forEach { it.output?.delete(); it.output?.parentFile?.delete() }
        _workspace.value.documents.forEach { it.file.delete(); it.file.parentFile?.delete() }
        val previous = _workspace.value
        _workspace.value = SigningWorkspace(mark = previous.mark, certificate = previous.certificate, previewName = previous.previewName, previewDni = previous.previewDni)
        editor = null; _uiState.value = SignerUiState.NoDocument
    }
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

        activeCard = transceiver
        signingJob = viewModelScope.launch {
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
                val signResult = (signerEngine ?: PadesSignerEngine.defaultInstance).signPdf(
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
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.failure("signer", "export", e)
                _uiState.value = SignerUiState.Error(
                    message = e.message ?: "Error desconocido durante la firma del documento.",
                    canRetry = true
                )
            }
            finally { inputStream.close(); outputStream.close(); transceiver.close(); activeCard = null }
        }
    }

    override fun onCleared() { cancelSigning(); importJob?.cancel() }

}
