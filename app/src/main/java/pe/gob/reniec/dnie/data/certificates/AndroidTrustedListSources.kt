package pe.gob.reniec.dnie.data.certificates

import android.content.Context
import pe.gob.reniec.dnie.validation.PeruTrustedListProvider

/** One serialized TSL provider for certificate checks and timestamping; shared rollback state. */
internal object AndroidTrustedListSources {
    private var provider: PeruTrustedListProvider? = null
    @Synchronized fun create(context: Context): PeruTrustedListProvider = provider ?:
        PeruTrustedListProvider(records = AndroidTrustedListRecordStore(context.applicationContext)).also { provider = it }
}
