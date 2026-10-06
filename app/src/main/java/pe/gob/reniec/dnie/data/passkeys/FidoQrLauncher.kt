package pe.gob.reniec.dnie.data.passkeys

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import pe.gob.reniec.dnie.passkeys.FidoQrPayload
import pe.gob.reniec.dnie.diagnostics.DiagnosticLog
import com.google.mlkit.common.MlKitException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

/** No QR secrets are logged, persisted or forwarded to a browser/search engine. */
class FidoQrLauncher(private val activity: Activity) {
    private var scanning = false
    fun scan(status: (QrScannerFeedback) -> Unit) {
        if (scanning) return
        val flow = java.util.UUID.randomUUID().toString()
        val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
        if (availability != ConnectionResult.SUCCESS) {
            DiagnosticLog.event("qr", "services_unavailable", "flow" to flow, "code" to availability)
            status(QrScannerFeedback("Actualiza o habilita Google Play Services para usar el lector QR.", error = true)); return
        }
        scanning = true
        status(QrScannerFeedback("Abriendo el lector de Google…", busy = true))
        DiagnosticLog.event("qr", "scan_requested", "flow" to flow)
        fun finish(event: String, feedback: QrScannerFeedback, code: Int? = null) {
            scanning = false
            DiagnosticLog.event("qr", event, "flow" to flow, "code" to code)
            if (!activity.isDestroyed && !activity.isFinishing) status(feedback)
        }
        fun failed(failure: Exception) {
            val code = when (failure) {
                is MlKitException -> failure.errorCode
                is ApiException -> if (failure.statusCode == CommonStatusCodes.CANCELED) MlKitException.CANCELLED else null
                else -> null
            }
            val feedback = QrScannerFailures.describe(code)
            DiagnosticLog.event("qr", "scanner_result", "flow" to flow, "code" to code, "exception" to failure.javaClass.simpleName)
            finish(if (feedback.error) "scan_failed" else "scan_cancelled", feedback, code)
        }
        try {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
        GmsBarcodeScanning.getClient(activity, options).startScan()
            .addOnSuccessListener { barcode ->
                if (activity.isDestroyed || activity.isFinishing) {
                    finish("scan_discarded", QrScannerFeedback("Escaneo descartado al cerrar la pantalla.")); return@addOnSuccessListener
                }
                val payload = try { FidoQrPayload.validate(requireNotNull(barcode.rawValue)) }
                    catch (_: Exception) { finish("payload_rejected", QrScannerFeedback("El código leído no es un QR FIDO de ‘usar otro dispositivo’.", error = true)); return@addOnSuccessListener }
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payload)).setPackage("com.google.android.gms")
                    if (intent.resolveActivity(activity.packageManager) == null) {
                        finish("handler_unavailable", QrScannerFeedback("Android no ofrece un receptor para este QR FIDO. No se inició una autenticación.", error = true))
                    } else {
                        activity.startActivity(intent)
                        finish("handoff_started", QrScannerFeedback("QR enviado al autenticador de Android. Comprueba el resultado en el navegador; el sistema decide qué proveedores admite."))
                    }
                } catch (_: Exception) { finish("handoff_failed", QrScannerFeedback("El código se leyó, pero Android no pudo abrir el autenticador FIDO.", error = true)) }
            }
            .addOnCanceledListener { finish("scan_cancelled", QrScannerFailures.describe(MlKitException.CANCELLED)) }
            .addOnFailureListener(::failed)
        } catch (failure: Exception) { failed(failure) }
    }
    fun camera(status: (QrScannerFeedback) -> Unit) {
        try {
            activity.startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            DiagnosticLog.event("qr", "system_camera_started")
            status(QrScannerFeedback("Cámara del teléfono abierta. Usa su lector QR o Lens si ofrece esa opción. El resultado lo gestiona esa app."))
        } catch (_: Exception) { status(QrScannerFeedback("No se pudo abrir la cámara del teléfono.", error = true)) }
    }
}
