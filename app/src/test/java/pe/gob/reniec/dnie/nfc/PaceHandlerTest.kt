package pe.gob.reniec.dnie.nfc

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.crypto.PaceProtocol
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.core.nfc.*
import java.math.BigInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class PaceHandlerTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val cardAccess = hex("31143012060A04007F0007020204040402010202010D")

    private inner class ImCard(private val tokenMode: Int = 0, private val v3Selection: Boolean = false) : DnieCardChannel {
        var step = 0
        var selectionStep = 0
        private val nonce = ByteArray(32) { (it + 1).toByte() }
        private var terminalNonce = byteArrayOf()
        private var terminalPublic = byteArrayOf()
        private var cardPublic = byteArrayOf()
        private var macKey = byteArrayOf()
        private var encKey = byteArrayOf()
        private fun response(tag: Int, data: ByteArray): ByteArray =
            byteArrayOf(0x7C, (data.size + 2).toByte(), tag.toByte(), data.size.toByte()) + data + hex("9000")

        override fun transceive(command: ByteArray): ByteArray {
            val cmd = CommandApdu.fromBytes(command)
            if (v3Selection && selectionStep < 2) {
                assertEquals(0xA4.toByte(), cmd.ins)
                assertEquals(12.toByte(), cmd.p2)
                assertArrayEquals(if (selectionStep++ == 0) V3PkiProbe.IAS_AID else V3PkiProbe.PKI_AID, cmd.data)
                return hex("9000")
            }
            return when (step++) {
                0 -> {
                    assertEquals(0x22, cmd.ins.toInt() and 255)
                    assertArrayEquals(ApduConstants.OID_PACE_ECDH_IM_AES256, cmd.data.copyOfRange(2, 12))
                    hex("9000")
                }
                1 -> {
                    assertArrayEquals(hex("10860000027C0000"), command)
                    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(PaceProtocol.deriveKpi("123456", 32), "AES"), IvParameterSpec(ByteArray(16)))
                    response(0x80, cipher.doFinal(nonce))
                }
                2 -> {
                    assertEquals(0x10, cmd.cla.toInt() and 255)
                    assertEquals(0x81, cmd.data[2].toInt() and 255)
                    assertEquals(32, cmd.data[3].toInt() and 255)
                    terminalNonce = cmd.data.copyOfRange(4, cmd.data.size)
                    response(0x82, byteArrayOf())
                }
                3 -> {
                    assertEquals(0x83, cmd.data[2].toInt() and 255)
                    assertEquals(65, cmd.data[3].toInt() and 255)
                    terminalPublic = cmd.data.copyOfRange(4, cmd.data.size)
                    val group = PaceProtocol.computeIntegratedMappedGenerator(nonce, terminalNonce, 13)
                    val privateKey = BigInteger.valueOf(37)
                    cardPublic = group.mappedGenerator.multiply(privateKey).normalize().getEncoded(false)
                    val shared = group.curve.decodePoint(terminalPublic).multiply(privateKey).normalize()
                    val sharedX = PaceProtocol.bigIntegerToFixedBytes(shared.affineXCoord.toBigInteger(), 32)
                    val keys = PaceProtocol.deriveSessionKeys(sharedX, isAes256 = true)
                    macKey = keys.kMac
                    encKey = keys.kEnc
                    response(0x84, cardPublic)
                }
                4 -> {
                    assertEquals(0, cmd.cla.toInt())
                    assertEquals(0x85, cmd.data[2].toInt() and 255)
                    assertArrayEquals(PaceProtocol.computeAuthenticationToken(macKey,
                        ApduConstants.OID_PACE_ECDH_IM_AES256, cardPublic), cmd.data.copyOfRange(4, cmd.data.size))
                    when (tokenMode) {
                        1 -> hex("7C009000")
                        2 -> response(0x86, ByteArray(8))
                        else -> response(0x86, PaceProtocol.computeAuthenticationToken(macKey,
                            ApduConstants.OID_PACE_ECDH_IM_AES256, terminalPublic))
                    }
                }
                5 -> {
                    check(v3Selection)
                    assertEquals(0x0C.toByte(), cmd.cla)
                    assertEquals(0xA4.toByte(), cmd.ins)
                    val counter = ByteArray(16).also { it[15] = 1 }
                    val ecb = Cipher.getInstance("AES/ECB/NoPadding")
                    ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"))
                    val cbc = Cipher.getInstance("AES/CBC/NoPadding")
                    cbc.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(ecb.doFinal(counter)))
                    assertEquals(0x87.toByte(), cmd.data[0])
                    val plain = cbc.doFinal(cmd.data.copyOfRange(3, 2 + (cmd.data[1].toInt() and 255)))
                    assertArrayEquals(V3PkiProbe.PKI_AID, plain.copyOfRange(0, 14))
                    val status = hex("99029000")
                    counter[15] = 2
                    val mac = PaceProtocol.computeCmac(macKey, counter + status + hex("80") + ByteArray(11))
                    status + hex("8E08") + mac + hex("9000")
                }
                else -> error("Unexpected APDU after PACE")
            }
        }
        override fun isConnected() = true
        override fun setTimeout(timeoutMs: Int) {}
        override fun close() {}
    }

    @Test fun imAes256CompletesAllWireStepsAndAuthenticatesChip() {
        val card = ImCard()
        val secure = PaceHandler(ApduLogger()).performPace(card, "123456", cardAccess)
        assertTrue(secure.isConnected())
        assertEquals(5, card.step)
    }

    @Test fun explicitIasProfileAuthenticatesWithoutSwitchingToMfOrCardAccess() {
        val card = ImCard()
        val secure = PaceHandler(ApduLogger()).performPace(card, "123456",
            profileSecurityInfo = V3PkiSession.paceProfile)
        assertTrue(secure.isConnected())
        assertEquals(5, card.step)
    }

    @Test fun transceiverEstablishesV3AndReselectsPkiUsingNegotiatedSecureKeys() = runTest {
        val card = ImCard(v3Selection = true)
        val reader = DnieTransceiver(card, ApduLogger(), Dispatchers.Unconfined, can = "123456")
        reader.selectPkiApplet().getOrThrow()
        assertEquals("DNIe v3 IAS/PKI", reader.activeAppletName)
        assertFalse(reader.isIcaoMode)
        assertEquals(2, card.selectionStep)
        assertEquals(6, card.step)
    }

    @Test fun missingCardTokenFailsEvenWith9000() {
        assertThrows(PaceException::class.java) {
            PaceHandler(ApduLogger()).performPace(ImCard(1), "123456", cardAccess)
        }
    }

    @Test fun incorrectCardTokenFails() {
        assertThrows(PaceException::class.java) {
            PaceHandler(ApduLogger()).performPace(ImCard(2), "123456", cardAccess)
        }
    }
}
