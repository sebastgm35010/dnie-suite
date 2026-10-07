package pe.gob.reniec.dnie.data.bridge

import android.hardware.usb.UsbAccessory

/** Accessory identity is discovery metadata, never PC or DNI authorization. */
object BridgeUsbIdentity {
    fun matches(accessory: UsbAccessory): Boolean =
        accessory.manufacturer == "DNIeBridge" && accessory.model == "NFC PC Bridge"
}
