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
    private var pending: (() -> Unit)? = null
    private val action=context.packageName+".BRIDGE_USB_PERMISSION"
    private val receiver=object: BroadcastReceiver() {
        override fun onReceive(c: Context,intent: Intent) {
            if(intent.action!=action)return
            val callback=pending;pending=null
            if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false))callback?.invoke()
        }
    }
    init {ContextCompat.registerReceiver(context,receiver,IntentFilter(action),ContextCompat.RECEIVER_NOT_EXPORTED)}
    fun authorize(onReady: () -> Unit,onError: (String) -> Unit) {
        val manager=context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device=manager.accessoryList?.singleOrNull()
        if(device==null || device.manufacturer!="DNIeBridge" || device.model!="NFC PC Bridge") {onError("Inicia USB directo en Windows y conecta el cable de datos.");return}
        if(manager.hasPermission(device))onReady()
        else {
            pending=onReady
            manager.requestPermission(device,PendingIntent.getBroadcast(context,0,Intent(action).setPackage(context.packageName),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
    }
    override fun close(){pending=null;context.unregisterReceiver(receiver)}
}
