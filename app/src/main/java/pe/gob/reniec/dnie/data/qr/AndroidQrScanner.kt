package pe.gob.reniec.dnie.data.qr

import android.app.Activity
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Scanner transport only. No logs, browser handoff or persistence of QR data. */
class AndroidQrScanner(private val activity: Activity) {
    private var scanning = false
    fun scan(onPayload: (String) -> Unit, onStatus: (String) -> Unit) {
        if (scanning) return
        scanning = true
        onStatus("Abriendo lector QR…")
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
            GmsBarcodeScanning.getClient(activity, options).startScan()
                .addOnSuccessListener { barcode ->
                    scanning = false
                    if (!activity.isDestroyed && !activity.isFinishing) {
                        val text = barcode.rawValue
                        if (text == null) onStatus("No se pudo leer el QR.") else onPayload(text)
                    }
                }
                .addOnCanceledListener { scanning = false; onStatus("Escaneo cancelado.") }
                .addOnFailureListener {
                    scanning = false
                    onStatus("No se pudo abrir el lector. Comprueba Google Play Services o usa la entrada manual.")
                }
        } catch (_: Exception) {
            scanning = false
            onStatus("Lector QR no disponible. Puedes usar la entrada manual.")
        }
    }
}
