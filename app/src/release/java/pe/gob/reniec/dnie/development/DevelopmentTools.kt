package pe.gob.reniec.dnie.development
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel
import pe.gob.reniec.dnie.ui.navigation.Screen
// Release adapter has no probe, export, recorder, screen or development activity.
class DevelopmentController : ViewModel() {
    val busy = false
    val isProbeActive = false
    var canProvider: (() -> String?)? = null
    fun onCardDetected(channel: DnieCardChannel) { channel.close() }
}
fun developmentDestinations(): List<Screen> = emptyList()
fun defaultTailnetHost() = ""
@Composable fun DevelopmentScreen(controller: DevelopmentController) {}
@Composable fun DevelopmentEntry() {}

fun cancelDevelopment(controller: DevelopmentController) { Unit }
