package pe.gob.reniec.dnie.data.preferences

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import pe.gob.reniec.dnie.pdf.*

/** Application scoped, ordered IO. Public preferences only; never stores an ICAO image or certificate. */
class AndroidStampPreferences(context: Context) {
    private val app=context.applicationContext
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val changes=Channel<String>(Channel.CONFLATED)
    @Volatile private var latest: String?=null
    private val preferences by lazy {app.getSharedPreferences("dnie_stamp_prefs",Context.MODE_PRIVATE)}
    init {scope.launch {for(design in changes) runCatching {preferences.edit().putString("design",design).apply()}}}
    suspend fun load(): PdfSigningOptions=withContext(Dispatchers.IO) {StampDesignCodec.decode(latest?:preferences.getString("design","").orEmpty())}
    fun save(options: PdfSigningOptions) {val design=StampDesignCodec.encode(options);latest=design;changes.trySend(design)}
}
