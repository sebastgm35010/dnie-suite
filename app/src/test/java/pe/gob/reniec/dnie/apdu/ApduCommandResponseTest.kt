package pe.gob.reniec.dnie.apdu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.apdu.StatusWord

class ApduCommandResponseTest {

    // --- CommandApdu Tests ---

    @Test
    fun testCommandApduCase1_HeaderOnly() {
        val cmd = CommandApdu(0x00.toByte(), 0xA4.toByte(), 0x00.toByte(), 0x00.toByte())
        val bytes = cmd.toByteArray()

        assertEquals(4, bytes.size)
        assertArrayEquals(byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x00), bytes)
        assertEquals("00 A4 00 00", cmd.toHexString())

        val parsed = CommandApdu.fromBytes(bytes)
        assertEquals(cmd, parsed)
    }

    @Test
    fun testCommandApduCase2_WithLe() {
        val cmd = CommandApdu(0x00.toByte(), 0xB0.toByte(), 0x00.toByte(), 0x00.toByte(), le = 256)
        val bytes = cmd.toByteArray()

        assertEquals(5, bytes.size)
        // Le=256 se codifica como 0x00
        assertArrayEquals(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x00), bytes)

        val parsed = CommandApdu.fromBytes(bytes)
        assertEquals(256, parsed.le)
        assertEquals(0xB0.toByte(), parsed.ins)
    }

    @Test
    fun testCommandApduCase3_WithDataNoLe() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val cmd = CommandApdu(0x00.toByte(), 0x20.toByte(), 0x00.toByte(), 0x81.toByte(), data)
        val bytes = cmd.toByteArray()

        assertEquals(4 + 1 + 4, bytes.size)
        assertEquals(0x04.toByte(), bytes[4]) // Lc
        assertArrayEquals(
            byteArrayOf(0x00, 0x20, 0x00, 0x81.toByte(), 0x04, 0x01, 0x02, 0x03, 0x04),
            bytes
        )

        val parsed = CommandApdu.fromBytes(bytes)
        assertArrayEquals(data, parsed.data)
        assertNull(parsed.le)
    }

    @Test
    fun testCommandApduCase4_WithDataAndLe() {
        val data = byteArrayOf(0x54, 0x02, 0x00, 0xE4.toByte())
        val cmd = CommandApdu(0x00.toByte(), 0xB1.toByte(), 0x00.toByte(), 0x00.toByte(), data, le = 0xFF)
        val bytes = cmd.toByteArray()

        assertEquals(4 + 1 + 4 + 1, bytes.size)
        assertEquals(0x04.toByte(), bytes[4]) // Lc
        assertEquals(0xFF.toByte(), bytes[bytes.size - 1]) // Le

        val parsed = CommandApdu.fromBytes(bytes)
        assertArrayEquals(data, parsed.data)
        assertEquals(0xFF, parsed.le)
    }

    @Test
    fun testFactorySelectReniecPkiApplet() {
        val cmd = CommandApdu.selectReniecPkiApplet()
        assertEquals(0x00.toByte(), cmd.cla)
        assertEquals(0xA4.toByte(), cmd.ins)
        assertEquals(0x04.toByte(), cmd.p1)
        assertEquals(0x00.toByte(), cmd.p2)
        assertArrayEquals(ApduConstants.AID_RENIEC_PKI, cmd.data)
        assertEquals(14, cmd.data.size)
        assertEquals(
            "00 A4 04 00 0E E8 28 BD 08 0F D2 50 47 65 6E 65 72 69 63",
            cmd.toHexString()
        )
    }

    @Test
    fun testFactorySelectEf() {
        val cmd = CommandApdu.selectEf(ApduConstants.EF_SIGNATURE)
        assertEquals(0x00.toByte(), cmd.cla)
        assertEquals(0xA4.toByte(), cmd.ins)
        assertEquals(0x02.toByte(), cmd.p1)
        assertEquals(0x04.toByte(), cmd.p2)
        assertArrayEquals(byteArrayOf(0x00, 0x1D), cmd.data)
        assertEquals("00 A4 02 04 02 00 1D", cmd.toHexString())
    }

    @Test
    fun testFactoryReadBinaryWithOffset() {
        val cmd = CommandApdu.readBinaryWithOffset(0x00E4, le = 0xFF)
        assertEquals(0xB1.toByte(), cmd.ins)
        assertArrayEquals(byteArrayOf(0x54, 0x02, 0x00, 0xE4.toByte()), cmd.data)
        assertEquals(0xFF, cmd.le)
        assertEquals("00 B1 00 00 04 54 02 00 E4 FF", cmd.toHexString())
    }

    @Test
    fun testFactoryVerifyPin() {
        val cmd = CommandApdu.verifyPin("123456", ApduConstants.PIN_TYPE_SIGNATURE)
        assertEquals(0x20.toByte(), cmd.ins)
        assertEquals(0x81.toByte(), cmd.p2)
        assertArrayEquals("123456".toByteArray(Charsets.US_ASCII), cmd.data)
        assertEquals("00 20 00 81 06 31 32 33 34 35 36", cmd.toHexString())
    }

    @Test
    fun testFactoryMseSetDst() {
        val cmd = CommandApdu.mseSetDst()
        assertEquals(0x22.toByte(), cmd.ins)
        assertEquals(0x41.toByte(), cmd.p1)
        assertEquals(0xB6.toByte(), cmd.p2)
        assertArrayEquals(ApduConstants.MSE_SET_DST_DATA, cmd.data)
        assertEquals("00 22 41 B6 06 80 01 8A 84 01 81", cmd.toHexString())
    }

    @Test
    fun testFactoryPsoComputeDigitalSignature() {
        val dummyHash = ByteArray(32) { 0xAA.toByte() }
        val cmd = CommandApdu.psoComputeDigitalSignature(dummyHash)

        assertEquals(0x2A.toByte(), cmd.ins)
        assertEquals(0x9E.toByte(), cmd.p1)
        assertEquals(0x9A.toByte(), cmd.p2)
        assertEquals(51, cmd.data.size)

        // Verificar prefijo ASN.1 de 19 bytes
        val prefix = cmd.data.copyOfRange(0, 19)
        assertArrayEquals(ApduConstants.SHA256_DIGEST_INFO_PREFIX, prefix)

        // Verificar hash de 32 bytes
        val hash = cmd.data.copyOfRange(19, 51)
        assertArrayEquals(dummyHash, hash)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPsoWithInvalidHashLengthThrowsException() {
        val invalidHash = ByteArray(31) // No tiene 32 bytes
        CommandApdu.psoComputeDigitalSignature(invalidHash)
    }

    // --- ResponseApdu Tests ---

    @Test
    fun testResponseApduSuccess() {
        val raw = byteArrayOf(0x01, 0x02, 0x90.toByte(), 0x00)
        val resp = ResponseApdu.fromBytes(raw)

        assertEquals(0x9000, resp.sw)
        assertEquals(0x90, resp.sw1)
        assertEquals(0x00, resp.sw2)
        assertTrue(resp.isSuccess)
        assertFalse(resp.isWarning)
        assertFalse(resp.isError)
        assertEquals("9000", resp.statusWordHex)
        assertArrayEquals(byteArrayOf(0x01, 0x02), resp.data)
        assertEquals("01 02", resp.dataToHexString())
        assertEquals("01 02 90 00", resp.toHexString())
    }

    @Test
    fun testResponseApduWarningPinRemaining() {
        val raw = byteArrayOf(0x63.toByte(), 0xC2.toByte()) // 2 intentos restantes
        val resp = ResponseApdu.fromBytes(raw)

        assertEquals(0x63C2, resp.sw)
        assertFalse(resp.isSuccess)
        assertTrue(resp.isWarning)
        assertFalse(resp.isError)
        assertTrue(resp.statusWord is StatusWord.PinRetryCounter)
        assertEquals(2, (resp.statusWord as StatusWord.PinRetryCounter).attemptsRemaining)
    }

    @Test
    fun testResponseApduErrorBlocked() {
        val raw = byteArrayOf(0x69.toByte(), 0x83.toByte())
        val resp = ResponseApdu.fromBytes(raw)

        assertEquals(0x6983, resp.sw)
        assertFalse(resp.isSuccess)
        assertFalse(resp.isWarning)
        assertTrue(resp.isError)
        assertTrue(resp.statusWord is StatusWord.AuthenticationBlocked)
    }

    // --- StatusWord Mapping Tests ---

    @Test
    fun testStatusWordMappings() {
        assertEquals(StatusWord.Ok, StatusWord.fromSw(0x9000))
        assertEquals(StatusWord.EofReached, StatusWord.fromSw(0x6282))
        assertEquals(StatusWord.WrongLength, StatusWord.fromSw(0x6700))
        assertEquals(StatusWord.SecurityStatusNotSatisfied, StatusWord.fromSw(0x6982))
        assertEquals(StatusWord.AuthenticationBlocked, StatusWord.fromSw(0x6983))
        assertEquals(StatusWord.ConditionsOfUseNotSatisfied, StatusWord.fromSw(0x6985))
        assertEquals(StatusWord.FileNotFound, StatusWord.fromSw(0x6A82))
        assertEquals(StatusWord.IncorrectParametersP1P2, StatusWord.fromSw(0x6A86))
        assertEquals(StatusWord.ReferencedDataNotFound, StatusWord.fromSw(0x6A88))
        assertEquals(StatusWord.WrongOffset, StatusWord.fromSw(0x6B00))
        assertEquals(StatusWord.InstructionNotSupported, StatusWord.fromSw(0x6D00))
        assertEquals(StatusWord.ClassNotSupported, StatusWord.fromSw(0x6E00))

        // Contadores 63C0 .. 63CF
        val swPin3 = StatusWord.fromSw(0x63C3)
        assertTrue(swPin3 is StatusWord.PinRetryCounter)
        assertEquals(3, (swPin3 as StatusWord.PinRetryCounter).attemptsRemaining)

        val swPin1 = StatusWord.fromSw(0x63C1)
        assertTrue(swPin1 is StatusWord.PinRetryCounter)
        assertEquals(1, (swPin1 as StatusWord.PinRetryCounter).attemptsRemaining)

        // Desconocido
        val unknown = StatusWord.fromSw(0x6F00)
        assertTrue(unknown is StatusWord.Unknown)
        assertEquals(0x6F00, unknown.code)
    }
}
