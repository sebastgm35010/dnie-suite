package pe.gob.reniec.dnie.data.suite

import android.content.Context
import android.net.Uri
import android.nfc.Tag
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.core.nfc.DnieTransceiver
import pe.gob.reniec.dnie.core.nfc.IsoDepCardChannel

/** Android storage/NFC APIs; caller runs blocking storage/card operations on IO. */
class RemoteSigningAndroidAdapter(private val context: Context) {
    fun read(uri: Uri): String = context.contentResolver.openInputStream(uri)!!.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1025)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            require(output.size() <= 1024)
        }
        output.toString("UTF-8")
    }
    fun card(tag: Tag, can: String): DnieTransceiver? = IsoDepCardChannel.fromTag(tag)?.let {
        // Only coarse diagnostics for this route; no APDU/certificate byte history.
        DnieTransceiver(it, logger = ApduLogger(enabled = false), can = can)
    }
}
