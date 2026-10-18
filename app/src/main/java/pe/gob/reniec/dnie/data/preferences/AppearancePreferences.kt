package pe.gob.reniec.dnie.data.preferences

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ThemeMode(val label: String) { SYSTEM("Del sistema"), LIGHT("Claro"), DARK("Oscuro") }

/** Application-scoped appearance adapter. Disk reads and ordered writes never run in composition. */
class AppearancePreferences(context: Context) {
    private val app = context.applicationContext
    private val selected = MutableStateFlow<ThemeMode?>(ThemeMode.SYSTEM)
    val mode = selected.asStateFlow()
    private val dismissed = MutableStateFlow(false)
    val onboardingDismissed = dismissed.asStateFlow()
    private val dismissal = Channel<Unit>(Channel.CONFLATED)
    private val tactile=MutableStateFlow(true)
    val hapticsEnabled=tactile.asStateFlow()
    private val tactileChanges=Channel<Boolean>(Channel.CONFLATED)
    private val changes = Channel<ThemeMode>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        scope.launch {
            val prefs = app.getSharedPreferences("appearance", Context.MODE_PRIVATE)
            selected.value = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }
                .getOrDefault(ThemeMode.SYSTEM)
            val wasDismissed = prefs.getBoolean("onboarding_dismissed", false)
            dismissed.update { it || wasDismissed }
            launch { for (ignored in dismissal) prefs.edit().putBoolean("onboarding_dismissed", true).apply() }
            tactile.value=prefs.getBoolean("haptics",true)
            pe.gob.reniec.dnie.data.feedback.ReadingFeedback.enabled=tactile.value
            launch {for(value in tactileChanges) {
                prefs.edit().putBoolean("haptics",value).apply();tactile.value=value
                pe.gob.reniec.dnie.data.feedback.ReadingFeedback.enabled=value
            }}
            for (value in changes) {
                prefs.edit().putString("theme", value.name).apply()
                selected.value = value
            }
        }
    }
    fun dismissOnboarding() { dismissed.value = true; dismissal.trySend(Unit) }
    fun setHaptics(value: Boolean) { tactileChanges.trySend(value) }
    fun select(mode: ThemeMode) { changes.trySend(mode) }
}
