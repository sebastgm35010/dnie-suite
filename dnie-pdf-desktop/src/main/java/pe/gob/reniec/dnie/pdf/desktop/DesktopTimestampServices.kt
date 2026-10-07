package pe.gob.reniec.dnie.pdf.desktop

import java.util.prefs.Preferences
import pe.gob.reniec.dnie.timestamp.*
import pe.gob.reniec.dnie.validation.*

/** Process-wide trust provider; stores public anti-rollback metadata in the user's preferences. */
internal object DesktopTimestampServices {
    val service: CmsTimestampService by lazy {
        ReniecCmsTimestampService(validator = TimestampTokenValidator(
            ReniecTimestampTrust(PeruTrustedListProvider(records = DesktopTrustedListRecords()))))
    }
}

internal class DesktopTrustedListRecords(private val preferences: Preferences =
    Preferences.userRoot().node("pe/gob/reniec/dnie/suite-trust")) : TrustedListRecordStore {
    @Synchronized override fun read(): TrustedListRecord? {
        preferences.sync()
        val value = preferences.get("tsl-record", null) ?: return null
        val fields = value.split(':')
        require(fields.size == 2 && fields[1].matches(Regex("[a-f0-9]{64}"))) { "Estado TSL inválido" }
        return TrustedListRecord(fields[0].toInt().also { require(it >= 25) }, fields[1])
    }
    @Synchronized override fun save(record: TrustedListRecord) {
        require(record.sequence >= 25 && record.sha256.matches(Regex("[a-f0-9]{64}")))
        read()?.let { previous ->
            require(record.sequence >= previous.sequence &&
                (record.sequence != previous.sequence || record.sha256 == previous.sha256))
        }
        preferences.put("tsl-record", "${record.sequence}:${record.sha256}")
        preferences.flush()
    }
}
