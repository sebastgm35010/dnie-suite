package pe.gob.reniec.dnie.data.passkeys

import com.google.mlkit.common.MlKitException

data class QrScannerFeedback(val message: String, val error: Boolean = false, val busy: Boolean = false)

/** Code mapping only; SDK exceptions/messages and scanned payloads never enter presentation. */
object QrScannerFailures {
    fun describe(code: Int?): QrScannerFeedback = when (code) {
        MlKitException.CANCELLED, MlKitException.CODE_SCANNER_CANCELLED -> QrScannerFeedback("Escaneo cancelado. No se leyó un QR ni se inició una autenticación.")
        MlKitException.CODE_SCANNER_UNAVAILABLE -> QrScannerFeedback("El módulo del lector aún no está disponible. Conéctate a Internet y vuelve a intentarlo.", error = true)
        MlKitException.CODE_SCANNER_CAMERA_PERMISSION_NOT_GRANTED -> QrScannerFeedback("Google Play Services necesita permiso de cámara para abrir su lector.", error = true)
        MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD -> QrScannerFeedback("Actualiza Google Play Services para usar el lector QR.", error = true)
        MlKitException.CODE_SCANNER_TASK_IN_PROGRESS -> QrScannerFeedback("Ya hay un escaneo en curso. Cierra el lector antes de volver a abrirlo.", error = true)
        MlKitException.CODE_SCANNER_PIPELINE_INITIALIZATION_ERROR, MlKitException.CODE_SCANNER_PIPELINE_INFERENCE_ERROR ->
            QrScannerFeedback("El lector se abrió pero no pudo procesar un código. Vuelve a intentarlo o usa la cámara del teléfono.", error = true)
        else -> QrScannerFeedback("El escaneo no terminó correctamente. Puedes volver a intentarlo o usar la cámara del teléfono.", error = true)
    }
}
