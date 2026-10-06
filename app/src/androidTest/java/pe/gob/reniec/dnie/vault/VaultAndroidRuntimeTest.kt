package pe.gob.reniec.dnie.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import javax.crypto.Cipher

/** Exercises the actual Android Tink/Protobuf ABI, not a desktop substitute. */
@RunWith(AndroidJUnit4::class)
class VaultAndroidRuntimeTest {
    @Test fun multiSegmentFileRestoresWithApkRuntimeAndRejectsTampering() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "vault-regression-${UUID.randomUUID()}")
        val root = File(directory, "objects")
        val stage = File(directory, "staging")
        var secret: ByteArray? = null
        try {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val key = pair.public as RSAPublicKey
            val source = ByteArray(2 * 1048576 + 73) { (it * 19).toByte() }
            val store = VaultFileStore(root, stage)
            val metadata = VaultMetadata("synthetic.bin", "application/octet-stream")
            val item = store.seal(ByteArrayInputStream(source), key, metadata)
            val header = store.readHeader(item.id)
            val recovered = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.DECRYPT_MODE, pair.private); doFinal(header.envelope)
            }.also { secret = it }
            var publishes = 0
            store.restore(item.id, recovered, header.recipient, { input, actual ->
                publishes++; assertEquals(metadata, actual); assertArrayEquals(source, input.readBytes())
            })
            assertEquals(1, publishes)
            val file = File(root, "${item.id}.dnievault")
            val corrupted = file.readBytes()
            corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 1).toByte()
            file.writeBytes(corrupted)
            try {
                store.restore(item.id, recovered, header.recipient, { _, _ -> publishes++ })
                fail("Tampered file must fail")
            } catch (_: Exception) { }
            assertEquals(1, publishes)
            assertTrue(stage.listFiles()!!.isEmpty())
        } finally { secret?.fill(0); directory.deleteRecursively() }
    }
}
