package pe.gob.reniec.dnie.vault

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher

class VaultContainerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val key get() = pair.public as RSAPublicKey
    private val container = VaultContainer()
    private val metadata = VaultMetadata("nombre-confidencial.pdf", "application/pdf")
    private fun seal(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also {
        container.seal(ByteArrayInputStream(bytes), it, key, metadata)
    }.toByteArray()
    private fun secret(header: VaultHeader): ByteArray = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
        init(Cipher.DECRYPT_MODE, pair.private); doFinal(header.envelope)
    }
    private fun fails(action: () -> Unit) { try { action(); fail("Expected failure") } catch (e: Exception) { /* expected */ } }

    @Test fun emptyAndMultiSegmentRoundTripWithEncryptedMetadata() {
        for (size in listOf(0, 3 * 1048576 + 31)) {
            val original = ByteArray(size) { (it * 13).toByte() }
            val encrypted = seal(original)
            assertFalse(String(encrypted, Charsets.ISO_8859_1).contains(metadata.name))
            val input = ByteArrayInputStream(encrypted)
            val header = container.readHeader(input)
            val output = ByteArrayOutputStream()
            val recovered = secret(header)
            assertEquals(32, recovered.size)
            assertEquals(metadata, container.openPayload(input, output, header, recovered))
            assertArrayEquals(original, output.toByteArray())
        }
    }
    @Test fun wrongSecretAndHeaderSubstitutionFailAuthentication() {
        val encrypted = seal(byteArrayOf(1,2,3))
        var input = ByteArrayInputStream(encrypted)
        var header = container.readHeader(input)
        fails { container.openPayload(input, ByteArrayOutputStream(), header, ByteArray(32)) }
        input = ByteArrayInputStream(encrypted)
        header = container.readHeader(input)
        val recovered = secret(header)
        header.recipient[0] = (header.recipient[0].toInt() xor 1).toByte()
        fails { container.openPayload(input, ByteArrayOutputStream(), header, recovered) }
    }
    @Test fun invalidVersionsAndOversizedHeadersAreRejected() {
        val encrypted = seal(byteArrayOf(1))
        for (offset in listOf(0, 11, 15)) {
            val modified = encrypted.copyOf(); modified[offset] = 99
            fails { container.readHeader(ByteArrayInputStream(modified)) }
        }
        val modified = encrypted.copyOf()
        // Header keyset length follows immutable AAD, recipient, envelope and nonce.
        java.nio.ByteBuffer.wrap(modified, 332, 4).putInt(Int.MAX_VALUE)
        fails { container.readHeader(ByteArrayInputStream(modified)) }
    }
    @Test fun namesCannotContainPathsAndInputIsBounded() {
        fails { container.seal(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), key, VaultMetadata("../a", "text/plain")) }
        fails { VaultContainer.copyBounded(ByteArrayInputStream(ByteArray(100)), ByteArrayOutputStream(), limit = 50) }
        val stalled = object : InputStream() { override fun read() = 0; override fun read(b: ByteArray) = 0 }
        fails { VaultContainer.copyBounded(stalled, ByteArrayOutputStream()) }
    }
    @Test fun restorePublishesOnlyAfterEverySegmentAuthenticatesAndCleansStaging() {
        val root = temporary.newFolder("objects")
        val stage = temporary.newFolder("stage")
        val store = VaultFileStore(root, stage)
        val original = ByteArray(2 * 1048576 + 57) { it.toByte() }
        val item = store.seal(ByteArrayInputStream(original), key, metadata)
        val header = store.readHeader(item.id)
        val recovered = secret(header)
        var published = 0
        store.restore(item.id, recovered, header.recipient, { input, actual ->
            published++; assertEquals(metadata, actual); assertArrayEquals(original, input.readBytes())
        })
        assertEquals(1, published)
        val file = File(root, "${item.id}.dnievault")
        val bad = file.readBytes(); bad[bad.lastIndex] = (bad.last().toInt() xor 1).toByte(); file.writeBytes(bad)
        fails { store.restore(item.id, recovered, header.recipient, { _, _ -> published++ }) }
        assertEquals(1, published)
        assertEquals(0, stage.listFiles()!!.size)
        file.writeBytes(bad.copyOf(bad.size - 100))
        fails { store.restore(item.id, recovered, header.recipient, { _, _ -> published++ }) }
        assertEquals(1, published)
        assertEquals(0, stage.listFiles()!!.size)
    }
    @Test fun importsExportsAreExactAndFailedWritesNeverCommit() {
        val root = temporary.newFolder("objects")
        val stage = temporary.newFolder("stage")
        val store = VaultFileStore(root, stage)
        val bytes = seal(byteArrayOf(8,9))
        val item = store.importEncrypted(ByteArrayInputStream(bytes))
        store.exportEncrypted(item.id) { assertArrayEquals(bytes, it.readBytes()) }
        fails { store.importEncrypted(ByteArrayInputStream(byteArrayOf(0,1,2))) }
        fails { store.seal(ByteArrayInputStream(ByteArray(100)), key, metadata) { throw IOException("cancel") } }
        assertEquals(1, store.list().size)
        assertFalse(root.listFiles()!!.any { it.name.startsWith(".pending-") })
        fails { store.readHeader("../recipient") }
        store.delete(item.id)
        assertTrue(store.list().isEmpty())
    }
    @Test fun visibleNamesSurviveRestartAndDuplicateNamesKeepIndependentContents() {
        val root = temporary.newFolder("named-objects")
        val stage = temporary.newFolder("named-stage")
        val store = VaultFileStore(root, stage)
        val first = store.seal(ByteArrayInputStream(byteArrayOf(1)), key, metadata)
        val second = store.seal(ByteArrayInputStream(byteArrayOf(2)), key, metadata)
        assertNotEquals(first.id, second.id)
        val restarted = VaultFileStore(root, stage)
        assertEquals(listOf(metadata.name, metadata.name), restarted.list().map { it.displayName })
        for ((item, expected) in listOf(first to byteArrayOf(1), second to byteArrayOf(2))) {
            val header = restarted.readHeader(item.id)
            restarted.restore(item.id, secret(header), header.recipient, { input, _ -> assertArrayEquals(expected, input.readBytes()) })
        }
        restarted.delete(first.id)
        assertFalse(File(root, "${first.id}.name").exists())
        assertEquals(second.id, restarted.list().single().id)
    }
    @Test fun verifiedMetadataReplacesUntrustedArchiveLabelAndLegacyItemsGainName() {
        val root = temporary.newFolder("label-objects")
        val stage = temporary.newFolder("label-stage")
        val store = VaultFileStore(root, stage)
        val bytes = seal(byteArrayOf(3,4))
        val legacy = store.importEncrypted(ByteArrayInputStream(bytes))
        val imported = store.importEncrypted(ByteArrayInputStream(bytes), displayName = "untrusted-name.bin")
        assertNull(legacy.displayName)
        assertEquals("untrusted-name.bin", imported.displayName)
        for (item in listOf(legacy, imported)) {
            val header = store.readHeader(item.id)
            store.restore(item.id, secret(header), header.recipient, { _, _ -> })
            assertEquals(metadata.name, store.list().first { it.id == item.id }.displayName)
        }
        File(root, "${legacy.id}.name").writeText("../outside.txt")
        assertNull(store.list().first { it.id == legacy.id }.displayName)
        File(root, "${legacy.id}.name").writeBytes(ByteArray(2048))
        assertNull(store.list().first { it.id == legacy.id }.displayName)
    }
    @Test fun cancelledRestorationNeverPublishesAndStartupRemovesInterruptedFiles() {
        val root = temporary.newFolder("objects")
        val stage = temporary.newFolder("stage")
        val store = VaultFileStore(root, stage)
        val item = store.importEncrypted(ByteArrayInputStream(seal(ByteArray(100))))
        val header = store.readHeader(item.id)
        var published = false
        fails { store.restore(item.id, secret(header), header.recipient, { _, _ -> published = true }) { throw IOException("cancel") } }
        assertFalse(published)
        assertEquals(0, stage.listFiles()!!.size)
        File(stage, "interrupted.plain").writeText("secret")
        File(root, ".pending-interrupted").writeText("partial")
        val reopened = VaultFileStore(root, stage)
        assertEquals(1, reopened.list().size)
        assertEquals(0, stage.listFiles()!!.size)
        assertEquals(1, root.listFiles()!!.size)
    }
}
