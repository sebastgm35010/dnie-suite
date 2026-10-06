package pe.gob.reniec.dnie.ui.console

import pe.gob.reniec.dnie.core.logging.ApduDirection
import pe.gob.reniec.dnie.core.logging.ApduLogEntry

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

