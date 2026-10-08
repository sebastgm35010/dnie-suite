package pe.gob.reniec.dnie.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.domain.vault.UnlockRecoveryBatch
import pe.gob.reniec.dnie.core.nfc.VaultProbeCard
import pe.gob.reniec.dnie.domain.model.*
import pe.gob.reniec.dnie.vault.*
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey

class UnlockRecoveryBatchTest {
    private fun headers(): Map<String, VaultHeader> {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public as RSAPublicKey
        val container = VaultContainer()
        return (0..2).associate { i ->
            val output = ByteArrayOutputStream()
            container.seal(byteArrayOf(i.toByte()).inputStream(), output, key, VaultMetadata("a.txt", "text/plain"))
            i.toString() to container.readHeader(output.toByteArray().inputStream())
        }
    }
    private class Card(val failPin: Boolean = false, val failThird: Boolean = false) : VaultProbeCard {
        var authorized = true // first PIN already verified by coordinator
        var verifies = 0; var unwraps = 0
        val returned = mutableListOf<ByteArray>()
        override suspend fun selectPkiApplet() = error("Not used")
        override suspend fun readCertificate(type: CertificateType): Result<ByteArray> = error("Not used")
        override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> = error("Never sign")
        override suspend fun decipherTestSecret(ciphertext: ByteArray): Result<ByteArray> = error("Not used")
        override suspend fun verifyPin(pin: String): Result<PinVerificationResult> {
            verifies++; assertEquals("1234", pin)
            authorized = !failPin
            return Result.success(if (failPin) PinVerificationResult.InvalidPin(2) else PinVerificationResult.Success)
        }
        override suspend fun unwrapVaultKey(ciphertext: ByteArray): Result<ByteArray> {
            check(authorized); authorized = false; unwraps++
            if (failThird && unwraps == 3) return Result.failure(IllegalStateException("Lost card"))
            return Result.success(ByteArray(32) { unwraps.toByte() }.also { returned += it })
        }
        override fun close() = Unit
    }
    @Test fun consumedPinIsVerifiedForEachFurtherEnvelopeAndSecretsWipedOnClose() = runTest {
        val card = Card()
        UnlockRecoveryBatch().run(card, headers(), "1234") { _, _ -> }.use { assertEquals(3, it.secrets.size) }
        assertEquals(2, card.verifies); assertEquals(3, card.unwraps)
        assertTrue(card.returned.all { it.all { byte -> byte == 0.toByte() } })
    }
    @Test fun rejectedPinStopsWithoutRetryAndWipesEarlierSecret() = runTest {
        val card = Card(failPin = true)
        var failed = false
        try { UnlockRecoveryBatch().run(card, headers(), "1234") { _, _ -> } } catch (_: Exception) { failed = true }
        assertTrue(failed); assertEquals(1, card.verifies); assertEquals(1, card.unwraps)
        assertTrue(card.returned.single().all { it == 0.toByte() })
    }
    @Test fun transportFailureWipesAllPreviouslyUnlockedBuffers() = runTest {
        val card = Card(failThird = true)
        var failed = false
        try { UnlockRecoveryBatch().run(card, headers(), "1234") { _, _ -> } } catch (_: Exception) { failed = true }
        assertTrue(failed); assertEquals(3, card.unwraps)
        assertTrue(card.returned.all { it.all { byte -> byte == 0.toByte() } })
    }
}
