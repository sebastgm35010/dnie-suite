package pe.gob.reniec.dnie.core.logging

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dirección del tráfico APDU.
 */
enum class ApduDirection {
    TX, // Comando transmitido a la tarjeta
    RX, // Respuesta recibida de la tarjeta
    INFO // Evento informativo del transceptor
}

/**
 * Registro estructurado para el flujo de diagnóstico de tráfico APDU en tiempo real.
 */
data class ApduLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val direction: ApduDirection,
    val apduHex: String,
    val statusWordHex: String? = null,
    val description: String,
    val durationMs: Long? = null,
    val isSuccess: Boolean = true
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
            return sdf.format(Date(timestamp))
        }

    override fun toString(): String {
        val durationStr = if (durationMs != null) " (${durationMs}ms)" else ""
        val swStr = if (statusWordHex != null) " [SW: $statusWordHex]" else ""
        return "[$formattedTime] [$direction] $description$swStr$durationStr\n  $apduHex"
    }
}
