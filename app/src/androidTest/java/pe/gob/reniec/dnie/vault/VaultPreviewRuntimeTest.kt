package pe.gob.reniec.dnie.vault

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.data.vault.*
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import javax.crypto.Cipher

/** Isolated roots: these tests never touch the user's real vault. */
class VaultPreviewRuntimeTest {
    private fun isolated(): Pair<Context, File> {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = File(app.cacheDir, "viewer-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        }
        return context to root
    }
    @Test fun authenticatedPreviewIsPrivateBoundedAndDeleted() = runBlocking {
        val (context, root) = isolated()
        try {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val store = VaultFileStore(File(context.filesDir, "vault-preview/objects"), File(context.cacheDir, "vault-private"))
            val item = store.seal(ByteArrayInputStream("secreto".toByteArray()), pair.public as RSAPublicKey, VaultMetadata("nota.txt", "text/plain"))
            val repository = AndroidVaultRepository(context); repository.initialize()
            val header = repository.readHeader(item.id)
            val secret = Cipher.getInstance("RSA/ECB/PKCS1Padding").run { init(Cipher.DECRYPT_MODE, pair.private); doFinal(header.envelope) }
            val ticket = repository.preview(item.id, secret, header.recipient)
            secret.fill(0)
            val previews = VaultPreviewFiles(context)
            assertEquals("secreto", VaultPreviewReader(previews.file(ticket.token), ticket.mime).read(0).text)
            assertTrue(File(context.cacheDir, "vault-private").listFiles().orEmpty().isEmpty())
            repository.discardPreview(ticket.token)
            assertFalse(previews.file(ticket.token).exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun textPreviewTruncatesAndImageDecodeIsSampled() = runBlocking {
        val (context, root) = isolated()
        try {
            val previews = VaultPreviewFiles(context)
            val text = previews.create(ByteArrayInputStream(ByteArray(300000) { 65 }), VaultMetadata("grande.txt", "text/plain")) {}
            assertTrue(VaultPreviewReader(previews.file(text.token), text.mime).read(0).text!!.endsWith("[Vista limitada a 256 KiB]"))
            val bitmap = Bitmap.createBitmap(2000, 10, Bitmap.Config.ARGB_8888)
            val png = java.io.ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, png); bitmap.recycle()
            val image = previews.create(ByteArrayInputStream(png.toByteArray()), VaultMetadata("foto.png", "image/png")) {}
            val decoded = VaultPreviewReader(previews.file(image.token), image.mime).read(0).bitmap!!
            assertTrue(decoded.width <= 1600); decoded.recycle()
            previews.clear(); assertFalse(previews.file(image.token).exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun cancellationAndUnsupportedFormatsDoNotLeavePlaintext() {
        val (context, root) = isolated()
        try {
            val previews = VaultPreviewFiles(context)
            try { previews.create(ByteArrayInputStream(byteArrayOf(1)), VaultMetadata("x.txt", "text/plain")) { throw kotlinx.coroutines.CancellationException() }; fail() }
            catch (_: kotlinx.coroutines.CancellationException) {}
            try { previews.create(ByteArrayInputStream(byteArrayOf(1)), VaultMetadata("x.exe", "application/octet-stream")) {}; fail() }
            catch (_: IllegalArgumentException) {}
            assertTrue(File(context.cacheDir, "vault-viewer-private").listFiles().orEmpty().isEmpty())
            try { previews.file("../outside"); fail() } catch (_: IllegalArgumentException) {}
        } finally { root.deleteRecursively() }
    }
    @Test fun privateActivityBlocksCaptureAndRemovesFileOnClose() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val previews = VaultPreviewFiles(app)
        val ticket = previews.create(ByteArrayInputStream("secreto".toByteArray()), VaultMetadata("nota.txt", "text/plain")) {}
        try {
            androidx.test.core.app.ActivityScenario.launch<pe.gob.reniec.dnie.ui.vault.VaultPreviewActivity>(
                pe.gob.reniec.dnie.ui.vault.VaultPreviewActivity.intent(app, ticket)).use { scenario ->
                scenario.onActivity { activity ->
                    assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
                }
            }
            assertFalse(previews.file(ticket.token).exists())
        } finally { previews.delete(ticket.token) }
    }
}
