package pe.gob.reniec.dnie.data.preferences

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.domain.vault.RememberedCanStore

/** Same preference/key as the main credentials dialog. No duplicate vault credential storage. */
class AndroidRememberedCanStore(context: Context) : RememberedCanStore {
    private val app = context.applicationContext
    override suspend fun read(): String = withContext(Dispatchers.IO) {
        CanStorage.getSavedCan(app).takeIf { it.length == 6 && it.all { digit -> digit in '0'..'9' } }.orEmpty()
    }
    override suspend fun save(can: String): Unit = withContext(Dispatchers.IO) { CanStorage.saveCan(app, can) }
    override suspend fun clear(): Unit = withContext(Dispatchers.IO) { CanStorage.clearCan(app) }
}
