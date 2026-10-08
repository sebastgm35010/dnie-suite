package pe.gob.reniec.dnie.ui.vault

import android.content.Context
import android.net.Uri
import pe.gob.reniec.dnie.data.vault.AndroidVaultRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.gob.reniec.dnie.core.nfc.VaultProbeCard
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult
import pe.gob.reniec.dnie.vault.*
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

class VaultViewModel(private val cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private var canStore: pe.gob.reniec.dnie.domain.vault.RememberedCanStore? = null) : ViewModel() {
    private var rememberedCan = ""
    private val state = MutableStateFlow(VaultUiState())
    val uiState = state.asStateFlow()
    private var job: Job? = null
    private var card: VaultProbeCard? = null
    private var generation = 0
    private var repository: AndroidVaultRepository? = null
    private var initialized = false
    private var destination: Uri? = null
    private var opening = false
    private var previewRequested = false
    private var inventory = emptyList<VaultCatalogItem>()
    private var library = VaultLibrarySnapshot(emptyList())
    private var filterJob: Job? = null
    private var filterGeneration = 0
    private var incomingSources = emptyList<Uri>()
    private class RecoveryPreparation(val id: String, val destination: Uri, val password: CharArray)
    private var pendingRecovery: RecoveryPreparation? = null
    // First use is in an IO/crypto block, keeping SecureRandom/class loading off startup UI.
    private val probe by lazy { VaultCapabilityProbe() }
    private val busy get() = state.value.awaitingCard || state.value.running
    val requestedCan: String? get() = state.value.takeIf { it.awaitingCard }?.can
    private val files get() = requireNotNull(repository) { "La bóveda todavía no está preparada" }

    fun initialize(value: Context) {
        if (repository == null) repository = AndroidVaultRepository(value)
        if (canStore == null) canStore = pe.gob.reniec.dnie.data.preferences.AndroidRememberedCanStore(value)
        if (busy) return
        if (initialized) { refreshRememberedCan(); return }
        launchDisk("Preparando bóveda…") {
            val inventory = files.initialize()
            state.value = state.value.copy(pairedFingerprint = inventory.pairedFingerprint,
                riskAcknowledged = inventory.riskAcknowledged)
            initialized = true
            loadRememberedCan()
        }
    }

    fun refreshRememberedCan() {
        if (busy) return
        val operation = generation
        viewModelScope.launch {
            val saved = canStore?.read().orEmpty()
            if (generation == operation && !busy) applyRememberedCan(saved)
        }
    }
    private suspend fun loadRememberedCan() { applyRememberedCan(canStore?.read().orEmpty()) }
    private fun applyRememberedCan(saved: String) {
        rememberedCan = saved.takeIf { it.length == 6 && it.all { digit -> digit in '0'..'9' } }.orEmpty()
        state.value = state.value.copy(can = rememberedCan, rememberCan = rememberedCan.isNotEmpty())
    }
    private fun idleCan() = if (state.value.rememberCan) rememberedCan else ""
    fun setRememberCan(remember: Boolean) {
        if (busy) return
        state.value = state.value.copy(rememberCan = remember)
        if (!remember) { rememberedCan = ""; viewModelScope.launch { canStore?.clear() } }
    }
    private fun persistCan() {
        val can = state.value.can
        if (state.value.rememberCan) {
            rememberedCan = can
            viewModelScope.launch { canStore?.save(can) }
        }
    }

    fun updateCan(value: String) { if (!busy) state.value = state.value.copy(can = value.filter { it in '0'..'9' }.take(6)) }
    fun updatePin(value: String) { if (!busy) state.value = state.value.copy(pin = value.filter { it in '0'..'9' }.take(8)) }
    private fun credentialsValid(): Boolean {
        if (state.value.can.length == 6 && state.value.pin.length in 4..8) return true
        state.value = state.value.copy(message = "Ingrese CAN de 6 dígitos y PIN de 4 a 8 dígitos.", verified = false)
        return false
    }
    fun startTest() {
        if (busy || !credentialsValid()) return
        persistCan()
        opening = false
        clearSelection()
        state.value = state.value.copy(awaitingCard = true, verified = false, certificateFingerprint = null,
            restoredName = null, restoredNameApplied = null,
            message = "Acerque el DNI y manténgalo inmóvil. Se verificará la capacidad de descifrado.")
    }
    fun prepareRecovery(id: String, uri: Uri, password: CharArray) {
        if (busy || !state.value.riskAcknowledged || !credentialsValid() ||
            state.value.items.none { it.id == id && !it.trashed } || password.size !in 16..1024) {
            password.fill('\u0000'); return
        }
        clearSelection(); persistCan(); opening = false
        pendingRecovery = RecoveryPreparation(id, uri, password)
        state.value = state.value.copy(awaitingCard = true, verified = false,
            message = "Acerque el DNI original para preparar la recuperación de este archivo.")
    }
    fun restoreRecovery(id: String, packageUri: Uri, password: CharArray, uri: Uri) {
        if (busy || !state.value.riskAcknowledged || state.value.items.none { it.id == id && !it.trashed }) {
            password.fill('\u0000'); return
        }
        launchDisk("Verificando paquete y recuperando sin el DNI antiguo…",
            { "Archivo recuperado. Conserva el paquete separado. El archivo cifrado mantiene su destinatario original." }) {
            val restored = files.restoreRecovery(id, packageUri, password, uri)
            state.value = state.value.copy(restoredName = restored.metadata.name,
                restoredNameApplied = restored.nameApplied, verified = true)
        }
        job?.invokeOnCompletion { password.fill('\u0000') }
    }
    fun requestOpen(id: String, uri: Uri) {
        if (busy) return
        if (!state.value.riskAcknowledged || state.value.items.none { it.id == id && !it.trashed }) return
        previewRequested = false
        destination = uri
        state.value = state.value.copy(selectedItemId = id, can = idleCan(), pin = "", restoredName = null, restoredNameApplied = null, previewMode = false,
            message = "Destino elegido. Ingrese CAN y PIN y acerque el DNI al iniciar.")
    }
    fun startOpen() {
        if (busy || (!previewRequested && destination == null) || state.value.selectedItemId == null || !state.value.riskAcknowledged) return
        if (!credentialsValid()) return
        persistCan()
        opening = true
        state.value = state.value.copy(awaitingCard = true, verified = false, message = "Acerque el DNI para abrir este archivo.")
    }
    fun clearSelection() {
        destination = null
        previewRequested = false
        state.value = state.value.copy(selectedItemId = null, previewMode = false)
    }
    fun acknowledgeRisk() {
        repository?.acknowledgeRisk()
        state.value = state.value.copy(riskAcknowledged = true)
    }

    fun onCardDetected(detected: VaultProbeCard) {
        if (!state.value.awaitingCard || job?.isActive == true) { detected.close(); return }
        val operation = ++generation
        val enteredPin = state.value.pin
        val recovery = pendingRecovery
        pendingRecovery = null
        val id = recovery?.id ?: state.value.selectedItemId
        val target = destination
        val restore = opening
        val preview = previewRequested
        card = detected
        state.value = state.value.copy(can = "", pin = "", awaitingCard = false, running = true,
            message = "Abriendo canal seguro PKI…")
        job = viewModelScope.launch {
            var secret: ByteArray? = null
            try {
                VaultDebugTrace.measure("nfc.pki_open") { detected.selectPkiApplet().getOrThrow() }
                ensureActive()
                detected.requireVaultProbeSupport().getOrThrow()
                state.value = state.value.copy(message = "Leyendo certificado de cifrado…")
                val der = VaultDebugTrace.measure("nfc.ce83_read") { detected.readCertificate(CertificateType.ENCRYPTION).getOrThrow() }
                val certificate = VaultDebugTrace.measure("certificate.parse") { withContext(cryptoDispatcher) { probe.readEncryptionCertificate(der) } }
                val key = certificate.publicKey as RSAPublicKey
                val fingerprint = probe.fingerprint(key)
                val header = if (restore || recovery != null) files.readHeader(requireNotNull(id)) else null
                val recipient = MessageDigest.getInstance("SHA-256").digest(key.encoded)
                require(header == null || MessageDigest.isEqual(header.recipient, recipient)) { "Este archivo requiere otro DNI. No se verificó el PIN." }
                ensureActive()
                state.value = state.value.copy(certificateFingerprint = fingerprint, message = "Verificando PIN…")
                when (val result = VaultDebugTrace.measure("nfc.pin_verify") { detected.verifyPin(enteredPin).getOrThrow() }) {
                    PinVerificationResult.Success -> Unit
                    is PinVerificationResult.InvalidPin -> error("PIN incorrecto. Quedan ${result.attemptsRemaining} intentos. No se reintentó.")
                    PinVerificationResult.CardBlocked -> error("PIN bloqueado. La operación se detuvo.")
                }
                ensureActive()
                if (header != null) {
                    state.value = state.value.copy(message = "Recuperando la clave con el DNI…")
                    val recovered = VaultDebugTrace.measure("nfc.key_unwrap") { detected.unwrapVaultKey(header.envelope).getOrThrow() }.also { secret = it }
                    detected.close()
                    ensureActive()
                    state.value = state.value.copy(message = "Verificando archivo y guardando copia descifrada…")
                    if (recovery != null) {
                        state.value = state.value.copy(message = "Verificando archivo y protegiendo el paquete de recuperación…")
                        files.prepareRecovery(recovery.id, header, recovered, recovery.password, recovery.destination)
                        ensureActive()
                        state.value = state.value.copy(verified = true,
                            message = "Paquete verificado y guardado para este archivo. Guárdalo separado y comprueba la recuperación antes de renovar el DNI.")
                    } else if (preview) {
                        val ticket = files.preview(requireNotNull(id), recovered, recipient)
                        try {
                            ensureActive()
                            state.value = state.value.copy(verified = true, preview = ticket,
                                message = "Contenido verificado. Abriendo vista previa privada…")
                        } catch (failure: Throwable) { files.discardPreview(ticket.token); throw failure }
                    } else {
                        val restored = VaultDebugTrace.measure("file.restore_total") {
                            files.restore(requireNotNull(id), recovered, recipient, requireNotNull(target)) { stage, elapsed ->
                                VaultDebugTrace.metric(stage, elapsed)
                            }
                        }
                        ensureActive()
                        state.value = state.value.copy(verified = true, restoredName = restored.metadata.name,
                            restoredNameApplied = restored.nameApplied,
                            message = if (restored.nameApplied) "Contenido verificado. Copia guardada con su nombre y extensión originales."
                            else "Contenido verificado y copia guardada. El destino no permitió recuperar automáticamente el nombre.")
                    }
                    refreshInventory()
                } else {
                    state.value = state.value.copy(message = "Comprobando descifrado y vinculando certificado público…")
                    val result = VaultDebugTrace.measure("nfc.synthetic_probe") { withContext(cryptoDispatcher) {
                        probe.verify(key, CardKeyUnwrapper { ciphertext -> ensureActive(); detected.decipherTestSecret(ciphertext).getOrThrow() })
                    } }
                    ensureActive()
                    repository?.savePublicCertificate(der)
                    ensureActive()
                    state.value = state.value.copy(verified = result.verified,
                        pairedFingerprint = if (repository != null) fingerprint else state.value.pairedFingerprint,
                        message = "Prueba completa: clave y contenido verificados. Certificado público listo para cifrar archivos.")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: LinkageError) {
                if (generation == operation) state.value = state.value.copy(verified = false,
                    message = "Incompatibilidad interna de bibliotecas. Actualice la app; el contenedor cifrado se conserva.")
            }
            catch (e: Exception) {
                if (generation == operation) state.value = state.value.copy(verified = false,
                    message = e.message ?: "No se pudo completar la operación NFC.")
            } finally {
                secret?.fill(0)
                recovery?.password?.fill('\u0000')
                detected.close()
                if (generation == operation) {
                    card = null
                    clearSelection()
                    state.value = state.value.copy(can = idleCan(), pin = "", running = false, awaitingCard = false)
                }
            }
        }
        job?.invokeOnCompletion { recovery?.password?.fill('\u0000') }
    }

    fun importFile(uri: Uri) {
        if (!state.value.riskAcknowledged || state.value.pairedFingerprint == null) return
        importFiles(listOf(uri))
    }
    fun importArchive(uri: Uri) {
        if (!state.value.riskAcknowledged) return
        launchDisk("Importando contenedor cifrado (se verificará al abrir)…") { files.importArchive(uri) }
    }
    fun exportEncrypted(id: String, uri: Uri) = launchDisk("Exportando copia cifrada…") { files.exportEncrypted(id, uri) }
    fun delete(id: String) = launchDisk("Moviendo a papelera…") {
        files.trash(id, true)
        if (state.value.selectedItemId == id) clearSelection()
    }
    fun permanentlyDelete(id: String) = launchDisk("Eliminando definitivamente…") {
        files.delete(id)
        if (state.value.selectedItemId == id) clearSelection()
    }
    private fun launchDisk(message: String, success: () -> String = { "Operación completada." }, action: suspend () -> Unit) {
        if (busy) return
        val operation = ++generation
        state.value = state.value.copy(running = true, can = "", pin = "", message = message,
            restoredName = null, restoredNameApplied = null)
        job = viewModelScope.launch {
            try {
                VaultDebugTrace.measure("file.local_operation") {
                    action(); ensureActive(); refreshInventory()
                }
                if (generation == operation) state.value = state.value.copy(message = success())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == operation) state.value = state.value.copy(message = e.message ?: "No se pudo completar la operación.")
            } finally { if (generation == operation) state.value = state.value.copy(running = false, can = idleCan()) }
        }
    }
    fun requestPreview(id: String) {
        if (busy || !state.value.riskAcknowledged || state.value.items.none { it.id == id && !it.trashed && it.canPreview }) return
        if (state.value.items.first { it.id == id }.encryptedBytes > pe.gob.reniec.dnie.data.vault.VaultPreviewFiles.MAX_BYTES + 1048576) {
            state.value = state.value.copy(message = "Vista previa limitada a 32 MiB. Puedes descifrar este archivo a un destino externo.")
            return
        }
        closePendingPreview()
        destination = null
        previewRequested = true
        state.value = state.value.copy(selectedItemId = id, can = idleCan(), pin = "", previewMode = true,
            message = "Vista previa privada. Introduce CAN y PIN y acerca el DNI.")
    }
    fun previewLaunched() { state.value = state.value.copy(preview = null) }
    private fun closePendingPreview() {
        state.value.preview?.let { repository?.discardPreview(it.token) }
        state.value = state.value.copy(preview = null)
    }
    fun updateQuery(query: String) { state.value = state.value.copy(query = query.take(200)); filterItems(debounce = true) }
    fun selectFolder(folder: String?) { state.value = state.value.copy(folder = folder); filterItems() }
    fun showTrash(value: Boolean) { state.value = state.value.copy(showTrash = value); clearSelection(); filterItems() }
    fun sortBy(sort: VaultSort) { state.value = state.value.copy(sort = sort); filterItems() }
    fun organize(id: String, folder: String, tags: List<String>) = launchDisk("Organizando archivo…") {
        val labels = inventory.first { it.file.id == id }.labels
        files.organize(id, labels.copy(folder = folder.trim(), tags = tags.map(String::trim).filter(String::isNotEmpty).distinct()))
    }
    fun recoverTrash(id: String) = launchDisk("Recuperando archivo…") { files.trash(id, false) }
    fun importFiles(uris: List<Uri>) {
        if (!state.value.riskAcknowledged || state.value.pairedFingerprint == null || uris.isEmpty()) return
        var result = ""
        val folder = state.value.folder.orEmpty()
        launchDisk("Cifrando lote…", { result }) {
            val batch = pe.gob.reniec.dnie.domain.vault.VaultBatchImportUseCase().run(uris.distinct(),
                { uri -> files.importFile(uri, folder) }, { completed, total, failed ->
                    state.value = state.value.copy(message = "Lote: $completed/$total · $failed fallido(s)")
                })
            result = "Lote terminado: ${batch.completed} cifrado(s), ${batch.failed} fallido(s). Los originales se conservan."
        }
    }
    fun receiveSharedFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        incomingSources = (incomingSources + uris).distinct().take(1000)
        state.value = state.value.copy(incomingFiles = incomingSources.size)
    }
    fun dismissSharedFiles() { incomingSources = emptyList(); state.value = state.value.copy(incomingFiles = 0) }
    fun encryptSharedFiles() {
        if (busy || !state.value.riskAcknowledged || state.value.pairedFingerprint == null) return
        importFiles(incomingSources)
        dismissSharedFiles()
    }
    fun createNote(name: String, body: String) {
        if (!state.value.riskAcknowledged || state.value.pairedFingerprint == null) return
        val folder = state.value.folder.orEmpty()
        launchDisk("Guardando nota cifrada…") { files.createNote(name, body, folder) }
    }
    fun inspect(id: String) = launchDisk("Leyendo ficha del archivo…") { state.value = state.value.copy(details = files.details(id)) }
    fun closeDetails() { state.value = state.value.copy(details = null) }
    fun exportBackup(uri: Uri) {
        if (!state.value.riskAcknowledged) return
        launchDisk("Creando respaldo completo…", { "Respaldo guardado. Contiene archivos cifrados y etiquetas visibles; incluye la papelera." }) {
            val operation = generation
            files.exportBackup(uri) { done, total -> progress(operation, "Respaldo: $done/$total") }
        }
    }
    fun importBackup(uri: Uri) {
        if (!state.value.riskAcknowledged) return
        var count = 0
        launchDisk("Restaurando respaldo…", { "$count archivo(s) importados. Su autenticidad se comprobará al abrirlos con el DNI." }) {
            val operation = generation
            count = files.importBackup(uri) { done -> progress(operation, "Restaurando: $done archivo(s)") }
        }
    }
    private fun progress(operation: Int, message: String) {
        viewModelScope.launch { if (generation == operation && busy) state.value = state.value.copy(message = message) }
    }
    private suspend fun refreshInventory() {
        val source = files.inventory()
        val prepared = withContext(cryptoDispatcher) { VaultLibrarySnapshot(source) }
        inventory = source
        library = prepared
        state.value = state.value.copy(totalItems = prepared.totalItems, folders = prepared.folders)
        filterItems()
    }
    private fun filterItems(debounce: Boolean = false) {
        filterJob?.cancel()
        val request = ++filterGeneration
        val snapshot = state.value
        val source = library
        filterJob = viewModelScope.launch {
            if (debounce) delay(120)
            val items = withContext(cryptoDispatcher) {
                source.select(snapshot.query, snapshot.folder, snapshot.showTrash, snapshot.sort)
            }
            if (request == filterGeneration) state.value = state.value.copy(items = items)
        }
    }
    fun cancel() {
        pendingRecovery?.password?.fill('\u0000'); pendingRecovery = null
        closePendingPreview()
        val active = busy
        ++generation
        val cancelledJob = job
        val operation = generation
        job?.cancel(); card?.close(); card = null; job = null
        if (active) clearSelection()
        state.value = state.value.copy(can = idleCan(), pin = "", awaitingCard = false, running = false,
            restoredName = null, restoredNameApplied = null,
            verified = if (active) false else state.value.verified,
            message = if (active) "Operación cancelada. Los temporales privados se limpiarán al terminar." else state.value.message)
        if (active && initialized && repository != null) viewModelScope.launch {
            cancelledJob?.join()
            if (generation == operation && !busy) runCatching { refreshInventory() }
        }
    }
    override fun onCleared() { cancel(); clearSelection() }
}
