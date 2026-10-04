package pe.gob.reniec.dnie.nfc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.gob.reniec.dnie.apdu.MockIsoDepCardChannel
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.apdu.StatusWord
import pe.gob.reniec.dnie.core.logging.ApduDirection
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.core.nfc.AppletNotFoundException
import pe.gob.reniec.dnie.core.nfc.CardDisconnectedException
import pe.gob.reniec.dnie.core.nfc.CryptoOperationException
import pe.gob.reniec.dnie.core.nfc.DnieTransceiver
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class DnieTransceiverTest {

    private lateinit var mockChannel: MockIsoDepCardChannel
    private lateinit var logger: ApduLogger
    private lateinit var transceiver: DnieTransceiver

    @Before
    fun setUp() {
        mockChannel = MockIsoDepCardChannel(
            pin = "123456",
            pinRetryCounter = 3,
            isCardBlocked = false,
            includeEncryptionCert = true,
            paddingBytesCount = 45 // 45 bytes de relleno EEPROM para probar recorte DER
        )
        logger = ApduLogger()
        transceiver = DnieTransceiver(
            channel = mockChannel,
            logger = logger,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    // --- 1. Selección de Applet PKI ---

    @Test
    fun testSelectPkiApplet_Success() = runTest {
        val result = transceiver.selectPkiApplet()

        assertTrue(result.isSuccess)
        assertTrue(mockChannel.isPkiAppletSelected)

        // Verificar logs generados
        val history = logger.getHistory()
        assertTrue(history.size >= 2)
        assertEquals(ApduDirection.TX, history[0].direction)
        assertEquals(ApduDirection.RX, history[1].direction)
        assertEquals("9000", history[1].statusWordHex)
    }

    @Test
    fun testSelectPkiApplet_CardNotDnie_ThrowsAppletNotFound() = runTest {
        // Tarjeta sin Applet PKI de RENIEC
        val invalidCard = object : pe.gob.reniec.dnie.core.nfc.DnieCardChannel {
            override fun transceive(command: ByteArray): ByteArray = byteArrayOf(0x6A.toByte(), 0x82.toByte())
            override fun isConnected(): Boolean = true
            override fun setTimeout(timeoutMs: Int) {}
            override fun close() {}
        }
        val customTransceiver = DnieTransceiver(invalidCard, logger, Dispatchers.Unconfined)
        val result = customTransceiver.selectPkiApplet()

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is AppletNotFoundException)
        assertEquals(0x6A82, (exception as AppletNotFoundException).statusWord)
    }

    // --- 2. Lectura Secuencial de Certificados X.509 y Recorte DER ---

    @Test
    fun testReadCertificate_Signature_MultiBlock_TrimPadding() = runTest {
        // Debe seleccionarse el applet primero
        transceiver.selectPkiApplet().getOrThrow()

        val certResult = transceiver.readCertificate(CertificateType.SIGNATURE)
        assertTrue(certResult.isSuccess)

        val cleanDer = certResult.getOrThrow()
        assertNotNull(cleanDer)

        // El certificado generado tiene típicamente entre 1000 y 1500 bytes (> 4 bloques de 228 bytes)
        assertTrue("El certificado debe ocupar múltiples bloques (> 500 bytes)", cleanDer.size > 500)

        // Verificar que es idéntico al certificado DER original sin los 45 bytes de padding 0xFF
        assertArrayEquals(mockChannel.signatureCertDer, cleanDer)

        // Parsear con CertificateFactory para comprobar integridad ASN.1 estricta
        val certFactory = CertificateFactory.getInstance("X.509")
        val parsedCert = certFactory.generateCertificate(ByteArrayInputStream(cleanDer)) as X509Certificate
        assertNotNull(parsedCert)
        assertTrue(parsedCert.subjectDN.name.contains("PEREZ LOPEZ JUAN CARLOS"))
        assertTrue(parsedCert.subjectDN.name.contains("45678901"))
    }

    @Test
    fun testReadCertificate_Authentication_Success() = runTest {
        transceiver.selectPkiApplet().getOrThrow()

        val certResult = transceiver.readCertificate(CertificateType.AUTHENTICATION)
        assertTrue(certResult.isSuccess)

        val cleanDer = certResult.getOrThrow()
        assertArrayEquals(mockChannel.authCertDer, cleanDer)

        val certFactory = CertificateFactory.getInstance("X.509")
        val parsedCert = certFactory.generateCertificate(ByteArrayInputStream(cleanDer)) as X509Certificate
        assertNotNull(parsedCert)
        assertTrue(parsedCert.subjectDN.name.contains("Autenticacion"))
    }

    @Test
    fun testReadCertificate_OptionalEncryptionMissing_HandlesGracefully() = runTest {
        // Tarjeta sin certificado de cifrado personalizado (00 1B)
        val cardWithoutEnc = MockIsoDepCardChannel(includeEncryptionCert = false)
        val trans = DnieTransceiver(cardWithoutEnc, logger, Dispatchers.Unconfined)
        trans.selectPkiApplet().getOrThrow()

        val encResult = trans.readCertificate(CertificateType.ENCRYPTION)
        assertTrue(encResult.isFailure)

        // Comprobar que readAllCertificates omite el opcional sin fallar
        val allResult = trans.readAllCertificates()
        assertTrue(allResult.isSuccess)
        val certsMap = allResult.getOrThrow()
        assertTrue(certsMap.containsKey(CertificateType.SIGNATURE))
        assertTrue(certsMap.containsKey(CertificateType.AUTHENTICATION))
        assertFalse(certsMap.containsKey(CertificateType.ENCRYPTION))
    }

    @Test
    fun testReadCertificate_FallbackToStandardReadBinary_Success() = runTest {
        // Simular tarjeta ICAO eMRTD / DNIe 3.0 que no soporta INS 0xB1 (READ BINARY DO)
        val icaoChannel = object : MockIsoDepCardChannel() {
            override fun transceive(command: ByteArray): ByteArray {
                val apdu = CommandApdu.fromBytes(command)
                if (apdu.ins == ApduConstants.INS_READ_BINARY_DO) {
                    return ResponseApdu.error(StatusWord.SW_INS_NOT_SUPPORTED).toByteArray()
                }
                return super.transceive(command)
            }
        }

        val trans = DnieTransceiver(icaoChannel, logger, Dispatchers.Unconfined, can = "123456")
        trans.selectPkiApplet().getOrThrow()

        val certResult = trans.readCertificate(CertificateType.SIGNATURE)
        assertTrue(certResult.isSuccess)
        assertArrayEquals(icaoChannel.signatureCertDer, certResult.getOrThrow())
    }

    // --- 3. Verificación de PIN en Tarjeta ---

    @Test
    fun testVerifyPin_CorrectPin_Success() = runTest {
        transceiver.selectPkiApplet().getOrThrow()

        val result = transceiver.verifyPin("123456")
        assertTrue(result.isSuccess)
        val pinResult = result.getOrThrow()

        assertTrue(pinResult is PinVerificationResult.Success)
        assertTrue(mockChannel.isPinVerified)
        assertEquals(3, mockChannel.pinRetryCounter)
    }

    @Test
    fun testVerifyPin_WrongPin_DecrementsCounter() = runTest {
        transceiver.selectPkiApplet().getOrThrow()

        // 1er intento fallido
        val res1 = transceiver.verifyPin("999999").getOrThrow()
        assertTrue(res1 is PinVerificationResult.InvalidPin)
        assertEquals(2, (res1 as PinVerificationResult.InvalidPin).attemptsRemaining)
        assertFalse(res1.isCritical)

        // 2do intento fallido (crítico: 1 intento restante)
        val res2 = transceiver.verifyPin("888888").getOrThrow()
        assertTrue(res2 is PinVerificationResult.InvalidPin)
        assertEquals(1, (res2 as PinVerificationResult.InvalidPin).attemptsRemaining)
        assertTrue(res2.isCritical)

        // 3er intento fallido (tarjeta se bloquea: 69 83)
        val res3 = transceiver.verifyPin("777777").getOrThrow()
        assertTrue(res3 is PinVerificationResult.CardBlocked)
        assertTrue(mockChannel.isCardBlocked)

        // Intento subsiguiente en tarjeta ya bloqueada
        val res4 = transceiver.verifyPin("123456").getOrThrow()
        assertTrue(res4 is PinVerificationResult.CardBlocked)
    }

    @Test
    fun testVerifyPin_InvalidFormat_ThrowsIllegalArgument() = runTest {
        // Menos de 4 dígitos
        val shortResult = transceiver.verifyPin("12")
        assertTrue(shortResult.isFailure)
        assertTrue(shortResult.exceptionOrNull() is IllegalArgumentException)

        // Contiene letras
        val alphaResult = transceiver.verifyPin("123a")
        assertTrue(alphaResult.isFailure)
        assertTrue(alphaResult.exceptionOrNull() is IllegalArgumentException)
    }

    // --- 4. Firma Digital RSA-2048 en Tarjeta (MSE + PSO CDS) ---

    @Test
    fun testSignSha256Hash_FullLifecycle_MathematicalVerification() = runTest {
        transceiver.selectPkiApplet().getOrThrow()

        // 1. Verificar PIN
        val pinRes = transceiver.verifyPin("123456").getOrThrow()
        assertTrue(pinRes is PinVerificationResult.Success)

        // 2. Preparar documento de prueba y calcular hash SHA-256 real
        val testDocument = "CONTRATO DIGITAL DE PRUEBA RENIEC 2026".toByteArray(Charsets.UTF_8)
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val documentHash = sha256Digest.digest(testDocument)
        assertEquals(32, documentHash.size)

        // 3. Ejecutar firma en hardware simulado (MSE + PSO CDS)
        val signResult = transceiver.signSha256Hash(documentHash)
        assertTrue(signResult.isSuccess)
        val signatureBytes = signResult.getOrThrow()

        // Verificar tamaño estricto de firma RSA-2048 (256 bytes)
        assertEquals(ApduConstants.RSA_2048_SIGNATURE_SIZE, signatureBytes.size)

        // 4. Verificación criptográfica matemática con la clave pública de la tarjeta
        val rsaVerifier = Signature.getInstance("SHA256withRSA")
        rsaVerifier.initVerify(mockChannel.signatureKeyPair.public)
        rsaVerifier.update(testDocument)
        val isSignatureValid = rsaVerifier.verify(signatureBytes)

        assertTrue("La firma RSA-2048 computada por la tarjeta debe ser criptográficamente válida", isSignatureValid)
    }

    @Test
    fun testSignSha256Hash_WithoutPin_FailsSecurityStatus() = runTest {
        transceiver.selectPkiApplet().getOrThrow()
        // No verificamos PIN

        val documentHash = ByteArray(32) { 0x11.toByte() }
        val signResult = transceiver.signSha256Hash(documentHash)

        assertTrue(signResult.isFailure)
        val ex = signResult.exceptionOrNull()
        assertTrue(ex is CryptoOperationException)
        assertEquals(0x6982, (ex as CryptoOperationException).statusWord)
    }

    @Test
    fun testSignSha256Hash_InvalidHashLength_FailsImmediately() = runTest {
        transceiver.selectPkiApplet().getOrThrow()
        transceiver.verifyPin("123456").getOrThrow()

        val invalidHash = ByteArray(16) // No son 32 bytes
        val signResult = transceiver.signSha256Hash(invalidHash)

        assertTrue(signResult.isFailure)
        assertTrue(signResult.exceptionOrNull() is IllegalArgumentException)
    }

    // --- 5. Manejo de Desconexión Física (TagLostException) ---

    @Test
    fun testCardDisconnectDuringOperation_ThrowsCardDisconnectedException() = runTest {
        transceiver.selectPkiApplet().getOrThrow()

        // Simular que el usuario retira el DNIe antes del siguiente comando
        mockChannel.throwOnNextTransceive = IOException("NFC tag lost")

        val result = transceiver.readCertificate(CertificateType.SIGNATURE)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }
}
