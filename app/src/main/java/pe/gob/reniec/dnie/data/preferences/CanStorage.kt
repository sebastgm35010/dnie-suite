package pe.gob.reniec.dnie.data.preferences

import android.content.Context

/**
 * Gestor de persistencia privada local para el CAN (Card Access Number).
 * Permite recordar el CAN del DNIe 3.0 en SharedPreferences para agilizar lecturas posteriores.
 */
object CanStorage {
    private const val PREFS_NAME = "pe.gob.reniec.dnie.prefs"
    private const val KEY_SAVED_CAN = "saved_can"

    suspend fun loadSavedCan(context: Context): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        getSavedCan(context)
    }

    fun getSavedCan(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_SAVED_CAN, "") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun saveCan(context: Context, can: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SAVED_CAN, can.trim()).apply()
        } catch (_: Exception) {}
    }

    fun clearCan(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_SAVED_CAN).apply()
        } catch (_: Exception) {}
    }
}
