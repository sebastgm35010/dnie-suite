package pe.gob.reniec.dnie.ui.console

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import pe.gob.reniec.dnie.core.logging.ApduDirection
import pe.gob.reniec.dnie.core.logging.ApduLogEntry
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel
import pe.gob.reniec.dnie.core.nfc.DnieTransceiver

/**
 * ViewModel reactivo para la consola de diagnóstico APDU ISO 7816-4.
 */
class ConsoleViewModel(
    private val apduLogger: ApduLogger = ApduLogger.default,
    var feedbackDispatcher: FeedbackDispatcher? = null,
    private val logDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private var probeJob: kotlinx.coroutines.Job? = null
    val busy: Boolean get() = probeJob?.isActive == true

    private val _uiState = MutableStateFlow(
        ConsoleUiState(entries = apduLogger.getHistory())
    )
    val uiState: StateFlow<ConsoleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(logDispatcher) {
            apduLogger.logStream.collect { newEntry ->
                _uiState.update { current ->
                    current.copy(entries = (current.entries + newEntry).takeLast(ApduLogger.DEFAULT_MAX_HISTORY))
                }

                // Logs from signing/vault also reach this bus. Feedback belongs to
                // the complete foreground operation, never every physical/logical RX.
            }
        }
    }

    fun setFilter(filter: ConsoleFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun toggleAutoScroll() {
        _uiState.update { it.copy(autoScroll = !it.autoScroll) }
    }

    fun clearLogs() {
        apduLogger.clear()
        _uiState.update { it.copy(entries = emptyList()) }
    }

    /**
     * Copia todos los registros visibles en formato texto plano al portapapeles.
     */
    fun copyLogsToClipboard(context: Context): Boolean {
        val currentLogs = _uiState.value.filteredEntries
        if (currentLogs.isEmpty()) return false

        val text = formatLogsAsText(currentLogs)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("DNIe APDU Logs", text)
        clipboard?.setPrimaryClip(clip)
        return true
    }

    /**
     * Comparte el registro completo de APDUs vía Intent.
     */
    fun exportLogs(context: Context) {
        val currentLogs = _uiState.value.filteredEntries
        if (currentLogs.isEmpty()) return

        val text = formatLogsAsText(currentLogs)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Exportar registros APDU")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    /**
     * Formatea una lista de entradas en un informe estructurado.
     */
    fun formatLogsAsText(entries: List<ApduLogEntry>): String {
        val sb = StringBuilder()
        sb.append("=== INFORME DE DIAGNÓSTICO APDU DNIe PERÚ ===\n")
        sb.append("Total de registros: ${entries.size}\n\n")

        for (entry in entries) {
            val sw = if (entry.statusWordHex != null) " [SW: ${entry.statusWordHex}]" else ""
            val duration = if (entry.durationMs != null) " (${entry.durationMs}ms)" else ""
            sb.append("${entry.formattedTime} [${entry.direction}] ${entry.description}$sw$duration\n")
            if (entry.apduHex.isNotEmpty()) {
                sb.append("  ${entry.apduHex}\n")
            }
            sb.append("\n")
        }

        return sb.toString().trimEnd()
    }

    /**
     * Estado de sondeo activo esperando la tarjeta.
     */
    var isProbeActive: Boolean = false
        private set

    /**
     * Inicia el modo de sondeo para analizar todos los AIDs de la tarjeta al aproximarla.
     */
    fun startCardProbe() {
        isProbeActive = true
        _uiState.update { it.copy(isProbing = true) }
        apduLogger.logInfo("Modo Diagnóstico activado: Aproxime su DNIe al sensor NFC para probar todos los AIDs conocidos...")
    }

    /**
     * Cancela el modo de sondeo.
     */
    fun cancelCardProbe() {
        isProbeActive = false
        _uiState.update { it.copy(isProbing = false) }
    }

    /**
     * Proveedor dinámico opcional del código CAN guardado para pruebas PACE.
     */
    var canProvider: (() -> String?)? = null

    /**
     * Ejecuta el sondeo completo de AIDs sobre el canal de tarjeta detectado.
     */
    fun onCardDetected(channel: DnieCardChannel) {
        if (busy) { viewModelScope.launch(Dispatchers.IO) { runCatching { channel.close() } }; return }
        val can = canProvider?.invoke()
        val transceiver = DnieTransceiver(channel, apduLogger, can = can)
        probeJob = viewModelScope.launch {
            try {
                feedbackDispatcher?.playSuccess()
                transceiver.probeAllAids()
                feedbackDispatcher?.playSuccess()
            } catch (e: Exception) {
                feedbackDispatcher?.playError()
                apduLogger.logInfo("Error durante el sondeo: ${e.message}")
            } finally {
                transceiver.close()
                isProbeActive = false
                _uiState.update { it.copy(isProbing = false) }
            }
        }
    }
}
