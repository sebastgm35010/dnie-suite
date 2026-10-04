package pe.gob.reniec.dnie.ui.console

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.gob.reniec.dnie.core.logging.ApduDirection
import pe.gob.reniec.dnie.core.logging.ApduLogEntry
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel
import pe.gob.reniec.dnie.core.nfc.DnieTransceiver

/**
 * Filtro de dirección para la consola de tramas APDU.
 */
enum class ConsoleFilter {
    ALL,
    TX,
    RX
}

/**
 * Estado inmutable de la consola de diagnóstico APDU.
 */
data class ConsoleUiState(
    val entries: List<ApduLogEntry> = emptyList(),
    val filter: ConsoleFilter = ConsoleFilter.ALL,
    val autoScroll: Boolean = true,
    val isProbing: Boolean = false
) {
    val filteredEntries: List<ApduLogEntry>
        get() = when (filter) {
            ConsoleFilter.ALL -> entries
            ConsoleFilter.TX -> entries.filter { it.direction == ApduDirection.TX }
            ConsoleFilter.RX -> entries.filter { it.direction == ApduDirection.RX }
        }
}

/**
 * Contrato para feedback sonoro y háptico desacoplado.
 */
interface FeedbackDispatcher {
    fun playSuccess()
    fun playError()
}

/**
 * Implementación de feedback háptico y sonoro para Android.
 */
class AndroidFeedbackDispatcher(private val context: Context) : FeedbackDispatcher {
    private var toneGen: ToneGenerator? = null

    init {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (_: Exception) {}
    }

    override fun playSuccess() {
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        } catch (_: Exception) {}

        try {
            val vibrator = getVibrator()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
            }
        } catch (_: Exception) {}
    }

    override fun playError() {
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 250)
        } catch (_: Exception) {}

        try {
            val vibrator = getVibrator()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 100, 80, 150),
                        intArrayOf(0, 255, 0, 255),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 100, 80, 150), -1)
            }
        } catch (_: Exception) {}
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}

/**
 * ViewModel reactivo para la consola de diagnóstico APDU ISO 7816-4.
 */
class ConsoleViewModel(
    private val apduLogger: ApduLogger = ApduLogger.default,
    var feedbackDispatcher: FeedbackDispatcher? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ConsoleUiState(entries = apduLogger.getHistory())
    )
    val uiState: StateFlow<ConsoleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            apduLogger.logStream.collect { newEntry ->
                _uiState.update { current ->
                    current.copy(entries = current.entries + newEntry)
                }

                if (newEntry.direction == ApduDirection.RX) {
                    val sw = newEntry.statusWordHex?.uppercase() ?: ""
                    if (sw == "9000" || sw.startsWith("90 00") || newEntry.isSuccess) {
                        feedbackDispatcher?.playSuccess()
                    } else if (sw.startsWith("6983") || sw.startsWith("69 83") || sw.startsWith("63C") || sw.startsWith("63 C") || !newEntry.isSuccess) {
                        feedbackDispatcher?.playError()
                    }
                }
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
     * Ejecuta el sondeo completo de AIDs sobre el canal de tarjeta detectado.
     */
    fun onCardDetected(channel: DnieCardChannel) {
        val transceiver = DnieTransceiver(channel, apduLogger)
        viewModelScope.launch {
            try {
                feedbackDispatcher?.playSuccess()
                transceiver.probeAllAids()
            } catch (e: Exception) {
                feedbackDispatcher?.playError()
                apduLogger.logInfo("Error durante el sondeo: ${e.message}")
            } finally {
                isProbeActive = false
                _uiState.update { it.copy(isProbing = false) }
            }
        }
    }
}
