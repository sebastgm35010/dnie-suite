package pe.gob.reniec.dnie.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult
import pe.gob.reniec.dnie.ui.certificates.CertificatesUiState
import pe.gob.reniec.dnie.ui.certificates.CertificatesViewModel
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class CertificatesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CertificatesViewModel

    companion object {
        private lateinit var sampleCertBytes: ByteArray

        @BeforeClass
        @JvmStatic
        fun initCert() {
            CryptoProviderInitializer.init()
            val kpg = KeyPairGenerator.getInstance("RSA", CryptoProviderInitializer.provider)
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()

            val notBefore = Date(System.currentTimeMillis() - 86400000L)
            val notAfter = Date(System.currentTimeMillis() + 365L * 86400000L)

            val builder = X509v3CertificateBuilder(
                X500Name("CN=RENIEC CA, O=RENIEC, C=PE"),
                BigInteger(64, SecureRandom()),
                notBefore,
                notAfter,
                X500Name("CN=JUAN PEREZ (FIRMA), SERIALNUMBER=12345678, C=PE, O=RENIEC"),
                SubjectPublicKeyInfo.getInstance(kp.public.encoded)
            )
            builder.addExtension(
                Extension.keyUsage,
                true,
                KeyUsage(KeyUsage.digitalSignature or KeyUsage.nonRepudiation)
            )

            val signer = JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(CryptoProviderInitializer.provider)
                .build(kp.private)
            val cert: X509Certificate = JcaX509CertificateConverter()
                .setProvider(CryptoProviderInitializer.provider)
                .getCertificate(builder.build(signer))
            sampleCertBytes = cert.encoded
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CertificatesViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateIsIdle() {
        assertTrue(viewModel.uiState.value is CertificatesUiState.Idle)
    }

    @Test
    fun testStartReadingTransitionsToReadingCard() {
        viewModel.startReading()
        assertTrue(viewModel.uiState.value is CertificatesUiState.ReadingCard)
    }

    @Test
    fun testCancelReadingReturnsToIdle() {
        viewModel.startReading()
        assertTrue(viewModel.uiState.value is CertificatesUiState.ReadingCard)
        viewModel.cancelReading()
        assertTrue(viewModel.uiState.value is CertificatesUiState.Idle)
    }

    @Test
    fun testOnCardDetectedSuccessLoadsCertificates() = runTest(testDispatcher) {
        val mockTransceiver = object : DnieTransceiverInterface {
            override suspend fun selectPkiApplet(): Result<Unit> = Result.success(Unit)

            override suspend fun readCertificate(type: CertificateType): Result<ByteArray> {
                return if (type == CertificateType.ENCRYPTION) {
                    Result.failure(Exception("Not found"))
                } else {
                    Result.success(sampleCertBytes)
                }
            }

            override suspend fun verifyPin(pin: String): Result<PinVerificationResult> =
                Result.success(PinVerificationResult.Success)

            override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> =
                Result.success(ByteArray(256))
        }

        viewModel.onCardDetected(mockTransceiver)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should be Loaded but was $state", state is CertificatesUiState.Loaded)
        val loaded = state as CertificatesUiState.Loaded

        assertTrue(loaded.certificates.containsKey(CertificateType.SIGNATURE))
        assertTrue(loaded.certificates.containsKey(CertificateType.AUTHENTICATION))
        assertFalse(loaded.certificates.containsKey(CertificateType.ENCRYPTION))

        val sigCert = loaded.certificates[CertificateType.SIGNATURE]
        assertNotNull(sigCert)
        assertEquals("JUAN PEREZ", sigCert!!.citizenName)
        assertEquals("12345678", sigCert.citizenDni)
        assertEquals(CertificateType.SIGNATURE, loaded.selectedType)

        // Test tab switching
        viewModel.selectTab(CertificateType.AUTHENTICATION)
        val updatedLoaded = viewModel.uiState.value as CertificatesUiState.Loaded
        assertEquals(CertificateType.AUTHENTICATION, updatedLoaded.selectedType)

        // Test exporting .cer
        val cerOut = ByteArrayOutputStream()
        val cerExported = viewModel.exportCertificate(CertificateType.SIGNATURE, cerOut, asPem = false)
        assertTrue(cerExported)
        assertTrue(cerOut.toByteArray().isNotEmpty())
        assertEquals(sampleCertBytes.size, cerOut.toByteArray().size)

        // Test exporting .pem
        val pemOut = ByteArrayOutputStream()
        val pemExported = viewModel.exportCertificate(CertificateType.SIGNATURE, pemOut, asPem = true)
        assertTrue(pemExported)
        val pemString = pemOut.toString(Charsets.US_ASCII.name())
        assertTrue(pemString.contains("-----BEGIN CERTIFICATE-----"))
        assertTrue(pemString.contains("-----END CERTIFICATE-----"))

        // Test reset
        viewModel.reset()
        assertTrue(viewModel.uiState.value is CertificatesUiState.Idle)
    }

    @Test
    fun testOnCardDetectedFailureTransitionsToError() = runTest(testDispatcher) {
        val failingTransceiver = object : DnieTransceiverInterface {
            override suspend fun selectPkiApplet(): Result<Unit> =
                Result.failure(Exception("Tarjeta no es un DNIe de RENIEC"))

            override suspend fun readCertificate(type: CertificateType): Result<ByteArray> =
                Result.failure(Exception("Error"))

            override suspend fun verifyPin(pin: String): Result<PinVerificationResult> =
                Result.failure(Exception("Error"))

            override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> =
                Result.failure(Exception("Error"))
        }

        viewModel.onCardDetected(failingTransceiver)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should be Error but was $state", state is CertificatesUiState.Error)
        val err = state as CertificatesUiState.Error
        assertTrue(err.message.contains("Tarjeta no es un DNIe"))
    }
}
