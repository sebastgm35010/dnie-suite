package pe.gob.reniec.dnie.vault

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher

class VaultCapabilityProbeTest {
    companion object {
        private val pair by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
    }

    @Test fun verifiesWithIndependentPrivateKeyAndClearsReturnedSecret() = runTest {
        var recovered: ByteArray? = null
        var envelope: ByteArray? = null
        val result = VaultCapabilityProbe().verify(pair.public as RSAPublicKey, CardKeyUnwrapper { ciphertext ->
            assertEquals(256, ciphertext.size)
            envelope = ciphertext
            val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsa.init(Cipher.DECRYPT_MODE, pair.private)
            rsa.doFinal(ciphertext).also { recovered = it }
        })
        assertTrue(result.verified)
        assertEquals(64, result.certificateFingerprint.length)
        assertArrayEquals(ByteArray(32), recovered)
        assertArrayEquals(ByteArray(256), envelope)
    }

    @Test fun rejectsDifferentSecretAndClearsItOnFailure() = runTest {
        val wrong = ByteArray(32) { 0x5A }
        try {
            VaultCapabilityProbe().verify(pair.public as RSAPublicKey, CardKeyUnwrapper { wrong })
            fail("Unexpected success")
        } catch (_: IllegalStateException) {}
        assertArrayEquals(ByteArray(32), wrong)
    }

    @Test fun rejectsWrongSecretLength() = runTest {
        try {
            VaultCapabilityProbe().verify(pair.public as RSAPublicKey, CardKeyUnwrapper { ByteArray(31) })
            fail("Unexpected success")
        } catch (_: IllegalStateException) {}
    }

    @Test fun rejectsUnsupportedKeyBeforeCallingCard() = runTest {
        val weak = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        var called = false
        try {
            VaultCapabilityProbe().verify(weak.public as RSAPublicKey, CardKeyUnwrapper { called = true; ByteArray(32) })
            fail("Unexpected success")
        } catch (_: IllegalArgumentException) {}
        assertFalse(called)
    }

    @Test fun cancellationPropagatesAndClearsCiphertextBuffer() = runTest {
        var envelope: ByteArray? = null
        try {
            VaultCapabilityProbe().verify(pair.public as RSAPublicKey, CardKeyUnwrapper {
                envelope = it
                throw CancellationException("Cancelled")
            })
            fail("Unexpected success")
        } catch (_: CancellationException) {}
        assertArrayEquals(ByteArray(256), envelope)
    }
}
