package pe.gob.reniec.dnie.data.bridge
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import java.io.Closeable

/** Explicit Android USB consent; independent of the NFC/session coordinator. */
class BridgeUsbPermission(private val context: Context) : Closeable {
    private data class Request(val accessory: android.hardware.usb.UsbAccessory,
        val onReady: () -> Unit, val onError: (String) -> Unit)
    private var pending: Request? = null
    private val action=context.packageName+".BRIDGE_USB_PERMISSION"
    private val receiver=object: BroadcastReceiver() {
        override fun onReceive(c: Context,intent: Intent) {
            if(intent.action!=action)return
            val request = pending ?: return
            pending = null
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            // The immutable PendingIntent does not need filled-in extras. The USB
            // service's actual permission and current device are authoritative.
            if (manager.accessoryList?.any { it == request.accessory } == true &&
                manager.hasPermission(request.accessory)) request.onReady()
            else request.onError("Permiso USB denegado o accesorio desconectado. Vuelve a autorizarlo.")
        }
    }
    init {ContextCompat.registerReceiver(context,receiver,IntentFilter(action),ContextCompat.RECEIVER_NOT_EXPORTED)}
    fun authorize(onReady: () -> Unit,onError: (String) -> Unit) {
        val manager=context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device=manager.accessoryList?.singleOrNull()
        if(device==null || !BridgeUsbIdentity.matches(device)) {onError("Inicia USB directo en Windows y conecta el cable de datos.");return}
        if(pending!=null) {onError("Hay una solicitud de permiso USB pendiente.");return}
        if(manager.hasPermission(device))onReady()
        else {
            pending=Request(device,onReady,onError)
            try {
                manager.requestPermission(device,PendingIntent.getBroadcast(context,0,Intent(action).setPackage(context.packageName),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            } catch (error: Exception) {
                pending=null
                onError("No se pudo solicitar el permiso USB. Reconecta el accesorio y vuelve a intentarlo.")
            }
        }
    }
    override fun close(){pending=null;context.unregisterReceiver(receiver)}
}
