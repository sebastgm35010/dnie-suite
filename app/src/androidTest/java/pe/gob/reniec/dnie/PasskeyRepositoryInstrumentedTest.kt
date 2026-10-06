package pe.gob.reniec.dnie

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.passkeys.*
import pe.gob.reniec.dnie.passkeys.android.EncryptedPasskeyRepository
import java.io.File
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import javax.crypto.Cipher

/** Runs only against a unique test directory; never touches a real user's passkey catalog. */
class PasskeyRepositoryInstrumentedTest {
    @Test fun encryptedCatalogRoundTripAndTamperRejection() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.cacheDir, "passkeys-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
        val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val ec = KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val catalog = PasskeyCatalog(mutableListOf(PasskeyRecord(ByteArray(32) { 3 }, "example.com", byteArrayOf(1),
            "private-test-account", "Test", ec.private.encoded, ec.public.encoded)))
        val repository = EncryptedPasskeyRepository(context, invalidateDevice = {})
        var secret: ByteArray? = null
        try {
            repository.save(catalog, rsa.public as RSAPublicKey)
            val file = File(directory, "passkeys/catalog.dnievault")
            assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("private-test-account"))
            val header = requireNotNull(repository.header())
            val recovered = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.DECRYPT_MODE, rsa.private); doFinal(header.envelope)
            }.also { secret = it }
            repository.open(recovered, header.recipient).use { restored ->
                assertEquals("private-test-account", restored.records.single().username)
                assertArrayEquals(catalog.records.single().privateKey, restored.records.single().privateKey)
            }
            val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
            try { repository.open(recovered, header.recipient).close(); fail("Tampered catalog accepted") } catch (_: Exception) {}
        } finally { secret?.fill(0); catalog.close(); directory.deleteRecursively() }
    }
}
