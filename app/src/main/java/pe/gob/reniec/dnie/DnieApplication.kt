package pe.gob.reniec.dnie

import android.app.Application
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer

/**
 * Clase principal de aplicación Android para DNIe Signer Perú.
 *
 * Responsable de la inicialización temprana del entorno criptográfico (BouncyCastle Provider)
 * y de los recursos de fuentes del motor PDF (PDFBox Android) antes de que cualquier
 * actividad o componente de fondo intente procesar firmas o manipular documentos.
 */
class DnieApplication : Application() {

    companion object {
        private const val TAG = "DnieApplication"

        @Volatile
        private var instance: DnieApplication? = null

        val appInstance: DnieApplication
            get() = instance ?: throw IllegalStateException("DnieApplication no ha sido inicializada")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        pe.gob.reniec.dnie.core.logging.ApduLogger.configureDefault(BuildConfig.DEBUG)
        pe.gob.reniec.dnie.diagnostics.AppDiagnostics.initialize(this)
        pe.gob.reniec.dnie.data.vault.VaultPreviewFiles(this).clear()
        // Remove plaintext left by process death before any vault screen is opened.
        java.io.File(cacheDir, "vault-private").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".plain") }?.forEach { it.delete() }

        try {
            // Inicializar cargador de recursos y fuentes de PDFBox Android
            PDFBoxResourceLoader.init(applicationContext)
            Log.d(TAG, "PDFBox Android inicializado correctamente.")
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando PDFBox Android: ${e.message}", e)
        }

        try {
            // Inicializar proveedor BouncyCastle en posición 1
            CryptoProviderInitializer.init()
            Log.d(TAG, "Proveedor BouncyCastle registrado en posicion 1.")
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando BouncyCastle Provider: ${e.message}", e)
        }
    }
}
