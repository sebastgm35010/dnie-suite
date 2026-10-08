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
    @Test fun wholeVaultCoversDuplicateImportsAndRejectsIncompleteOrCorruptRecoveryBeforePublish() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "recovery-all-test-${UUID.randomUUID()}")
        check(root.mkdirs())
        val isolated = object : ContextWrapper(context) { override fun getCacheDir() = root }
        try {
            val stage = File(root, "vault-private"); val objects = File(root, "objects")
            val store = VaultFileStore(objects, stage)
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val first = store.seal(byteArrayOf(1, 2).inputStream(), pair.public as RSAPublicKey, VaultMetadata("a.txt", "text/plain"))
            val second = store.seal(byteArrayOf(3, 4).inputStream(), pair.public as RSAPublicKey, VaultMetadata("a.txt", "text/plain"))
            store.exportEncrypted(first.id) { store.importEncrypted(it) }
            val headers = store.list().associate { it.id to store.readHeader(it.id) }
            val secrets = headers.mapValues { (_, header) -> Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.DECRYPT_MODE, pair.private); doFinal(header.envelope)
            } }
            val adapter = AndroidVaultRecoveryAdapter(isolated); val pkg = File(root, "all.dnierecovery")
            val password = "prueba-completa-recuperación-android".toCharArray()
            pe.gob.reniec.dnie.domain.vault.UnlockedRecoveryBatch(headers, secrets).use { batch ->
                assertEquals(3, adapter.prepareAll(store, batch, password, Uri.fromFile(pkg)) { _, _ -> })
            }
            val destination = File(root, "all.zip")
            assertEquals(3, adapter.restoreAll(store, Uri.fromFile(pkg), password, Uri.fromFile(destination)) { _, _ -> })
            val contents = mutableListOf<List<Byte>>(); val names = mutableSetOf<String>()
            java.util.zip.ZipInputStream(destination.inputStream()).use { zip ->
                while (true) { val e = zip.nextEntry ?: break; assertTrue(names.add(e.name)); contents += zip.readBytes().toList() }
            }
            assertEquals(3, names.size); assertEquals(2, contents.count { it == listOf(1.toByte(), 2.toByte()) })
            assertEquals(1, contents.count { it == listOf(3.toByte(), 4.toByte()) })
            val extra = store.seal(byteArrayOf(5).inputStream(), pair.public as RSAPublicKey, VaultMetadata("b.txt", "text/plain"))
            destination.writeText("sentinel", Charsets.UTF_8)
            var rejected = false
            try { adapter.restoreAll(store, Uri.fromFile(pkg), password, Uri.fromFile(destination)) { _, _ -> } }
            catch (_: Exception) { rejected = true }
            assertTrue(rejected); assertEquals("sentinel", destination.readText(Charsets.UTF_8)); store.delete(extra.id)
            val bad = File(objects, "${second.id}.dnievault"); val bytes = bad.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); bad.writeBytes(bytes)
            rejected = false
            try { adapter.restoreAll(store, Uri.fromFile(pkg), password, Uri.fromFile(destination)) { _, _ -> } }
            catch (_: Exception) { rejected = true }
            assertTrue(rejected); assertEquals("sentinel", destination.readText(Charsets.UTF_8))
            assertEquals(0, stage.listFiles()!!.size); password.fill('\u0000')
        } finally { check(root.deleteRecursively()) }
    }
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
