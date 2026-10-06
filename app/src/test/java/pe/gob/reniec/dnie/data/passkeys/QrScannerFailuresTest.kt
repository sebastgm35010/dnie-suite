package pe.gob.reniec.dnie.data.passkeys

import com.google.mlkit.common.MlKitException
import org.junit.Assert.*
import org.junit.Test

class QrScannerFailuresTest {
    @Test fun bothSdkCancellationCodesAreNeutralAndTerminal() {
        listOf(MlKitException.CANCELLED, MlKitException.CODE_SCANNER_CANCELLED).forEach {
            val result = QrScannerFailures.describe(it)
            assertFalse(result.error); assertFalse(result.busy)
            assertTrue(result.message.startsWith("Escaneo cancelado"))
            assertFalse(result.message.contains("No se pudo abrir"))
        }
    }
    @Test fun unavailablePermissionAndOldServicesHaveDistinctActionableMessages() {
        val results = listOf(MlKitException.CODE_SCANNER_UNAVAILABLE, MlKitException.CODE_SCANNER_CAMERA_PERMISSION_NOT_GRANTED,
            MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD).map(QrScannerFailures::describe)
        assertEquals(3, results.map { it.message }.distinct().size)
        assertTrue(results.all { it.error && !it.busy })
    }
    @Test fun processingFailureDoesNotClaimCameraFailedToOpen() {
        assertTrue(QrScannerFailures.describe(MlKitException.CODE_SCANNER_PIPELINE_INFERENCE_ERROR).message.contains("se abrió"))
        assertTrue(QrScannerFailures.describe(null).error)
    }
}
