package pe.gob.reniec.dnie

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.passkeys.*
import pe.gob.reniec.dnie.passkeys.android.*
import java.io.File
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.util.UUID

/** Real maintenance/AtomicFile/URI engine with isolated synthetic card keys. No simulator in the app APK. */
class PasskeyMaintenanceInstrumentedTest {
    private class Fixture : AutoCloseable {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.cacheDir, "maintenance-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = directory
        }
        var failCommit = false
        val repository = EncryptedPasskeyRepository(context, invalidateDevice = { if (failCommit) throw java.io.IOException("Synthetic commit failure") })
        val maintenance = PasskeyMaintenance(context, repository)
        val old = SyntheticDniCard.newPair()
        val next = SyntheticDniCard.newPair()
        val challenge = PasskeyEncoding.encode(ByteArray(32) { 4 })
        val caller = PasskeyCaller("android:apk-key-hash:${PasskeyEncoding.encode(ByteArray(32) { 3 })}")
        val options = PasskeyCreateOptions.parse("""{"rp":{"id":"example.com"},"user":{"id":"AQID","name":"Synthetic","displayName":"Synthetic"},"challenge":"$challenge","pubKeyCredParams":[{"type":"public-key","alg":-7}]}""")
        suspend fun seed(eligible: Boolean = true) {
            PasskeyCatalog().use { catalog ->
                catalog.records.add(PasskeyEngine(backupEligible = eligible).create(options, caller, catalog).first)
                repository.save(catalog, old.public as RSAPublicKey)
            }
        }
        fun catalogFile() = File(directory, "passkeys/catalog.dnievault")
        override fun close() { directory.deleteRecursively() }
    }
    @Test fun completeMigrationPreservesCredentialSignsAndCanRollbackWithPreviousDni() = runBlocking {
        Fixture().use { f ->
            f.seed()
            val oldCard = SyntheticDniCard(f.old, expired = true)
            val snapshot = f.maintenance.open(oldCard, SyntheticDniCard.TEST_PIN)
            try {
                val newCard = SyntheticDniCard(f.next)
                f.maintenance.migrate(newCard, SyntheticDniCard.TEST_PIN, snapshot.bytes, snapshot.revision)
                assertEquals(1, newCard.pinAttempts); assertTrue(newCard.closed)
                assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(f.next.public.encoded), f.repository.header()!!.recipient)
                assertTrue(f.maintenance.hasPrevious())
                PasskeyCardAccess(f.repository).open(SyntheticDniCard(f.next), SyntheticDniCard.TEST_PIN, false).use { session ->
                    assertArrayEquals(snapshot.bytes, session.catalog.encode())
                    val record = session.catalog.records.single()
                    val json = PasskeyEngine().authenticate(PasskeyGetOptions("example.com", listOf(record.encodedId), f.challenge), f.caller, record)
                    assertEquals(record.encodedId, PasskeyJson.parse(json)["id"].asString)
                }
                val oldRetry = SyntheticDniCard(f.old)
                try { PasskeyCardAccess(f.repository).open(oldRetry, SyntheticDniCard.TEST_PIN, false).close(); fail("Old DNI still opens active catalog") }
                catch (_: IllegalArgumentException) { assertEquals(0, oldRetry.pinAttempts) }
                f.maintenance.restorePrevious(SyntheticDniCard(f.old, expired = true), SyntheticDniCard.TEST_PIN)
                assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(f.old.public.encoded), f.repository.header()!!.recipient)
                assertTrue(oldCard.lastSecret!!.all { it == 0.toByte() })
            } finally { snapshot.bytes.fill(0) }
        }
    }
    @Test fun failedMigrationNeverChangesActiveCatalogOrWritesRollback() = runBlocking {
        Fixture().use { f ->
            f.seed(); val snapshot = f.maintenance.open(SyntheticDniCard(f.old), SyntheticDniCard.TEST_PIN)
            val before = f.catalogFile().readBytes()
            try {
                val expired = SyntheticDniCard(f.next, expired = true)
                try { f.maintenance.migrate(expired, SyntheticDniCard.TEST_PIN, snapshot.bytes, snapshot.revision); fail("Expired destination accepted") }
                catch (_: java.security.cert.CertificateExpiredException) { assertEquals(0, expired.pinAttempts) }
                val badPin = SyntheticDniCard(f.next)
                try { f.maintenance.migrate(badPin, "000000", snapshot.bytes, snapshot.revision); fail("Incorrect PIN accepted") }
                catch (_: IllegalStateException) { assertEquals(1, badPin.pinAttempts); assertEquals(0, badPin.privateOperations) }
                val cancelled = SyntheticDniCard(f.next).apply { cancelProof = true }
                try { f.maintenance.migrate(cancelled, SyntheticDniCard.TEST_PIN, snapshot.bytes, snapshot.revision); fail("Cancelled operation committed") }
                catch (_: CancellationException) { assertTrue(cancelled.closed) }
                val wrong = SyntheticDniCard(f.next).apply { wrongProof = true }
                try { f.maintenance.migrate(wrong, SyntheticDniCard.TEST_PIN, snapshot.bytes, snapshot.revision); fail("Invalid private proof accepted") }
                catch (_: IllegalStateException) {}
                assertArrayEquals(before, f.catalogFile().readBytes()); assertFalse(f.maintenance.hasPrevious())
            } finally { snapshot.bytes.fill(0) }
        }
    }
    @Test fun staleSnapshotCannotOverwriteAnUpdatedCatalog() = runBlocking {
        Fixture().use { f ->
            f.seed(); val snapshot = f.maintenance.open(SyntheticDniCard(f.old), SyntheticDniCard.TEST_PIN)
            try {
                f.seed(); val updated = f.catalogFile().readBytes()
                try { f.maintenance.migrate(SyntheticDniCard(f.next), SyntheticDniCard.TEST_PIN, snapshot.bytes, snapshot.revision); fail("Stale snapshot accepted") }
                catch (_: IllegalArgumentException) {}
                assertArrayEquals(updated, f.catalogFile().readBytes()); assertFalse(f.maintenance.hasPrevious())
            } finally { snapshot.bytes.fill(0) }
        }
    }
    @Test fun exportStageRestoreAndTamperRejectionUseRealEncryptedFiles() = runBlocking {
        Fixture().use { f ->
            f.seed(); val file = File(f.directory, "portable.dnievault"); val uri = Uri.fromFile(file)
            assertEquals(0, f.maintenance.export(uri, SyntheticDniCard(f.old), SyntheticDniCard.TEST_PIN))
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("Synthetic"))
            val staged = f.maintenance.stage(uri)
            f.maintenance.restore(SyntheticDniCard(f.old), SyntheticDniCard.TEST_PIN, staged)
            PasskeyCardAccess(f.repository).open(SyntheticDniCard(f.old), SyntheticDniCard.TEST_PIN, false).use {
                assertTrue(it.catalog.records.single().backedUp)
            }
            val before = f.catalogFile().readBytes()
            file.writeBytes(file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            val damaged = f.maintenance.stage(uri)
            try { f.maintenance.restore(SyntheticDniCard(f.old), SyntheticDniCard.TEST_PIN, damaged); fail("Corrupt backup replaced active catalog") }
            catch (_: Exception) {}
            assertArrayEquals(before, f.catalogFile().readBytes())
            f.maintenance.discard(damaged)
        }
    }
    @Test fun writeFailureLeavesOriginalCatalogReadableAndRollbackEncrypted() = runBlocking {
        Fixture().use { f ->
            f.seed(); val snapshot = f.maintenance.open(SyntheticDniCard(f.old), SyntheticDniCard.TEST_PIN)
            try {
                val before = f.catalogFile().readBytes(); f.failCommit = true
                try { f.maintenance.migrate(SyntheticDniCard(f.next), SyntheticDniCard.TEST_PIN, snapshot.bytes, snapshot.revision); fail("Commit failure ignored") }
                catch (_: java.io.IOException) {}
                assertArrayEquals(before, f.catalogFile().readBytes()); assertTrue(f.maintenance.hasPrevious())
                assertFalse(File(f.directory, "passkeys/catalog.dnievault.new").exists())
                PasskeyCardAccess(f.repository).open(SyntheticDniCard(f.old), SyntheticDniCard.TEST_PIN, false).use {
                    assertArrayEquals(snapshot.bytes, it.catalog.encode())
                }
            } finally { snapshot.bytes.fill(0) }
        }
    }
    @Test fun legacyCredentialsCanChangeDniOnThisPhoneButCannotBeExported() = runBlocking {
        Fixture().use { f ->
            f.seed(eligible = false); val snapshot = f.maintenance.open(SyntheticDniCard(f.old), SyntheticDniCard.TEST_PIN)
            try {
                f.maintenance.migrate(SyntheticDniCard(f.next), SyntheticDniCard.TEST_PIN, snapshot.bytes, snapshot.revision)
                PasskeyCardAccess(f.repository).open(SyntheticDniCard(f.next), SyntheticDniCard.TEST_PIN, false).use {
                    assertFalse(it.catalog.records.single().backupEligible)
                    assertArrayEquals(snapshot.bytes, it.catalog.encode())
                }
                val before = f.catalogFile().readBytes(); val file = File(f.directory, "legacy-export.dnievault")
                try { f.maintenance.export(Uri.fromFile(file), SyntheticDniCard(f.next), SyntheticDniCard.TEST_PIN); fail("Legacy exported") }
                catch (_: IllegalArgumentException) {}
                assertFalse(file.exists()); assertArrayEquals(before, f.catalogFile().readBytes())
            } finally { snapshot.bytes.fill(0) }
        }
    }
}
