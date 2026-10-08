package pe.gob.reniec.dnie.vault.recovery

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.gob.reniec.dnie.vault.*
import java.io.*
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class RecoveryFailureScenariosTest {
    @get:Rule val temp = TemporaryFolder()
    private val container = VaultContainer()
    private val service = VaultRecoveryService()
    private val codec = RecoveryPackageCodec()
    private val password = "recuperación-pruebas-adversas-2026".toCharArray()
    private val context = ByteArray(32) { 7 }
    private val pair by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
    private val metadata = VaultMetadata("archivo.bin", "application/octet-stream")
    private fun file(size: Int = 16) = ByteArrayOutputStream().also {
        container.seal(ByteArray(size) { (it * 31).toByte() }.inputStream(), it, pair.public as RSAPublicKey, metadata)
    }.toByteArray()
    private fun unwrap(header: VaultHeader): ByteArray = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
        init(Cipher.DECRYPT_MODE, pair.private); doFinal(header.envelope)
    }
    private fun entry(bytes: ByteArray) = service.prepare(bytes.inputStream(), ::unwrap)
    private fun rejected(action: () -> Unit): Exception {
        try { action() } catch (expected: Exception) { return expected }
        throw AssertionError("Se esperaba rechazo")
    }
    private class TrackedInput(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    @Test fun invalidStagingClosesInputWithoutPublication() {
        val bytes = file()
        entry(bytes).use { e ->
            for (stage in listOf(temp.newFile(), File(temp.root, "absent"))) {
                val input = TrackedInput(bytes); var published = false
                rejected { service.restore(input, e, stage, { _, _ -> published = true }) }
                assertTrue(input.closed); assertFalse(published)
            }
        }
    }

    @Test fun cancellationBeforeUnwrapNeverRequestsCardOperation() {
        val bytes = file()
        for (cancelAt in listOf(1, 2)) {
            val input = TrackedInput(bytes); var checks = 0; var cardCalls = 0
            rejected { service.prepare(input, { cardCalls++; unwrap(it) }) {
                if (++checks == cancelAt) throw IOException("cancel before card")
            } }
            assertEquals(0, cardCalls); assertTrue(input.closed)
        }
    }

    @Test fun failedDestinationPublishesOnceAndCleansStaging() {
        val bytes = file(1048576 + 77)
        entry(bytes).use { e ->
            val stage = temp.newFolder(); val input = TrackedInput(bytes)
            var calls = 0; var publishedStream: InputStream? = null
            val cause = IOException("destination full")
            val failure = rejected { service.restore(input, e, stage, { stream, _ ->
                calls++; publishedStream = stream; assertTrue(stream.read() >= 0); throw cause
            }) }
            assertSame(cause, failure); assertEquals(1, calls); assertTrue(input.closed)
            assertEquals(0, stage.listFiles()!!.size)
            rejected { publishedStream!!.read() }
            // Recovery can be explicitly requested again; the motor never repeats the callback itself.
            service.restore(bytes.inputStream(), e, stage, { stream, _ -> assertEquals(1048576 + 77, stream.readBytes().size) })
        }
    }

    @Test fun cancellationAfterWritingPlaintextRemovesIt() {
        val bytes = file(3 * 1048576 + 71)
        entry(bytes).use { e ->
            val stage = temp.newFolder(); val input = TrackedInput(bytes)
            var sawPlaintext = false; var published = false
            rejected { service.restore(input, e, stage, { _, _ -> published = true }) {
                if (stage.listFiles()!!.any { it.length() > 0 }) {
                    sawPlaintext = true; throw IOException("cancel after progress")
                }
            } }
            assertTrue(sawPlaintext); assertFalse(published); assertTrue(input.closed)
            assertEquals(0, stage.listFiles()!!.size)
        }
    }

    @Test fun readFailureAndBadUnwrapCloseResourcesAndWipeOwnedSecret() {
        val bytes = file(2 * 1048576 + 41)
        val input = TrackedInput(bytes)
        val supplied = ByteArray(32) { 99 }
        rejected { service.prepare(input, { supplied }) }
        assertTrue(input.closed); assertArrayEquals(ByteArray(32), supplied)
        val failureInput = object : FilterInputStream(bytes.inputStream()) {
            var count = 0; var closed = false
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (count > 600000) throw IOException("provider disconnected")
                return super.read(b, off, minOf(len, 4096)).also { if (it > 0) count += it }
            }
            override fun close() { closed = true; super.close() }
        }
        entry(bytes).use { e ->
            val stage = temp.newFolder(); var published = false
            rejected { service.restore(failureInput, e, stage, { _, _ -> published = true }) }
            assertTrue(failureInput.closed); assertFalse(published); assertEquals(0, stage.listFiles()!!.size)
        }
        val unwrapFailure = TrackedInput(bytes)
        rejected { service.prepare(unwrapFailure, { throw IOException("card absent") }) }
        assertTrue(unwrapFailure.closed)
    }

    @Test fun emptySegmentBoundariesAndLargerFileRecoverExactly() {
        for (size in listOf(0, 1, 1048575, 1048576, 1048577, 8 * 1048576 + 13)) {
            val bytes = file(size)
            entry(bytes).use { e ->
                val stage = temp.newFolder()
                service.restore(bytes.inputStream(), e, stage, { stream, actual ->
                    assertEquals(metadata, actual)
                    assertArrayEquals(ByteArray(size) { (it * 31).toByte() }, stream.readBytes())
                })
                assertEquals(0, stage.listFiles()!!.size)
            }
        }
    }

    @Test fun separateRestoresCanRunConcurrentlyWithoutMixingStaging() {
        val a = file(1048576 + 5); val b = file(19)
        entry(a).use { ea -> entry(b).use { eb ->
            val stage = temp.newFolder(); val pool = Executors.newFixedThreadPool(2)
            try {
                val futures = listOf(a to ea, b to eb).mapIndexed { index, (bytes, e) -> pool.submit<Boolean> {
                    val size = if (index == 0) 1048576 + 5 else 19
                    service.restore(bytes.inputStream(), e, stage, { stream, _ ->
                        assertArrayEquals(ByteArray(size) { (it * 31).toByte() }, stream.readBytes())
                    }); true
                } }
                futures.forEach { assertTrue(it.get(30, TimeUnit.SECONDS)) }
                assertEquals(0, stage.listFiles()!!.size)
            } finally { pool.shutdownNow() }
        } }
    }

    @Test fun maximumEntryCountRoundTripsAndOverflowIsRejected() {
        val entries = (0 until RecoveryPackageCodec.MAX_ENTRIES).map {
            RecoveryEntry(UUID(0, it.toLong()), ByteArray(32), ByteArray(32) { 1 }, ByteArray(32) { 2 })
        }
        try {
            val encoded = codec.seal(entries, password, context)
            assertEquals(RecoveryPackageCodec.MAX_BYTES, encoded.size)
            codec.open(encoded, password).use { assertEquals(entries.map { it.id }, it.ids()) }
            rejected { codec.seal(entries + entries.first(), password, context) }
        } finally { entries.forEach { it.close() } }
    }

    /** Authentically encrypted malformed bodies test parser invariants beyond GCM tampering. */
    private fun authenticatedBody(body: ByteArray): ByteArray {
        val salt = ByteArray(16) { 3 }; val nonce = ByteArray(12) { 4 }
        val header = ByteBuffer.allocate(88).apply {
            put("DNIERCP1".toByteArray(Charsets.US_ASCII)); putInt(1)
            putInt(65536); putInt(3); putInt(4); put(salt); put(nonce); put(context); putInt(body.size + 16)
        }.array()
        val key = RecoveryKdf.derive(password, salt, context)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header); return header + cipher.doFinal(body)
        } finally { key.fill(0) }
    }

    @Test fun authenticatedMalformedBodiesRejectCountsLengthsAndDuplicateIds() {
        val bodies = listOf(
            ByteBuffer.allocate(4).putInt(0).array(),
            ByteBuffer.allocate(116).putInt(Int.MAX_VALUE).array(),
            ByteBuffer.allocate(116).putInt(-1).array(),
            ByteBuffer.allocate(116).putInt(2).array(),
            ByteBuffer.allocate(228).putInt(2).array() // two all-zero identical UUIDs
        )
        for (body in bodies) rejected { codec.open(authenticatedBody(body), password) }
    }

    @Test fun cancellationWhileParsingAndAfterKdfDoesNotReturnPackage() {
        val bytes = file()
        entry(bytes).use { e ->
            val encoded = codec.seal(listOf(e), password, context)
            for (cancelAt in listOf(2, 3, 4)) {
                var calls = 0
                rejected { codec.open(encoded, password) { if (++calls == cancelAt) throw IOException("cancel") } }
                assertEquals(cancelAt, calls)
            }
        }
    }

    @Test fun randomSaltAndNonceAndPasswordOwnershipArePreserved() {
        val bytes = file()
        entry(bytes).use { e ->
            val before = password.copyOf(); val beforeContext = context.copyOf()
            val a = codec.seal(listOf(e), password, context)
            val b = codec.seal(listOf(e), password, context)
            assertFalse(a.copyOfRange(24, 40).contentEquals(b.copyOfRange(24, 40)))
            assertFalse(a.copyOfRange(40, 52).contentEquals(b.copyOfRange(40, 52)))
            codec.open(a, password).use { assertEquals(listOf(e.id), it.ids()) }
            assertArrayEquals(before, password); assertArrayEquals(beforeContext, context)
            for (invalid in listOf(CharArray(15) { 'x' }, CharArray(1025) { 'x' }, CharArray(16) { '\u0000' })) {
                rejected { codec.seal(listOf(e), invalid, context) }
            }
        }
    }
}
