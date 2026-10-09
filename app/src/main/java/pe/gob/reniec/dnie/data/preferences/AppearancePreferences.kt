package pe.gob.reniec.dnie.data.preferences

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("Del sistema"), LIGHT("Claro"), DARK("Oscuro") }

/** Application-scoped appearance adapter. Disk reads and ordered writes never run in composition. */
class AppearancePreferences(context: Context) {
    private val app = context.applicationContext
    private val selected = MutableStateFlow<ThemeMode?>(null)
    val mode = selected.asStateFlow()
    private val changes = Channel<ThemeMode>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        scope.launch {
            val prefs = app.getSharedPreferences("appearance", Context.MODE_PRIVATE)
            selected.value = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }
                .getOrDefault(ThemeMode.SYSTEM)
            for (value in changes) {
                prefs.edit().putString("theme", value.name).apply()
                selected.value = value
            }
        }
    }
    fun select(mode: ThemeMode) { changes.trySend(mode) }
}
