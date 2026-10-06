package pe.gob.reniec.dnie.ui.diagnostics

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import pe.gob.reniec.dnie.data.diagnostics.*
import pe.gob.reniec.dnie.diagnostics.AppDiagnostics

data class DiagnosticsUiState(val page: DiagnosticPage? = null, val category: String? = null,
    val before: Long? = null, val message: String? = null)
class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(DiagnosticsUiState())
    val state = mutable.asStateFlow()
    private var refresh: Job? = null
    fun start() { if (refresh?.isActive == true) return; refresh = viewModelScope.launch {
        while (isActive) { read(); delay(1000) }
    } }
    fun stop() { refresh?.cancel(); refresh = null }
    private suspend fun read() {
        val request = state.value
        try {
            val page = AppDiagnostics.store.page(request.before, request.category)
            if (request.before == state.value.before && request.category == state.value.category) mutable.value = state.value.copy(page = page)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { mutable.value = state.value.copy(message = "No se pudo leer el historial") }
    }
    fun filter(category: String?) { mutable.value = state.value.copy(category = category, before = null); viewModelScope.launch { read() } }
    fun older() { mutable.value = state.value.copy(before = state.value.page?.rows?.lastOrNull()?.id); viewModelScope.launch { read() } }
    fun newest() { mutable.value = state.value.copy(before = null); viewModelScope.launch { read() } }
    fun clear() { viewModelScope.launch {
        try { AppDiagnostics.store.clear(); mutable.value = state.value.copy(before = null, message = "Historial borrado"); read()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { mutable.value = state.value.copy(message = "No se pudo borrar el historial") }
    } }
    fun export(uri: Uri) { viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")).use { AppDiagnostics.store.export(it) } }
            mutable.value = state.value.copy(message = "Historial completo exportado")
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { mutable.value = state.value.copy(message = "No se pudo exportar el historial") }
    } }
}
