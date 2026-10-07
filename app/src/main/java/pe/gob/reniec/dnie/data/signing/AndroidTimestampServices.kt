package pe.gob.reniec.dnie.data.signing

import android.content.Context
import pe.gob.reniec.dnie.data.certificates.AndroidTrustedListSources
import pe.gob.reniec.dnie.timestamp.*

/** Persistent TSL rollback state and network adapters live outside Compose/ViewModels. */
internal object AndroidTimestampServices {
    private var service: CmsTimestampService? = null
    @Synchronized fun create(context: Context): CmsTimestampService = service ?: ReniecCmsTimestampService(
        validator = TimestampTokenValidator(ReniecTimestampTrust(
            lists = AndroidTrustedListSources.create(context)
        ))
    ).also { service = it }
}
