package pe.gob.reniec.dnie.passkeys.android

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import pe.gob.reniec.dnie.core.nfc.VaultProbeCard
import pe.gob.reniec.dnie.domain.model.*
import pe.gob.reniec.dnie.passkeys.PasskeyCatalog
import pe.gob.reniec.dnie.vault.*
import java.io.*
import java.math.BigInteger
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.crypto.Cipher

class PasskeyCardAccessTest {
    companion object {
        private val pair by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
        private val certificate by lazy {
            val name = X500Name("CN=Synthetic Test Only")
            JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date(System.currentTimeMillis() - 86400000),
                Date(System.currentTimeMillis() + 86400000), name, pair.public)
                .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyEncipherment))
                .build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)).encoded
        }
    }
    private class MemoryStore : PasskeyStore {
        private var bytes: ByteArray? = null
        override suspend fun header() = bytes?.let { VaultContainer().readHeader(ByteArrayInputStream(it)) }
        override suspend fun open(secret: ByteArray, expectedRecipient: ByteArray): PasskeyCatalog {
            val input = ByteArrayInputStream(requireNotNull(bytes)); val engine = VaultContainer(); val header = engine.readHeader(input)
            require(MessageDigest.isEqual(header.recipient, expectedRecipient))
            val output = ByteArrayOutputStream()
            engine.openPayload(input, output, header, secret)
            return PasskeyCatalog.decode(output.toByteArray())
        }
        override suspend fun save(catalog: PasskeyCatalog, key: RSAPublicKey) {
            val output = ByteArrayOutputStream()
            VaultContainer().seal(ByteArrayInputStream(catalog.encode()), output, key, VaultMetadata("passkeys.bin", "application/x-dnie-passkeys"))
            bytes = output.toByteArray()
        }
    }
    private class Card : VaultProbeCard {
        var der: ByteArray = certificate
        var pins = 0; var unwraps = 0; var probes = 0; var closed = false
        var result: PinVerificationResult = PinVerificationResult.Success
        var recovered: ByteArray? = null
        var wrong = false; var cancelProbe = false
        override suspend fun selectPkiApplet() = Result.success(Unit)
        override fun requireVaultProbeSupport() = Result.success(Unit)
        override suspend fun readCertificate(type: CertificateType): Result<ByteArray> {
            assertEquals(CertificateType.ENCRYPTION, type); return Result.success(der)
        }
        override suspend fun verifyPin(pin: String): Result<PinVerificationResult> { pins++; return Result.success(result) }
        private fun decrypt(input: ByteArray): ByteArray = (if (wrong) ByteArray(32) { 8 } else Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
            init(Cipher.DECRYPT_MODE, pair.private); doFinal(input)
        }).also { recovered = it }
        override suspend fun decipherTestSecret(ciphertext: ByteArray): Result<ByteArray> {
            probes++; if (cancelProbe) throw CancellationException("Synthetic cancellation")
            return Result.success(decrypt(ciphertext))
        }
        override suspend fun unwrapVaultKey(ciphertext: ByteArray): Result<ByteArray> { unwraps++; return Result.success(decrypt(ciphertext)) }
        override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> = error("RENIEC signing key must not be used for passkeys")
        override fun close() { closed = true }
    }
    @Test fun registrationNeedsSuccessfulPrivateProofAndOnlyOnePinAttempt() = runBlocking {
        val card = Card()
        PasskeyCardAccess(MemoryStore()).open(card, "123456", creating = true).use { assertTrue(it.catalog.records.isEmpty()) }
        assertEquals(1, card.pins); assertEquals(1, card.probes); assertEquals(0, card.unwraps)
        assertTrue(card.closed); assertTrue(requireNotNull(card.recovered).all { it == 0.toByte() })
    }
    @Test fun failedPinDoesNotUnwrapOrRetry() = runBlocking {
        val card = Card().apply { result = PinVerificationResult.InvalidPin(2) }
        try { PasskeyCardAccess(MemoryStore()).open(card, "123456", true); fail("Unexpected success") } catch (_: IllegalStateException) {}
        assertEquals(1, card.pins); assertEquals(0, card.unwraps); assertEquals(0, card.probes); assertTrue(card.closed)
    }
    @Test fun wrongDniRejectedBeforePin() = runBlocking {
        val store = MemoryStore()
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        store.save(PasskeyCatalog(), other.public as RSAPublicKey)
        val card = Card()
        try { PasskeyCardAccess(store).open(card, "123456", false); fail("Unexpected success") } catch (_: IllegalArgumentException) {}
        assertEquals(0, card.pins); assertTrue(card.closed)
    }
    @Test fun existingCatalogNeedsPrivateUnwrapAndClearsSecret() = runBlocking {
        val store = MemoryStore(); store.save(PasskeyCatalog(), pair.public as RSAPublicKey)
        val card = Card()
        PasskeyCardAccess(store).open(card, "123456", false).use { assertTrue(it.catalog.records.isEmpty()) }
        assertEquals(1, card.pins); assertEquals(1, card.unwraps); assertEquals(0, card.probes)
        assertTrue(requireNotNull(card.recovered).all { it == 0.toByte() }); assertTrue(card.closed)
    }
    @Test fun incorrectPrivateProofCannotCreateCredentialsAndReleasesLock() = runBlocking {
        val store = MemoryStore(); val bad = Card().apply { wrong = true }
        try { PasskeyCardAccess(store).open(bad, "123456", true); fail("Unexpected success") } catch (_: IllegalStateException) {}
        assertTrue(bad.closed); assertTrue(requireNotNull(bad.recovered).all { it == 0.toByte() })
        withTimeout(10000) { PasskeyCardAccess(store).open(Card(), "123456", true).close() }
    }
    @Test fun cancellationReleasesLockAndClosesCard() = runBlocking {
        val card = Card().apply { cancelProbe = true }; val store = MemoryStore()
        try { PasskeyCardAccess(store).open(card, "123456", true); fail("Unexpected success") } catch (_: CancellationException) {}
        assertTrue(card.closed)
        withTimeout(10000) { PasskeyCardAccess(store).open(Card(), "123456", true).close() }
    }
    @Test fun concurrentCatalogUpdatesAreSerializedUntilCommitAndClose() = runBlocking {
        val store = MemoryStore(); val first = PasskeyCardAccess(store).open(Card(), "123456", true)
        val second = Card()
        val pending = async { PasskeyCardAccess(store).open(second, "123456", true) }
        yield(); assertEquals(0, second.pins)
        store.save(first.catalog, first.key); first.close(); first.close()
        withTimeout(10000) { pending.await().use { assertEquals(1, second.unwraps); assertEquals(0, second.probes) } }
    }
    @Test fun authenticationWithoutCatalogDoesNotSpendPinAttempt() = runBlocking {
        val card = Card()
        try { PasskeyCardAccess(MemoryStore()).open(card, "123456", false); fail("Unexpected success") } catch (_: IllegalArgumentException) {}
        assertEquals(0, card.pins); assertTrue(card.closed)
    }
    private fun expiredCertificate(): ByteArray {
        val name = X500Name("CN=Synthetic Expired Certificate")
        return JcaX509v3CertificateBuilder(name, BigInteger.TEN,
            Date(System.currentTimeMillis() - 172800000), Date(System.currentTimeMillis() - 86400000), name, pair.public)
            .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyEncipherment))
            .build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)).encoded
    }
    @Test fun expiredCertificateWithSamePrivateKeyStillRecoversHistoricalCatalog() = runBlocking {
        val store = MemoryStore(); store.save(PasskeyCatalog(), pair.public as RSAPublicKey)
        val card = Card().apply { der = expiredCertificate() }
        PasskeyCardAccess(store).open(card, "123456", false).use { assertTrue(it.catalog.records.isEmpty()) }
        assertEquals(1, card.unwraps); assertEquals(1, card.pins)
    }
    @Test fun expiredCertificateCannotEnrollNewProtectionOrCreateBeforeSpendingPin() = runBlocking {
        val card = Card().apply { der = expiredCertificate() }
        try { PasskeyCardAccess(MemoryStore()).open(card, "123456", true); fail("Expired certificate accepted") }
        catch (_: java.security.cert.CertificateExpiredException) {}
        assertEquals(0, card.pins); assertEquals(0, card.probes); assertTrue(card.closed)
    }
}
