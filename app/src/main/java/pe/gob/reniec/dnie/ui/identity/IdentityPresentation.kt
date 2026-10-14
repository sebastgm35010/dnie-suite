package pe.gob.reniec.dnie.ui.identity

import pe.gob.reniec.dnie.icao.*
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** MRZ contains two-digit years: do not invent a century when several plausible dates remain. */
internal object IdentityPresentation {
    fun date(value: String, birth: Boolean, today: Date = Date()): String {
        if (value.length != 6 || value.any { it !in '0'..'9' }) return value
        val year = value.take(2).toInt(); val month = value.substring(2, 4).toInt(); val day = value.takeLast(2).toInt()
        val now = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { time = today }
        val currentYear = now.get(Calendar.YEAR)
        val candidates = listOf(1900, 2000, 2100).mapNotNull { century ->
            runCatching { GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
                clear(); isLenient = false; set(century + year, month - 1, day); timeInMillis
            } }.getOrNull()
        }.filter { candidate ->
            val y = candidate.get(Calendar.YEAR)
            if (birth) candidate.time <= today && y >= currentYear - 120 else y in (currentYear - 49)..(currentYear + 50)
        }
        return if (candidates.size == 1) String.format(Locale.ROOT, "%02d/%02d/%04d", day, month, candidates.single().get(Calendar.YEAR))
            else if (candidates.isEmpty()) value else String.format(Locale.ROOT, "%02d/%02d/%02d (siglo no indicado)", day, month, year)
    }
    fun mrzVerified(document: IcaoDocument): Boolean {
        val block = document.blocks[IcaoFile.DG1] ?: return false
        val identity = block.content as? IcaoContent.Identity ?: return false
        return block.status == BlockStatus.AVAILABLE && block.hashStatus != HashStatus.MISMATCH && identity.checkDigits.isNotEmpty() && identity.checkDigits.values.all { it }
    }
    fun sodSignatureVerified(document: IcaoDocument): Boolean {
        val block = document.blocks[IcaoFile.SOD] ?: return false
        return block.status == BlockStatus.AVAILABLE && (block.content as? IcaoContent.Security)?.signatureValid == true
    }
    fun markUsable(document: IcaoDocument): Boolean {
        val block = document.blocks[IcaoFile.DG7] ?: return false
        return block.status == BlockStatus.AVAILABLE && block.hashStatus != HashStatus.MISMATCH && (block.content as? IcaoContent.Images)?.images?.isNotEmpty() == true
    }
}
