package pe.gob.reniec.dnie.data.vault

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.domain.vault.UnlockedRecoveryBatch
import pe.gob.reniec.dnie.vault.*
import java.io.File
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import javax.crypto.Cipher

class VaultArchiveInstrumentedTest {
    @Test fun androidExportsOneArchiveAndMigratesAllToDifferentKey() = runBlocking(Dispatchers.IO) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "archive-device-test-${UUID.randomUUID()}")
        check(root.mkdirs())
        val isolated = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val password = "prueba-archivo-completo-android-2026".toCharArray()
        try {
            val old = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val new = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val store = VaultFileStore(File(isolated.filesDir, "vault-preview/objects"), File(isolated.cacheDir, "vault-private"))
            store.seal("uno".byteInputStream(), old.public as RSAPublicKey, VaultMetadata("uno.txt", "text/plain"))
            store.seal("dos".byteInputStream(), old.public as RSAPublicKey, VaultMetadata("dos.txt", "text/plain"))
            val repository = AndroidVaultRepository(isolated); repository.initialize()
            val headers = store.list().associate { it.id to store.readHeader(it.id) }
            val secrets = headers.mapValues { (_, header) -> Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.DECRYPT_MODE, old.private); doFinal(header.envelope)
            } }
            val backup = File(root, "vault.dniebackup")
            UnlockedRecoveryBatch(headers, secrets).use { batch ->
                assertEquals(2, repository.exportVaultArchive(batch, password, Uri.fromFile(backup)) { _, _ -> })
            }
            assertEquals(2, repository.importVaultArchive(Uri.fromFile(backup), password, new.public as RSAPublicKey) { _, _ -> })
            assertEquals(2, repository.list().size)
            val contents = mutableSetOf<String>()
            repository.list().forEach { item ->
                val header = repository.readHeader(item.id)
                val secret = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                    init(Cipher.DECRYPT_MODE, new.private); doFinal(header.envelope)
                }
                try {
                    val output = File(root, "restored.txt")
                    repository.restore(item.id, secret, header.recipient, Uri.fromFile(output))
                    contents += output.readText(Charsets.UTF_8)
                } finally { secret.fill(0) }
            }
            assertEquals(setOf("uno", "dos"), contents)
            assertTrue(File(isolated.cacheDir, "vault-private").listFiles()!!.isEmpty())
        } finally { password.fill('\u0000'); check(root.deleteRecursively()) }
    }
}
