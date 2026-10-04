package pe.gob.reniec.dnie.core.logging

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import java.util.Collections

/**
 * Bus de eventos de diagnóstico en tiempo real para tráfico APDU del DNIe.
 *
 * Mantiene un búfer circular en memoria para consultas históricas y expone un [SharedFlow]
 * reactivo con replay para la UI de consola de diagnóstico (Milestone 4).
 */
class ApduLogger(
    private val maxHistorySize: Int = DEFAULT_MAX_HISTORY
) {
    private val _events = MutableSharedFlow<ApduLogEntry>(
        replay = 50,
        extraBufferCapacity = 200,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ApduLogEntry> = _events.asSharedFlow()
    val logStream: SharedFlow<ApduLogEntry> get() = events

    private val historyList = Collections.synchronizedList(ArrayList<ApduLogEntry>())

    fun log(entry: ApduLogEntry) {
        synchronized(historyList) {
            if (historyList.size >= maxHistorySize) {
                historyList.removeAt(0)
            }
            historyList.add(entry)
        }
        _events.tryEmit(entry)
    }

    fun logTx(command: CommandApdu, description: String) {
        val entry = ApduLogEntry(
            direction = ApduDirection.TX,
            apduHex = command.toHexString(),
            description = description,
            isSuccess = true
        )
        log(entry)
    }

    fun logRx(
        response: ResponseApdu,
        durationMs: Long? = null,
        description: String = response.statusWord.description
    ) {
        val entry = ApduLogEntry(
            direction = ApduDirection.RX,
            apduHex = response.toHexString(),
            statusWordHex = response.statusWordHex,
            description = description,
            durationMs = durationMs,
            isSuccess = response.isSuccess || response.isWarning
        )
        log(entry)
    }

    fun logInfo(message: String) {
        val entry = ApduLogEntry(
            direction = ApduDirection.INFO,
            apduHex = "",
            description = message,
            isSuccess = true
        )
        log(entry)
    }

    fun getHistory(): List<ApduLogEntry> {
        synchronized(historyList) {
            return ArrayList(historyList)
        }
    }

    fun clear() {
        synchronized(historyList) {
            historyList.clear()
        }
    }

    companion object {
        const val DEFAULT_MAX_HISTORY = 1000

        /**
         * Instancia compartida por defecto para la aplicación.
         */
        val default = ApduLogger()
    }
}
