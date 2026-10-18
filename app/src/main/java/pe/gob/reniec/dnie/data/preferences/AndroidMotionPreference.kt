package pe.gob.reniec.dnie.data.preferences

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/** Read-only system adapter, one observer shared by visible screens. Never changes Android settings. */
class AndroidMotionPreference(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialReduced = if (android.os.Build.VERSION.SDK_INT >= 26) {
        !android.animation.ValueAnimator.areAnimatorsEnabled()
    } else false
    val reduced: StateFlow<Boolean> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate().map {
        runCatching { val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            !scale.isFinite() || scale <= 0f }.getOrDefault(initialReduced)
    }.distinctUntilChanged().flowOn(Dispatchers.IO).stateIn(scope, SharingStarted.WhileSubscribed(5000), initialReduced)
}
