package pe.gob.reniec.dnie.vault.recovery

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import pe.gob.reniec.dnie.vault.*
import java.io.*
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import javax.crypto.Cipher

class RecoveryPackageTest {
    @get:Rule val temp = TemporaryFolder()
    private val container = VaultContainer()
    private val service = VaultRecoveryService()
    private val codec = RecoveryPackageCodec()
    private val password = "contraseña-sintética-recuperación-2026".toCharArray()
    private val context = ByteArray(32) { it.toByte() }
    private val metadata = VaultMetadata("documento-ñ.pdf", "application/pdf")
    private fun pair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private fun seal(data: ByteArray, pair: KeyPair): ByteArray = ByteArrayOutputStream().also {
        container.seal(data.inputStream(), it, pair.public as RSAPublicKey, metadata)
    }.toByteArray()
    private fun unwrap(header: VaultHeader, pair: KeyPair) = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
        init(Cipher.DECRYPT_MODE, pair.private); doFinal(header.envelope)
    }
    private fun prepare(file: ByteArray, pair: KeyPair) = service.prepare(file.inputStream(), { unwrap(it, pair) })
    private fun fails(action: () -> Unit) {
        var caught = false
        try { action() } catch (expected: Exception) { caught = true }
        assertTrue("La operación debía ser rechazada", caught)
    }
    private fun restore(file: ByteArray, entry: RecoveryEntry): ByteArray {
        val stage = temp.newFolder()
        var result: ByteArray? = null
        assertEquals(metadata, service.restore(file.inputStream(), entry, stage, { stream, actual ->
            assertEquals(metadata, actual); result = stream.readBytes()
        }))
        assertEquals(0, stage.listFiles()!!.size)
        return result!!
    }

    @Test fun portableRecoverySurvivesReplacementKeyWithoutOldPrivateKey() {
        val old = pair(); val renewed = pair()
        val data = ByteArray(2 * 1048576 + 53) { (it * 17).toByte() }
        val file = seal(data, old)
        val entry = prepare(file, old)
        val packageFile = temp.newFile("portable.recovery")
        packageFile.writeBytes(codec.seal(listOf(entry), password, context))
        val id = entry.id; entry.close()
        fails { unwrap(container.readHeader(file.inputStream()), renewed) }
        RecoveryPackageCodec().open(packageFile.readBytes(), password).use { recovered ->
            assertArrayEquals(data, restore(file, recovered.entry(id)))
        }
    }

    @Test fun multipleRecipientsAndExplicitCoverage() {
        val firstKey = pair(); val secondKey = pair()
        val first = seal(byteArrayOf(1), firstKey); val second = seal(byteArrayOf(2), secondKey)
        prepare(first, firstKey).use { a -> prepare(second, secondKey).use { b ->
            codec.open(codec.seal(listOf(a, b), password, context), password).use { pkg ->
                assertEquals(setOf(a.id, b.id), pkg.ids().toSet())
                assertArrayEquals(byteArrayOf(1), restore(first, pkg.entry(a.id)))
                assertArrayEquals(byteArrayOf(2), restore(second, pkg.entry(b.id)))
                fails { pkg.entry(UUID.randomUUID()) }
                fails { restore(first, pkg.entry(b.id)) }
            }
            fails { codec.seal(listOf(a, a), password, context) }
            fails { codec.seal(emptyList(), password, context) }
        } }
    }

    @Test fun wrongPasswordAndAuthenticatedHeaderTamperingAreRejected() {
        val key = pair(); prepare(seal(byteArrayOf(4), key), key).use { entry ->
            val encoded = codec.seal(listOf(entry), password, context)
            fails { codec.open(encoded, "otra-contraseña-sintética-2026".toCharArray()) }
            for (offset in listOf(0, 11, 24, 40, 52, encoded.lastIndex)) {
                val bad = encoded.copyOf(); bad[offset] = (bad[offset].toInt() xor 1).toByte()
                fails { codec.open(bad, password) }
            }
            fails { codec.open(encoded.copyOf(encoded.size - 1), password) }
            fails { codec.open(encoded + byteArrayOf(0), password) }
        }
    }

    @Test fun hostileKdfCostsRejectedBeforePasswordWork() {
        val key = pair(); prepare(seal(byteArrayOf(), key), key).use { entry ->
            val encoded = codec.seal(listOf(entry), password, context)
            ByteBuffer.wrap(encoded, 12, 4).putInt(Int.MAX_VALUE)
            var reachedKdf = false
            fails { codec.open(encoded, password) { reachedKdf = true } }
            assertFalse(reachedKdf)
            fails { codec.open(ByteArray(RecoveryPackageCodec.MAX_BYTES + 1), password) }
        }
    }

    @Test fun corruptFinalSegmentNeverPublishesAndCannotBePrepared() {
        val key = pair(); val file = seal(ByteArray(2 * 1048576 + 1), key)
        prepare(file, key).use { entry ->
            val bad = file.copyOf(); bad[bad.lastIndex] = (bad.last().toInt() xor 1).toByte()
            fails { prepare(bad, key) }
            val stage = temp.newFolder(); var published = false
            fails { service.restore(bad.inputStream(), entry, stage, { _, _ -> published = true }) }
            assertFalse(published); assertEquals(0, stage.listFiles()!!.size)
            fails { service.restore(file.copyOf(file.size - 100).inputStream(), entry, stage, { _, _ -> published = true }) }
            assertFalse(published); assertEquals(0, stage.listFiles()!!.size)
        }
    }

    @Test fun exactCiphertextBindingCheckedBeforePublication() {
        val key = pair(); val file = seal(byteArrayOf(7), key)
        prepare(file, key).use { entry ->
            RecoveryEntry(entry.id, entry.recipient(), ByteArray(32), entry.secret()).use { wrongHash ->
                val stage = temp.newFolder(); var published = false
                fails { service.restore(file.inputStream(), wrongHash, stage, { _, _ -> published = true }) }
                assertFalse(published); assertEquals(0, stage.listFiles()!!.size)
            }
        }
    }

    @Test fun cancellationCleansPrivateStagingAndDoesNotPublish() {
        val key = pair(); val file = seal(ByteArray(1048576 + 9), key)
        prepare(file, key).use { entry ->
            val stage = temp.newFolder(); var published = false
            fails { service.restore(file.inputStream(), entry, stage, { _, _ -> published = true }) { throw IOException("cancel") } }
            assertFalse(published); assertEquals(0, stage.listFiles()!!.size)
            fails { service.prepare(file.inputStream(), { unwrap(it, key) }) { throw IOException("cancel") } }
            fails { codec.seal(listOf(entry), password, context) { throw IOException("cancel") } }
        }
    }

    @Test fun passwordChangeDoesNotRevokeHistoricalCopies() {
        val key = pair(); val file = seal(byteArrayOf(3), key)
        prepare(file, key).use { entry ->
            val oldPackage = codec.seal(listOf(entry), password, context)
            val nextPassword = "nueva-contraseña-sintética-2026".toCharArray()
            val newPackage = codec.open(oldPackage, password).use { opened ->
                codec.seal(opened.ids().map(opened::entry), nextPassword, context)
            }
            fails { codec.open(newPackage, password) }
            codec.open(newPackage, nextPassword).use { assertArrayEquals(byteArrayOf(3), restore(file, it.entry(entry.id))) }
            codec.open(oldPackage, password).use { assertArrayEquals(byteArrayOf(3), restore(file, it.entry(entry.id))) }
        }
    }

    @Test fun recoveredContentCanBeReencryptedForNewDniKey() {
        val old = pair(); val renewed = pair(); val data = byteArrayOf(9, 8, 7)
        val original = seal(data, old)
        prepare(original, old).use { entry ->
            val migrated = seal(restore(original, entry), renewed)
            fails { unwrap(container.readHeader(migrated.inputStream()), old) }
            prepare(migrated, renewed).use { newEntry ->
                assertNotEquals(entry.id, newEntry.id)
                assertArrayEquals(data, restore(migrated, newEntry))
                fails { restore(migrated, entry) }
            }
        }
    }

    @Test fun argon2idMatchesRfc9106PublishedVector() {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13).withMemoryAsKB(32)
            .withIterations(3).withParallelism(4).withSalt(ByteArray(16) { 2 })
            .withSecret(ByteArray(8) { 3 }).withAdditional(ByteArray(12) { 4 }).build()
        val result = ByteArray(32)
        Argon2BytesGenerator().apply { init(parameters) }.generateBytes(ByteArray(32) { 1 }, result)
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", result.joinToString("") { "%02x".format(it) })
        parameters.clear(); result.fill(0)
    }

    @Test fun fixedProfileContextSaltAndUnicodePasswordMatter() {
        val salt = ByteArray(16) { 5 }
        val start = System.nanoTime()
        val derived = RecoveryKdf.derive(password, salt, context)
        println("Argon2id R1: 65536 KiB, 3 passes, 4 lanes; single derive ms=${(System.nanoTime() - start) / 1000000}")
        assertArrayEquals(derived, RecoveryKdf.derive(password.copyOf(), salt, context))
        assertFalse(derived.contentEquals(RecoveryKdf.derive(password, salt, ByteArray(32) { 8 })))
        assertFalse(derived.contentEquals(RecoveryKdf.derive(password, ByteArray(16) { 6 }, context)))
        assertFalse(derived.contentEquals(RecoveryKdf.derive((String(password) + " ").toCharArray(), salt, context)))
        fails { RecoveryKdf.derive("corta".toCharArray(), salt, context) }
        derived.fill(0)
    }

    @Test fun entriesOwnBuffersAndClosedSecretsAreUnusable() {
        val secret = ByteArray(32) { 42 }; val hash = ByteArray(32) { 3 }
        val entry = RecoveryEntry(UUID.randomUUID(), ByteArray(32), hash, secret)
        secret.fill(0); hash.fill(0)
        assertEquals(42.toByte(), entry.secret()[0]); assertEquals(3.toByte(), entry.fileHash()[0])
        assertFalse(entry.toString().contains("42"))
        entry.close(); fails { entry.secret() }; fails { codec.seal(listOf(entry), password, context) }
    }
}
