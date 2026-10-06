package pe.gob.reniec.dnie.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.gob.reniec.dnie.icao.BlockStatus
import pe.gob.reniec.dnie.icao.IcaoDocument
import pe.gob.reniec.dnie.icao.IcaoReader

data class IdentityUiState(
    val document: IcaoDocument? = null,
    val enteringCan: Boolean = false,
    val awaitingCard: Boolean = false,
    val reading: Boolean = false,
    val message: String? = null
)

class IdentityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(IdentityUiState())
    val uiState = _uiState.asStateFlow()
    private var enteredCan: String? = null
    val requestedCan: String? get() = if (_uiState.value.awaitingCard) enteredCan else null
    private var job: Job? = null
    private var reader: IcaoReader? = null
    private var generation = 0

    fun requestReading() {
        cancelReading()
        _uiState.value = _uiState.value.copy(enteringCan = true, message = null)
    }

    fun confirmCan(can: String) {
        if (can.length != 6 || can.any { it !in '0'..'9' }) return
        enteredCan = can
        _uiState.value = _uiState.value.copy(enteringCan = false, awaitingCard = true, message = null)
    }

    fun cancelCan() { _uiState.value = _uiState.value.copy(enteringCan = false) }

    fun onCardDetected(newReader: IcaoReader) {
        if (!_uiState.value.awaitingCard || job?.isActive == true) {
            newReader.close()
            return
        }
        val operation = ++generation
        reader = newReader
        _uiState.value = IdentityUiState(document = IcaoDocument(), reading = true, message = "Abriendo canal seguro ICAO…")
        job = viewModelScope.launch {
            try {
                val result = newReader.read { document ->
                    if (operation == generation) {
                        val current = document.blocks.values.firstOrNull { it.status == BlockStatus.READING }
                        _uiState.value = IdentityUiState(document = document, reading = true,
                            message = current?.let { "Leyendo ${it.file.title.lowercase()}… Mantenga el DNI inmóvil." }
                                ?: "Abriendo canal seguro ICAO…")
                    }
                }
                if (operation == generation) _uiState.value = IdentityUiState(document = result, message = result.sessionMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.failure("identity", "read", e)
                if (operation == generation) _uiState.value = _uiState.value.copy(reading = false, message = e.message)
            } finally {
                newReader.close()
                if (operation == generation) {
                    reader = null
                    enteredCan = null
                }
            }
        }
    }

    fun cancelReading() {
        val wasReading = _uiState.value.reading
        ++generation
        job?.cancel()
        reader?.close()
        reader = null
        job = null
        enteredCan = null
        val retained = _uiState.value.document?.let { d ->
            if (!wasReading) d else d.copy(completed = true, sessionMessage = "Lectura cancelada; se conservan los bloques completos.",
                blocks = d.blocks.mapValues { (_, b) ->
                    if (b.status in setOf(BlockStatus.PENDING, BlockStatus.READING)) b.copy(status = BlockStatus.INTERRUPTED) else b
                })
        }
        _uiState.value = IdentityUiState(document = retained, message = retained?.sessionMessage)
    }

    fun clear() {
        cancelReading()
        _uiState.value = IdentityUiState()
    }

    override fun onCleared() { cancelReading() }
}
