package pe.gob.reniec.dnie.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import pe.gob.reniec.dnie.core.nfc.VaultProbeCard
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult
import pe.gob.reniec.dnie.ui.vault.VaultViewModel
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.util.Date
import javax.crypto.Cipher

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var vm: VaultViewModel
    @Before fun setup() { Dispatchers.setMain(dispatcher); vm = VaultViewModel(dispatcher) }
    @After fun cleanup() { vm.cancel(); Dispatchers.resetMain() }
    private fun arm() { vm.updateCan("123456"); vm.updatePin("1234"); vm.startTest() }
    private class MemoryCanStore(var value: String) : pe.gob.reniec.dnie.domain.vault.RememberedCanStore {
        override suspend fun read() = value
        override suspend fun save(can: String) { value = can }
        override suspend fun clear() { value = "" }
    }

    @Test fun rememberedCanIsInheritedAndSurvivesCancellationWithoutKeepingPin() = runTest(dispatcher) {
        val store = MemoryCanStore("654321")
        vm = VaultViewModel(dispatcher, store)
        vm.refreshRememberedCan(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.rememberCan)
        assertEquals("654321", vm.uiState.value.can)
        vm.updatePin("1234"); vm.startTest(); vm.cancel()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("654321", vm.uiState.value.can)
        assertEquals("", vm.uiState.value.pin)
        assertFalse(vm.uiState.value.toString().contains("654321"))
        assertNull(vm.requestedCan)
        store.value = "111111"
        vm.refreshRememberedCan(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("111111", vm.uiState.value.can)
    }
    @Test fun optingOutClearsSharedPreferenceAndInvalidStoredCanIsRejected() = runTest(dispatcher) {
        val store = MemoryCanStore("123456")
        vm = VaultViewModel(dispatcher, store)
        vm.refreshRememberedCan(); dispatcher.scheduler.advanceUntilIdle()
        vm.setRememberCan(false); dispatcher.scheduler.advanceUntilIdle(); vm.cancel()
        assertEquals("", store.value)
        assertEquals("", vm.uiState.value.can)
        store.value = "１２３４５６"
        vm.refreshRememberedCan(); dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.rememberCan)
        assertEquals("", vm.uiState.value.can)
    }

    @Test fun invalidCredentialsCannotArmAndStateRedactsCredentials() {
        vm.updateCan("１２３４５６")
        vm.updatePin("1234")
        vm.startTest()
        assertFalse(vm.uiState.value.awaitingCard)
        assertNull(vm.requestedCan)
        assertFalse(vm.uiState.value.toString().contains("1234"))
    }

    @Test fun cancellationDropsCredentialsAndCannotRearmImplicitly() {
        arm()
        assertEquals("123456", vm.requestedCan)
        vm.updatePin("9999")
        assertEquals("1234", vm.uiState.value.pin)
        vm.cancel()
        assertNull(vm.requestedCan)
        assertEquals("", vm.uiState.value.pin)
        assertEquals("", vm.uiState.value.can)
        vm.startTest()
        assertFalse(vm.uiState.value.awaitingCard)
    }

    @Test fun unexpectedCardIsClosedWithoutStartingProbe() {
        val card = FakeCard()
        vm.onCardDetected(card)
        assertTrue(card.closed)
        assertEquals(0, card.selects)
    }

    @Test fun cancelledSessionCannotPublishLateResultsAndRepeatedTagIsRejected() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<Unit>>()
        val card = object : FakeCard() {
            override suspend fun selectPkiApplet(): Result<Unit> { selects++; return gate.await() }
        }
        arm()
        vm.onCardDetected(card)
        assertTrue(vm.uiState.value.running)
        assertEquals("", vm.uiState.value.pin)
        dispatcher.scheduler.runCurrent()
        val duplicate = FakeCard()
        vm.onCardDetected(duplicate)
        assertTrue(duplicate.closed)
        vm.cancel()
        gate.complete(Result.success(Unit))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(card.closed)
        assertFalse(vm.uiState.value.running)
        assertFalse(vm.uiState.value.verified)
        assertEquals(0, card.pins)
    }

    @Test fun selectionFailureClosesCardAndDoesNotTryPinOrDecrypt() = runTest(dispatcher) {
        val card = FakeCard()
        arm()
        vm.onCardDetected(card)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(card.closed)
        assertFalse(vm.uiState.value.running)
        assertFalse(vm.uiState.value.verified)
        assertEquals(0, card.pins)
        assertEquals("", vm.uiState.value.can)
        assertEquals("", vm.uiState.value.pin)
    }

    @Test fun unsupportedProfileStopsBeforeReadingCertificateOrVerifyingPin() = runTest(dispatcher) {
        val card = object : FakeCard() {
            override suspend fun selectPkiApplet() = Result.success(Unit)
        }
        arm()
        vm.onCardDetected(card)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.verified)
        assertEquals(0, card.pins)
        assertTrue(card.closed)
    }

    @Test fun fullProbeUsesEncryptionCertificateOnceAndClearsCredentials() = runTest(dispatcher) {
        val card = SyntheticCard()
        arm()
        vm.onCardDetected(card)
        val result = withTimeout(10000) { vm.uiState.first { !it.running } }
        assertTrue(result.verified)
        assertEquals(64, result.certificateFingerprint?.length)
        assertEquals("", result.can)
        assertEquals("", result.pin)
        assertEquals(1, card.pins)
        assertEquals(1, card.deciphers)
        assertTrue(card.closed)
    }

    @Test fun wrongPinStopsWithoutRetryOrDecrypt() = runTest(dispatcher) {
        val card = SyntheticCard(PinVerificationResult.InvalidPin(2))
        arm()
        vm.onCardDetected(card)
        val result = withTimeout(10000) { vm.uiState.first { !it.running } }
        assertFalse(result.verified)
        assertTrue(result.message!!.contains("2 intentos"))
        assertEquals(1, card.pins)
        assertEquals(0, card.deciphers)
        assertTrue(card.closed)
    }

    @Test fun sessionCanIsNotClearedOnLocalDiskOperation() = runTest(dispatcher) {
        val store = MemoryCanStore("")
        vm = VaultViewModel(dispatcher, store)
        vm.setRememberCan(false)
        vm.updateCan("654321")
        assertEquals("654321", vm.uiState.value.can)
        assertFalse(vm.uiState.value.rememberCan)
        vm.inspect("fake-id")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("654321", vm.uiState.value.can)
    }

    @Test fun organizeMissingIdPublishesControlledMessageWithoutCrashing() = runTest(dispatcher) {
        vm = VaultViewModel(dispatcher)
        vm.organize("non-existent-id", "Carpeta", listOf("tag1"))
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.running)
        assertEquals("El archivo ya no se encuentra en la bóveda.", vm.uiState.value.message)
    }

    private class SyntheticCard(private val pinResult: PinVerificationResult = PinVerificationResult.Success) : FakeCard() {
        var deciphers = 0
        companion object {
            private val pair by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
            private val certificate by lazy {
                val name = X500Name("CN=Synthetic Vault Test")
                JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date(0), Date(4102444800000L), name, pair.public)
                    .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyEncipherment))
                    .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider()).build(pair.private)).encoded
            }
        }
        override suspend fun selectPkiApplet() = Result.success(Unit)
        override fun requireVaultProbeSupport() = Result.success(Unit)
        override suspend fun readCertificate(type: CertificateType): Result<ByteArray> {
            assertEquals(CertificateType.ENCRYPTION, type)
            return Result.success(certificate)
        }
        override suspend fun verifyPin(pin: String): Result<PinVerificationResult> {
            assertEquals("1234", pin)
            pins++
            return Result.success(pinResult)
        }
        override suspend fun decipherTestSecret(ciphertext: ByteArray): Result<ByteArray> {
            deciphers++
            val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsa.init(Cipher.DECRYPT_MODE, pair.private)
            return Result.success(rsa.doFinal(ciphertext))
        }
    }

    private open class FakeCard : VaultProbeCard {
        var closed = false
        var selects = 0
        var pins = 0
        override suspend fun selectPkiApplet(): Result<Unit> { selects++; return Result.failure(IllegalStateException("No PKI")) }
        override suspend fun readCertificate(type: CertificateType): Result<ByteArray> = error("Unexpected read")
        override suspend fun verifyPin(pin: String): Result<PinVerificationResult> { pins++; error("Unexpected PIN") }
        override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> = error("Never sign")
        override suspend fun decipherTestSecret(ciphertext: ByteArray): Result<ByteArray> = error("Unexpected decrypt")
        override fun close() { closed = true }
    }
}
