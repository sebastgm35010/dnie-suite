package pe.gob.reniec.dnie.ui.bridge

import android.app.Activity
import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.core.content.IntentCompat
import pe.gob.reniec.dnie.data.bridge.BridgeUsbIdentity

/** Narrow OS discovery entry: opens UI only, without arming NFC or authorizing a PC. */
class BridgeUsbAttachActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(USB_SERVICE) as UsbManager
        val attached = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
        if (intent.action == UsbManager.ACTION_USB_ACCESSORY_ATTACHED && attached != null &&
            BridgeUsbIdentity.matches(attached) && manager.accessoryList?.any { it == attached } == true) {
            startActivity(Intent(this, BridgeActivity::class.java)
                .putExtra("usb_attached", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        finish()
    }
}
