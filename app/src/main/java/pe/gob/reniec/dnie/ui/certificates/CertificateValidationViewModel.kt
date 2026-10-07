package pe.gob.reniec.dnie.ui.certificates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.gob.reniec.dnie.validation.*
import pe.gob.reniec.dnie.data.certificates.*

data class CertificateValidationUiState(val fingerprint: String = "", val checking: Boolean = false,
    val report: CertificateValidationReport? = null, val error: String? = null)

class CertificateValidationViewModel(private val repository: CertificateValidationRepository = OnlineCertificateValidationRepository()) : ViewModel() {
    private val mutable = MutableStateFlow(CertificateValidationUiState())
    val state = mutable.asStateFlow()
    private var task: Job? = null
    private var generation = 0
    fun clear() { generation++; task?.cancel(); mutable.value = CertificateValidationUiState() }
    fun check(der: ByteArray, fingerprint: String, forSignature: Boolean) {
        clear(); val current = generation
        mutable.value = CertificateValidationUiState(fingerprint, checking = true)
        val copy = der.clone()
        task = viewModelScope.launch {
            try {
                val report = repository.inspect(copy, forSignature)
                if (current == generation) mutable.value = CertificateValidationUiState(fingerprint, report = report)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (current == generation) mutable.value = CertificateValidationUiState(fingerprint,
                    error = "No se pudo interpretar o comprobar el certificado")
            }
        }
    }
}
