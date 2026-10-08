package pe.gob.reniec.dnie.passkeys

import org.junit.Assert.*
import org.junit.Test

class PasskeyLocalCipherTest {
    private fun catalog() = PasskeyCatalog(mutableListOf(
        PasskeyRecord(ByteArray(32) { 1 }, "example.com", byteArrayOf(2), "Test", "Test",
            byteArrayOf(3, 4), byteArrayOf(5), backupEligible = false),
        PasskeyRecord(ByteArray(32) { 6 }, "example.org", byteArrayOf(7), "Test2", "Test2",
            byteArrayOf(8, 9), byteArrayOf(10), backupEligible = true, backedUp = true)))
    @Test fun migrationPreservesCredentialsAndBackupPolicyWithoutDniKeys() {
        val key = PasskeyLocalCipher.secret(); val binding = PasskeyLocalCipher.secret()
        try { catalog().use { source ->
            val sealed = PasskeyLocalCipher.seal(source, key, binding)
            PasskeyLocalCipher.open(sealed, key, binding).use { target ->
                for (i in source.records.indices) {
                    assertArrayEquals(source.records[i].id, target.records[i].id)
                    assertArrayEquals(source.records[i].privateKey, target.records[i].privateKey)
                    assertEquals(source.records[i].backupEligible, target.records[i].backupEligible)
                    assertEquals(source.records[i].backedUp, target.records[i].backedUp)
                }
                PasskeyBackupPolicy.portableCopy(target).use { assertEquals(1, it.records.size) }
            }
        } } finally { key.fill(0) }
    }
    @Test fun wrongKeyBindingTamperAndTruncationAreRejected() {
        val key = PasskeyLocalCipher.secret(); val binding = PasskeyLocalCipher.secret()
        try { catalog().use { source ->
            val sealed = PasskeyLocalCipher.seal(source, key, binding)
            fun rejected(bytes: ByteArray, candidateKey: ByteArray = key, candidateBinding: ByteArray = binding) {
                try { PasskeyLocalCipher.open(bytes, candidateKey, candidateBinding).close(); fail("Accepted invalid envelope") }
                catch (_: Exception) { }
            }
            rejected(sealed, ByteArray(32)); rejected(sealed, key, ByteArray(32))
            rejected(sealed.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            rejected(sealed.copyOf(sealed.size - 1)); rejected(ByteArray(PasskeyLocalCipher.MAX_SEALED_BYTES + 1))
        } } finally { key.fill(0) }
    }
    @Test fun updatesUseNewNonceAndOldEnvelopeRemainsReadable() {
        val key = PasskeyLocalCipher.secret(); val binding = PasskeyLocalCipher.secret()
        try { catalog().use { source ->
            val first = PasskeyLocalCipher.seal(source, key, binding)
            val second = PasskeyLocalCipher.seal(source, key, binding)
            assertFalse(first.contentEquals(second))
            PasskeyLocalCipher.open(first, key, binding).close()
            PasskeyLocalCipher.open(second, key, binding).close()
        } } finally { key.fill(0) }
    }
    @Test fun emptyAuthorizedStoreAndKeySizes() {
        val key = PasskeyLocalCipher.secret(); val binding = PasskeyLocalCipher.secret()
        try { PasskeyCatalog().use { catalog ->
            PasskeyLocalCipher.open(PasskeyLocalCipher.seal(catalog, key, binding), key, binding).use {
                assertTrue(it.records.isEmpty())
            }
            try { PasskeyLocalCipher.seal(catalog, ByteArray(16), binding); fail("Short key") }
            catch (_: IllegalArgumentException) { }
        } } finally { key.fill(0) }
    }
    @Test fun migratedRealKeyStillVerifiesAgainstOriginalRegisteredPublicKey() {
        val pair = java.security.KeyPairGenerator.getInstance("EC").apply {
            initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val key = PasskeyLocalCipher.secret(); val binding = PasskeyLocalCipher.secret()
        try {
            PasskeyCatalog(mutableListOf(PasskeyRecord(ByteArray(32) { 1 }, "example.com", byteArrayOf(2),
                "Test", "Test", pair.private.encoded, pair.public.encoded))).use { original ->
                PasskeyLocalCipher.open(PasskeyLocalCipher.seal(original, key, binding), key, binding).use { migrated ->
                    val private = java.security.KeyFactory.getInstance("EC").generatePrivate(
                        java.security.spec.PKCS8EncodedKeySpec(migrated.records.single().privateKey))
                    val challenge = ByteArray(32) { it.toByte() }
                    val signature = java.security.Signature.getInstance("SHA256withECDSA").apply {
                        initSign(private); update(challenge)
                    }.sign()
                    assertTrue(java.security.Signature.getInstance("SHA256withECDSA").apply {
                        initVerify(pair.public); update(challenge)
                    }.verify(signature))
                }
            }
        } finally { key.fill(0) }
    }
}
