package pe.gob.reniec.dnie.ui

import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult
import pe.gob.reniec.dnie.pdf.PadesSignerEngine
import pe.gob.reniec.dnie.pdf.SignaturePreset
import pe.gob.reniec.dnie.ui.signer.SignerUiState
import pe.gob.reniec.dnie.ui.signer.SignerViewModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.crypto.Cipher

@OptIn(ExperimentalCoroutinesApi::class)
class SignerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: SignerViewModel

    companion object {
        private lateinit var keyPair: KeyPair
        private lateinit var signingCertBytes: ByteArray

        @BeforeClass
        @JvmStatic
        fun initKeys() {
            CryptoProviderInitializer.init()
            val kpg = KeyPairGenerator.getInstance("RSA", CryptoProviderInitializer.provider)
            kpg.initialize(2048)
            keyPair = kpg.generateKeyPair()

            val notBefore = Date(System.currentTimeMillis() - 86400000L)
            val notAfter = Date(System.currentTimeMillis() + 365L * 86400000L)

            val builder = X509v3CertificateBuilder(
                X500Name("CN=RENIEC ECP, O=RENIEC, C=PE"),
                BigInteger(64, SecureRandom()),
                notBefore,
                notAfter,
                X500Name("CN=QUISPE MAMANI\\, CARLOS ENRIQUE (FIRMA), SERIALNUMBER=47851234, C=PE, O=RENIEC"),
                SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
            )
            builder.addExtension(
                Extension.keyUsage,
                true,
                KeyUsage(KeyUsage.digitalSignature or KeyUsage.nonRepudiation)
            )

            val signer = JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(CryptoProviderInitializer.provider)
                .build(keyPair.private)
            val cert: X509Certificate = JcaX509CertificateConverter()
                .setProvider(CryptoProviderInitializer.provider)
                .getCertificate(builder.build(signer))
            signingCertBytes = cert.encoded
        }

        fun mockSignHash(sha256Digest: ByteArray): ByteArray {
            val digestInfo = ByteArray(ApduConstants.PSO_CDS_PAYLOAD_SIZE)
            System.arraycopy(ApduConstants.SHA256_DIGEST_INFO_PREFIX, 0, digestInfo, 0, 19)
            System.arraycopy(sha256Digest, 0, digestInfo, 19, 32)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", CryptoProviderInitializer.provider)
            cipher.init(Cipher.ENCRYPT_MODE, keyPair.private)
            return cipher.doFinal(digestInfo)
        }

        fun createSamplePdf(): ByteArray {
            val doc = PDDocument()
            doc.addPage(PDPage(PDRectangle.A4))
            val baos = ByteArrayOutputStream()
            doc.save(baos)
            doc.close()
            return baos.toByteArray()
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SignerViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateIsNoDocument() {
        assertTrue(viewModel.uiState.value is SignerUiState.NoDocument)
    }
    @Test fun handwrittenConfirmationIsClearedWhenTheSourceIdentityChanges() {
        viewModel.setHandwrittenMark(pe.gob.reniec.dnie.domain.signing.HandwrittenMark(byteArrayOf(1, 2), "Titular uno"))
        viewModel.confirmMark(true)
        assertTrue(viewModel.workspace.value.markConfirmed)
        viewModel.setHandwrittenMark(pe.gob.reniec.dnie.domain.signing.HandwrittenMark(byteArrayOf(1, 2), "Titular dos"))
        assertTrue(!viewModel.workspace.value.markConfirmed)
    }

    @Test
    fun testDocumentSelectionAndPresetConfiguration() {
        viewModel.onDocumentSelected(
            uri = null,
            name = "contrato.pdf",
            sizeBytes = 245000L,
            pageCount = 2
        )

        val state = viewModel.uiState.value
        assertTrue(state is SignerUiState.DocumentLoaded)
        val loaded = state as SignerUiState.DocumentLoaded
        assertEquals("contrato.pdf", loaded.documentName)
        assertEquals(245000L, loaded.documentSizeBytes)
        assertEquals(SignaturePreset.BOTTOM_RIGHT, loaded.preset)

        viewModel.onPresetSelected(SignaturePreset.BOTTOM_LEFT)
        val updatedPreset = (viewModel.uiState.value as SignerUiState.DocumentLoaded).preset
        assertEquals(SignaturePreset.BOTTOM_LEFT, updatedPreset)

        viewModel.onReasonChanged("Doy conformidad")
        assertEquals("Doy conformidad", (viewModel.uiState.value as SignerUiState.DocumentLoaded).reason)

        viewModel.onLocationChanged("Cusco, Perú")
        assertEquals("Cusco, Perú", (viewModel.uiState.value as SignerUiState.DocumentLoaded).location)
    }

    @Test
    fun testTwoStepPinFlowStateTransitions() {
        viewModel.onDocumentSelected(uri = null, name = "acta.pdf", sizeBytes = 10000L, pageCount = 1)

        // Paso 1: Abrir ingreso de credenciales
        viewModel.startPinEntry()
        assertTrue(viewModel.uiState.value is SignerUiState.EnteringPin)

        // Cancelar regresa a DocumentLoaded
        viewModel.cancelPinEntry()
        assertTrue(viewModel.uiState.value is SignerUiState.DocumentLoaded)

        // Reintentar y confirmar con CAN y PIN
        viewModel.startPinEntry()
        viewModel.onCredentialsConfirmed(can = "654321", pin = "5678")
        assertTrue(viewModel.uiState.value is SignerUiState.AwaitingCard)
        val awaiting = viewModel.uiState.value as SignerUiState.AwaitingCard
        assertEquals("5678", awaiting.pin)
        assertEquals("654321", awaiting.can)
        assertEquals("654321", viewModel.enteredCan)

        // Cancelar en espera de tarjeta regresa a DocumentLoaded
        viewModel.cancelAwaitingCard()
        assertTrue(viewModel.uiState.value is SignerUiState.DocumentLoaded)

        // Probar también onPinConfirmed
        viewModel.startPinEntry()
        viewModel.onPinConfirmed("1234")
        val awaiting2 = viewModel.uiState.value as SignerUiState.AwaitingCard
        assertEquals("1234", awaiting2.pin)
        assertEquals("654321", awaiting2.can)
    }

    @Test
    fun testSigningSuccessWithMockTransceiver() = runTest(testDispatcher) {
        viewModel.onDocumentSelected(uri = null, name = "documento.pdf", sizeBytes = 12345L, pageCount = 1)
        viewModel.startPinEntry()
        viewModel.onPinConfirmed("1234")

        val mockTransceiver = object : DnieTransceiverInterface {
            override suspend fun selectPkiApplet(): Result<Unit> = Result.success(Unit)

            override suspend fun readCertificate(type: CertificateType): Result<ByteArray> =
                Result.success(signingCertBytes)

            override suspend fun verifyPin(pin: String): Result<PinVerificationResult> =
                Result.success(PinVerificationResult.Success)

            override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> =
                Result.success(mockSignHash(hash32Bytes))
        }

        val pdfBytes = createSamplePdf()
        val inStream = ByteArrayInputStream(pdfBytes)
        val outStream = ByteArrayOutputStream()

        viewModel.onCardDetected(mockTransceiver, inStream, outStream)
        var attempts = 0
        while (viewModel.uiState.value is SignerUiState.Signing && attempts < 100) {
            Thread.sleep(25)
            testDispatcher.scheduler.advanceUntilIdle()
            attempts++
        }

        val state = viewModel.uiState.value
        assertTrue("State should be SignedSuccess but was $state", state is SignerUiState.SignedSuccess)
        val success = state as SignerUiState.SignedSuccess

        assertTrue(success.signerName.contains("QUISPE MAMANI"))
        assertEquals("47851234", success.signerDni)
        assertTrue(success.signatureSize > 0)
        assertTrue(outStream.toByteArray().isNotEmpty())
    }

    @Test
    fun testInvalidPinTransitionsToError() = runTest(testDispatcher) {
        viewModel.onDocumentSelected(uri = null, name = "documento.pdf", sizeBytes = 12345L, pageCount = 1)
        viewModel.startPinEntry()
        viewModel.onPinConfirmed("9999")

        val mockTransceiver = object : DnieTransceiverInterface {
            override suspend fun selectPkiApplet(): Result<Unit> = Result.success(Unit)

            override suspend fun readCertificate(type: CertificateType): Result<ByteArray> =
                Result.success(signingCertBytes)

            override suspend fun verifyPin(pin: String): Result<PinVerificationResult> =
                Result.success(PinVerificationResult.InvalidPin(attemptsRemaining = 2))

            override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> =
                Result.success(ByteArray(256))
        }

        val inStream = ByteArrayInputStream(createSamplePdf())
        val outStream = ByteArrayOutputStream()

        viewModel.onCardDetected(mockTransceiver, inStream, outStream)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should be Error but was $state", state is SignerUiState.Error)
        val err = state as SignerUiState.Error
        assertTrue(err.message.contains("2 intento(s)"))
    }

    @Test
    fun testBlockedCardTransitionsToError() = runTest(testDispatcher) {
        viewModel.onDocumentSelected(uri = null, name = "documento.pdf", sizeBytes = 12345L, pageCount = 1)
        viewModel.startPinEntry()
        viewModel.onPinConfirmed("1111")

        val mockTransceiver = object : DnieTransceiverInterface {
            override suspend fun selectPkiApplet(): Result<Unit> = Result.success(Unit)

            override suspend fun readCertificate(type: CertificateType): Result<ByteArray> =
                Result.success(signingCertBytes)

            override suspend fun verifyPin(pin: String): Result<PinVerificationResult> =
                Result.success(PinVerificationResult.CardBlocked)

            override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> =
                Result.success(ByteArray(256))
        }

        val inStream = ByteArrayInputStream(createSamplePdf())
        val outStream = ByteArrayOutputStream()

        viewModel.onCardDetected(mockTransceiver, inStream, outStream)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should be Error but was $state", state is SignerUiState.Error)
        val err = state as SignerUiState.Error
        assertTrue(err.message.contains("bloqueado"))
    }
}
