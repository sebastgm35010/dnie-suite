package pe.gob.reniec.dnie.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Security

/**
 * Pruebas unitarias para [CryptoProviderInitializer].
 *
 * Valida la correcta inserción del proveedor BouncyCastle ("BC"),
 * la idempotencia de su inicialización y la disponibilidad en java.security.Security.
 */
class CryptoProviderInitializerTest {

    @Test
    fun testInitRegistersBouncyCastleProvider() {
        val provider = CryptoProviderInitializer.init()
        assertNotNull("El proveedor retornado no debe ser nulo", provider)
        assertEquals("El nombre del proveedor debe ser BC", BouncyCastleProvider.PROVIDER_NAME, provider.name)
        assertTrue("CryptoProviderInitializer debe marcar isInitialized = true", CryptoProviderInitializer.isInitialized)

        val registeredProvider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        assertNotNull("Security.getProvider('BC') debe encontrar el proveedor registrado", registeredProvider)
        assertEquals("El proveedor registrado debe ser BouncyCastleProvider", BouncyCastleProvider.PROVIDER_NAME, registeredProvider.name)
    }

    @Test
    fun testInitIsIdempotent() {
        val first = CryptoProviderInitializer.init()
        val second = CryptoProviderInitializer.init()
        assertNotNull("Primera llamada no nula", first)
        assertNotNull("Segunda llamada no nula", second)
        assertSame("Múltiples llamadas a init() deben devolver la misma instancia de proveedor", first, second)
    }

    @Test
    fun testProviderPropertyAccess() {
        val provider = CryptoProviderInitializer.provider
        assertNotNull("La propiedad provider debe devolver un proveedor válido", provider)
        assertEquals("BouncyCastleProvider", provider.name, BouncyCastleProvider.PROVIDER_NAME)
        assertTrue("isInitialized debe ser verdadero", CryptoProviderInitializer.isInitialized)
    }
}
