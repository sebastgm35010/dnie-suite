package pe.gob.reniec.dnie.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Provider
import java.security.Security

/**
 * Gestor de inicialización del proveedor criptográfico BouncyCastle (BC).
 *
 * En el entorno de ejecución Android, el sistema operativo incluye internamente
 * una versión reducida y desactualizada de Bouncy Castle (com.android.org.bouncycastle).
 * Para evitar conflictos de firmas de métodos, colisiones en algoritmos de firma digital
 * RSA-2048 y fallas en la construcción de contenedores CMS / PKCS#7 (RFC 5652),
 * este inicializador remueve cualquier instancia previa y registra el proveedor
 * BouncyCastle completo (bcprov-jdk18on) en la posición 1 de la lista de proveedores de seguridad.
 */
object CryptoProviderInitializer {

    @Volatile
    private var initialized = false

    private var bouncyCastleProvider: Provider? = null

    /**
     * Inicializa y registra el proveedor BouncyCastle en la posición 1.
     * Esta función es idempotente y segura para ejecución concurrente.
     */
    @Synchronized
    fun init(): Provider {
        if (initialized && bouncyCastleProvider != null) {
            return bouncyCastleProvider!!
        }

        // Remover proveedor previo si estuviera registrado para evitar conflictos de clase
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)

        // Instanciar proveedor bcprov-jdk18on
        val provider = BouncyCastleProvider()

        // Insertar en la posición 1 de prioridad JCA
        val position = Security.insertProviderAt(provider, 1)
        if (position == -1) {
            // Fallback si la inserción por posición falla en algún entorno de prueba
            Security.addProvider(provider)
        }

        bouncyCastleProvider = provider
        initialized = true
        return provider
    }

    /**
     * Obtiene la instancia activa del proveedor BouncyCastle.
     * Si no ha sido inicializado previamente, ejecuta [init] de manera automática.
     */
    val provider: Provider
        get() {
            if (!initialized || bouncyCastleProvider == null) {
                return init()
            }
            return bouncyCastleProvider!!
        }

    /**
     * Indica si el proveedor BouncyCastle ha sido inicializado e insertado en Security.
     */
    val isInitialized: Boolean
        get() = initialized
}
