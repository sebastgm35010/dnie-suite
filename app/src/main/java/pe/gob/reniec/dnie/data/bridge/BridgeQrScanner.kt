package pe.gob.reniec.dnie.data.bridge

import android.app.Activity
import pe.gob.reniec.dnie.data.qr.AndroidQrScanner

/** Pairing callers retain their own parser; stamp QR never enters pairing automatically. */
class BridgeQrScanner(activity: Activity) {
    private val scanner=AndroidQrScanner(activity)
    fun scan(onPayload: (String) -> Unit, onStatus: (String) -> Unit) = scanner.scan(onPayload,onStatus)
}
