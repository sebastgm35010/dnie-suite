package pe.gob.reniec.dnie.data.vault

import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.vault.*
import java.io.File
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import javax.crypto.Cipher

/** Uses only test-private directories and synthetic RSA, never a real DNI or production vault. */
class VaultRecoveryInstrumentedTest {
    @Test fun portablePackageWorksAfterImportAndWrongPasswordNeverPublishes() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "recovery-test-${UUID.randomUUID()}")
        check(root.mkdirs())
        val isolated = object : ContextWrapper(context) { override fun getCacheDir() = root }
        try {
            val stage = File(root, "vault-private")
            val sender = VaultFileStore(File(root, "sender"), stage)
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val original = ByteArray(1048576 + 33) { (it * 11).toByte() }
            val item = sender.seal(original.inputStream(), pair.public as RSAPublicKey,
                VaultMetadata("prueba-ñ.txt", "text/plain"))
            val header = sender.readHeader(item.id)
            val secret = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.DECRYPT_MODE, pair.private); doFinal(header.envelope)
            }
            val adapter = AndroidVaultRecoveryAdapter(isolated)
            val pkg = File(root, "test.dnierecovery")
            val password = "prueba-sintética-android-recuperación".toCharArray()
            try { adapter.prepare(sender, item.id, header, secret, password, Uri.fromFile(pkg)) }
            finally { secret.fill(0) }
            val receiver = VaultFileStore(File(root, "receiver"), stage)
            var imported: VaultStoredItem? = null
            sender.exportEncrypted(item.id) { imported = receiver.importEncrypted(it) }
            val received = requireNotNull(imported)
            assertNotEquals(item.id, received.id)
            assertEquals(header.id, receiver.readHeader(received.id).id)
            val destination = File(root, "restored.txt")
            adapter.restore(receiver, received.id, Uri.fromFile(pkg), password, Uri.fromFile(destination))
            assertArrayEquals(original, destination.readBytes())
            destination.writeText("sentinel", Charsets.UTF_8)
            var rejected = false
            try {
                adapter.restore(receiver, received.id, Uri.fromFile(pkg),
                    "contraseña-incorrecta-sintética-2026".toCharArray(), Uri.fromFile(destination))
            } catch (_: Exception) { rejected = true }
            assertTrue(rejected); assertEquals("sentinel", destination.readText(Charsets.UTF_8))
            assertEquals(0, stage.listFiles()!!.size)
            password.fill('\u0000')
        } finally { check(root.deleteRecursively()) }
    }
}
