package pe.gob.reniec.dnie.validation

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** Restricted XML dateTime profile, with mandatory timezone; no optional Xerces provider on Android. */
internal fun parseDate(text: String): Date {
    val match = Regex("([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2})(?:\\.([0-9]{1,9}))?(Z|[+-][0-9]{2}:[0-9]{2})")
        .matchEntire(text.trim()) ?: throw IllegalArgumentException("Fecha fuera del perfil admitido")
    val zone = match.groupValues[3]
    if (zone != "Z") {
        val hours = zone.substring(1, 3).toInt(); val minutes = zone.substring(4, 6).toInt()
        require(hours <= 14 && minutes <= 59 && (hours < 14 || minutes == 0))
    }
    val fraction = match.groupValues[2].padEnd(3, '0').take(3)
    val normalized = match.groupValues[1] + "." + fraction + if (zone == "Z") "+0000" else zone.replace(":", "")
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT).apply {
        calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
            gregorianChange = Date(Long.MIN_VALUE)
        }
        isLenient = false
    }
    val position = ParsePosition(0)
    return requireNotNull(formatter.parse(normalized, position)).also { require(position.index == normalized.length) }
}
