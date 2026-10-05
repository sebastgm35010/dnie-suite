package pe.gob.reniec.dnie.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.crypto.PaceProtocol
import pe.gob.reniec.dnie.core.crypto.PaceSecurityInfo
import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class PaceProtocolTest {

    @Before
    fun setUp() {
        CryptoProviderInitializer.init()
    }

    @Test
    fun testParseCardAccess_ValidBsiPaceInfo() {
        // Trama real leída del DNIe 3.0: Tag 0x53, len 0x44 (68 bytes)
        // Set (0x31) -> Sequence (0x30) -> OID (0.4.0.127.0.7.2.2.4.2.2), Version (2), ParamId (13)
        val rawCardAccess = byteArrayOf(
            0x53.toByte(), 0x18.toByte(),
            0x31.toByte(), 0x16.toByte(),
            0x30.toByte(), 0x14.toByte(),
            0x06.toByte(), 0x0A.toByte(), 0x04.toByte(), 0x00.toByte(),
            0x7F.toByte(), 0x00.toByte(), 0x07.toByte(), 0x02.toByte(),
            0x02.toByte(), 0x04.toByte(), 0x02.toByte(), 0x02.toByte(),
            0x02.toByte(), 0x01.toByte(), 0x02.toByte(), // Version 2
            0x02.toByte(), 0x01.toByte(), 0x0D.toByte()  // ParamId 13 (BrainpoolP256r1)
        )

        val secInfo = PaceProtocol.parseCardAccess(rawCardAccess)
        assertNotNull("PaceSecurityInfo no debe ser nulo", secInfo)
        assertEquals("0.4.0.127.0.7.2.2.4.2.2", secInfo!!.oid)
        assertEquals(2, secInfo.version)
        assertEquals(13, secInfo.parameterId)
    }

    @Test
    fun testDeriveKpi_And_DecryptNonce() {
        val can = "123456"
        val kPi = PaceProtocol.deriveKpi(can, 16)
        assertEquals(16, kPi.size)

        // Cifrar un Nonce aleatorio de 16 bytes con AES-128-CBC IV=0
        val originalNonce = ByteArray(16)
        SecureRandom().nextBytes(originalNonce)

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kPi, "AES"), IvParameterSpec(ByteArray(16)))
        val encryptedNonce = cipher.doFinal(originalNonce)

        // Descifrar con PaceProtocol
        val decryptedNonce = PaceProtocol.decryptNonce(can, encryptedNonce)
        assertArrayEquals("El Nonce descifrado debe coincidir exactamente", originalNonce, decryptedNonce)
    }

    @Test
    fun testCurveResolution_BrainpoolP256r1_And_Secp256r1() {
        val brainpool = PaceProtocol.getCurveParameters(13)
        assertNotNull(brainpool)
        assertEquals(256, brainpool.curve.fieldSize)

        val secp = PaceProtocol.getCurveParameters(12)
        assertNotNull(secp)
        assertEquals(256, secp.curve.fieldSize)
    }

    @Test
    fun testPaceGenericMapping_And_KeyAgreement_EndToEnd() {
        val parameterId = 13 // BrainpoolP256r1
        val params = PaceProtocol.getCurveParameters(parameterId)
        val curve = params.curve
        val g = params.g
        val n = params.n

        // Nonce compartido (16 bytes)
        val nonce = ByteArray(16)
        SecureRandom().nextBytes(nonce)
        val s = BigInteger(1, nonce)

        // 1. Fase de Mapeo (Generic Mapping):
        // Terminal genera mapping key
        val terminalMapping = PaceProtocol.generateMappingKey(parameterId)

        // Simular que la Tarjeta genera su mapping key
        val cardX2 = BigInteger(n.bitLength() - 1, SecureRandom()).add(BigInteger.ONE)
        val cardY2 = g.multiply(cardX2).normalize()
        val cardY2Bytes = cardY2.getEncoded(false)

        // Terminal calcula G' a partir de cardY2 y nonce
        val terminalMappedGroup = PaceProtocol.computeMappedGenerator(
            mappingPair = terminalMapping,
            cardMappingPubBytes = cardY2Bytes,
            nonceBytes = nonce,
            parameterId = parameterId
        )

        // Tarjeta calcula G' a partir de terminalY1 y nonce:
        // H = cardX2 * terminalY1
        // G'_card = s * G + H
        val cardH = terminalMapping.publicPoint.multiply(cardX2).normalize()
        val cardGPrime = g.multiply(s).add(cardH).normalize()

        // Ambos generadores mapeados deben ser idénticos
        assertEquals("El generador mapeado del terminal y la tarjeta deben coincidir",
            terminalMappedGroup.mappedGenerator, cardGPrime)

        // 2. Fase de Acuerdo de Claves Efímeras (Key Agreement sobre G'):
        val terminalEphemeral = PaceProtocol.generateEphemeralKey(terminalMappedGroup)

        // Tarjeta genera su clave efímera sobre G'
        val cardX4 = BigInteger(n.bitLength() - 1, SecureRandom()).add(BigInteger.ONE)
        val cardY4 = cardGPrime.multiply(cardX4).normalize()
        val cardY4Bytes = cardY4.getEncoded(false)

        // Terminal calcula Kx
        val terminalKx = PaceProtocol.computeSharedSecretX(
            ephemeralPair = terminalEphemeral,
            cardEphemeralPubBytes = cardY4Bytes,
            mappedGroup = terminalMappedGroup
        )

        // Tarjeta calcula K = cardX4 * terminalY3
        val cardK = terminalEphemeral.publicPoint.multiply(cardX4).normalize()
        val cardKx = PaceProtocol.bigIntegerToFixedBytes(cardK.affineXCoord.toBigInteger(), 32)

        assertArrayEquals("El secreto compartido Kx debe coincidir exactamente", cardKx, terminalKx)

        // 3. Derivación de claves de sesión (K_Enc, K_Mac)
        val terminalSessionKeys = PaceProtocol.deriveSessionKeys(terminalKx)
        val cardSessionKeys = PaceProtocol.deriveSessionKeys(cardKx)

        assertArrayEquals("K_Enc debe coincidir", cardSessionKeys.kEnc, terminalSessionKeys.kEnc)
        assertArrayEquals("K_Mac debe coincidir", cardSessionKeys.kMac, terminalSessionKeys.kMac)

        // 4. Autenticación Mutua (Token MAC)
        val oidValueBytes = ApduConstants.OID_PACE_ECDH_GM_AES128

        // Terminal calcula su token T_PCD sobre la clave de la tarjeta (cardY4Bytes)
        val tPcd = PaceProtocol.computeAuthenticationToken(
            terminalSessionKeys.kMac,
            oidValueBytes,
            cardY4Bytes
        )
        // Tarjeta verifica T_PCD calculándolo con su propia clave K_Mac
        val tPcdCardCheck = PaceProtocol.computeAuthenticationToken(
            cardSessionKeys.kMac,
            oidValueBytes,
            cardY4Bytes
        )
        assertArrayEquals("El token T_PCD debe ser verificado por la tarjeta", tPcdCardCheck, tPcd)

        // Tarjeta calcula su token T_PICC sobre la clave del terminal (terminalEphemeral.publicPointBytes)
        val tPicc = PaceProtocol.computeAuthenticationToken(
            cardSessionKeys.kMac,
            oidValueBytes,
            terminalEphemeral.publicPointBytes
        )
        // Terminal verifica T_PICC
        val tPiccTerminalCheck = PaceProtocol.computeAuthenticationToken(
            terminalSessionKeys.kMac,
            oidValueBytes,
            terminalEphemeral.publicPointBytes
        )
        assertArrayEquals("El token T_PICC debe ser verificado por el terminal", tPiccTerminalCheck, tPicc)
    }

    @Test
    fun testAuthenticationTokenData_Structure() {
        val oidBytes = byteArrayOf(0x04, 0x00, 0x7F, 0x00, 0x07, 0x02, 0x02, 0x04, 0x02, 0x02)
        val pubKeyBytes = ByteArray(65) { 0x05 }
        val tokenData = PaceProtocol.buildAuthenticationTokenData(oidBytes, pubKeyBytes)

        assertTrue(tokenData.size > 70)
        assertEquals(0x7F.toByte(), tokenData[0])
        assertEquals(0x49.toByte(), tokenData[1])
    }

    @Test
    fun testComputeIntegratedMappedGenerator() {
        // ICAO Doc 9303 Part 11 Appendix H.1, independent published IM vector.
        fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val nonce = hex("2923BE84E16CD6AE529049F1F1BBE9EB")
        val terminalNonce = hex("5DD4CBFC96F5453B130D890A1CDBAE32")
        val p = PaceProtocol.getCurveParameters(13).curve.field.characteristic
        assertEquals(BigInteger("A2F8FF2DF50E52C6599F386ADCB595D229F6A167ADE2BE5F2C3296ADD5B7430E", 16),
            PaceProtocol.integratedMappingFieldElement(nonce, terminalNonce, p))
        val group = PaceProtocol.computeIntegratedMappedGenerator(nonce, terminalNonce, 13)
        assertArrayEquals(hex("048E82D31559ED0FDE92A4D0498ADD3C23BABA94FB77691E31E90AEA77FB17D427" +
            "4C1AE14BD0C3DBAC0C871B7F3608169364437CA30AC243A089D3F266C1E60FAD"),
            group.mappedGenerator.getEncoded(false))
    }

    @Test
    fun testRealCardAccessAdvertisesImAes256() {
        val bytes = "534431423012060A04007F0007020204040402010202010D3015060904007F000702020C01300302010130030301003015060904007F000702020C0230030201023003030100"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val info = PaceProtocol.parseCardAccess(bytes)!!
        assertEquals("0.4.0.127.0.7.2.2.4.4.4", info.oid)
        assertTrue(info.isIntegratedMapping)
        assertEquals(32, info.keyLength)
        assertEquals(13, info.parameterId)
    }

    @Test
    fun testDecryptLongNonceUsesNegotiatedAes128() {
        val nonce = ByteArray(32) { it.toByte() }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(PaceProtocol.deriveKpi("123456"), "AES"), IvParameterSpec(ByteArray(16)))
        assertArrayEquals(nonce, PaceProtocol.decryptNonce("123456", cipher.doFinal(nonce)))
    }

    @Test
    fun testDeriveSessionKeys_Aes256() {
        val dummyKx = ByteArray(32) { 0x42 }
        val keys = PaceProtocol.deriveSessionKeys(dummyKx, isAes256 = true)

        assertEquals(32, keys.kEnc.size)
        assertEquals(32, keys.kMac.size)
    }

    @Test
    fun testPaceSecurityInfo_Flags() {
        val gmInfo = PaceSecurityInfo(
            oid = "0.4.0.127.0.7.2.2.4.2.2",
            oidValueBytes = ApduConstants.OID_PACE_ECDH_GM_AES128,
            version = 2,
            parameterId = 13
        )
        assertTrue(gmInfo.isGenericMapping)
        assertTrue(!gmInfo.isIntegratedMapping)
        assertTrue(!gmInfo.isAes256)

        val imInfo = PaceSecurityInfo(
            oid = "0.4.0.127.0.7.2.2.4.4.4",
            oidValueBytes = ApduConstants.OID_PACE_ECDH_IM_AES256,
            version = 2,
            parameterId = 13
        )
        assertTrue(!imInfo.isGenericMapping)
        assertTrue(imInfo.isIntegratedMapping)
        assertTrue(imInfo.isAes256)
    }
}
