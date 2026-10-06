package pe.gob.reniec.dnie.nfc

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.crypto.PaceProtocol
import pe.gob.reniec.dnie.core.nfc.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class PaceSecureChannelTest {
    @Test fun extendedDecipherWireAuthenticatesLengthAndEncryptsEntireRsaBlock() {
        val ciphertext = ByteArray(256) { it.toByte() }
        val wire = PaceSecureChannel(base, key, key).wrapCommand(V3PkiSession.decipher(ciphertext))
        val parsed = CommandApdu.fromBytes(wire)
        assertEquals("0C2A8086000123", parsed.toHexString("").take(14))
        assertEquals(65536, parsed.le)
        assertTrue(parsed.isExtended)
        val data = parsed.data
        assertArrayEquals(byteArrayOf(0x87.toByte(), 0x82.toByte(), 1, 0x11, 1), data.copyOfRange(0, 5))
        assertArrayEquals(byteArrayOf(0x97.toByte(), 2, 1, 0), data.copyOfRange(277, 281))
        val counter = ByteArray(16).also { it[15] = 1 }
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val cbc = Cipher.getInstance("AES/CBC/NoPadding")
        cbc.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ecb.doFinal(counter)))
        assertArrayEquals(byteArrayOf(0x81.toByte()) + ciphertext + byteArrayOf(0x80.toByte()) + ByteArray(14),
            cbc.doFinal(data.copyOfRange(5, 277)))
        val header = byteArrayOf(0x0C, 0x2A, 0x80.toByte(), 0x86.toByte(), 0x80.toByte()) + ByteArray(11)
        val macInput = counter + header + data.copyOfRange(0, 281) + byteArrayOf(0x80.toByte()) + ByteArray(6)
        val cmac = org.bouncycastle.crypto.macs.CMac(org.bouncycastle.crypto.engines.AESEngine.newInstance())
        cmac.init(org.bouncycastle.crypto.params.KeyParameter(key))
        cmac.update(macInput, 0, macInput.size)
        val independentMac = ByteArray(16).also { cmac.doFinal(it, 0) }
        assertArrayEquals(independentMac.copyOf(8), data.copyOfRange(283, 291))
    }

    @Test fun extendedPrivateCommandStillAuthenticatesResponseAndRejectsBadMac() {
        val card = object : DnieCardChannel by base {
            override fun transceive(command: ByteArray) = protectedStatus(0x9000, tampered = true)
        }
        val secure = PaceSecureChannel(card, key, key)
        assertThrows(CardProtocolException::class.java) {
            secure.transceive(V3PkiSession.decipher(ByteArray(256)).toByteArray())
        }
        assertThrows(IllegalStateException::class.java) {
            secure.transceive(V3PkiSession.sign(ByteArray(32)).toByteArray())
        }
    }
    private val key = ByteArray(32) { it.toByte() }
    private val base = object : DnieCardChannel {
        override fun transceive(command: ByteArray) = byteArrayOf(0x90.toByte(), 0)
        override fun isConnected() = true
        override fun setTimeout(timeoutMs: Int) {}
        override fun close() {}
    }

    private fun protectedStatus(sw: Int, outerSw: Int = sw, tampered: Boolean = false, counter: Int = 2): ByteArray {
        val status = byteArrayOf(0x99.toByte(), 2, (sw shr 8).toByte(), sw.toByte())
        val ssc = ByteArray(16).also { it[15] = counter.toByte() }
        val mac = PaceProtocol.computeCmac(key, ssc + status + byteArrayOf(0x80.toByte()) + ByteArray(11))
        if (tampered) mac[0] = (mac[0].toInt() xor 1).toByte()
        return status + byteArrayOf(0x8E.toByte(), 8) + mac + byteArrayOf((outerSw shr 8).toByte(), outerSw.toByte())
    }

    private fun protectedSignature(signature: ByteArray): ByteArray {
        val counter = ByteArray(16).also { it[15] = 2 }
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val cbc = Cipher.getInstance("AES/CBC/NoPadding")
        cbc.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ecb.doFinal(counter)))
        val padded = signature + byteArrayOf(0x80.toByte()) + ByteArray(15)
        val do87 = byteArrayOf(0x87.toByte(), 0x82.toByte(), 1, 0x11, 1) + cbc.doFinal(padded)
        val objects = do87 + byteArrayOf(0x99.toByte(), 2, 0x90.toByte(), 0)
        val macInput = counter + objects
        val padding = ByteArray(16 - macInput.size % 16).also { it[0] = 0x80.toByte() }
        return objects + byteArrayOf(0x8E.toByte(), 8) + PaceProtocol.computeCmac(key, macInput + padding) +
            byteArrayOf(0x90.toByte(), 0)
    }

    @Test fun rsaResponse6123IsCollectedBeforeMacValidationAndDoesNotAdvanceSsc() {
        val signature = ByteArray(256) { it.toByte() }
        val envelope = protectedSignature(signature)
        assertEquals(293, envelope.size) // 291 bytes of protected data plus final status
        var calls = 0
        val card = object : DnieCardChannel by base {
            override fun transceive(command: ByteArray): ByteArray = when (++calls) {
                1 -> envelope.copyOfRange(0, 256) + byteArrayOf(0x61, 0x23)
                2 -> {
                    assertArrayEquals(byteArrayOf(0, 0xC0.toByte(), 0, 0, 0x23), command)
                    envelope.copyOfRange(256, envelope.size)
                }
                3 -> protectedStatus(0x9000, counter = 4)
                else -> error("Unexpected physical APDU")
            }
        }
        val secure = PaceSecureChannel(card, key, key)
        assertArrayEquals(signature + byteArrayOf(0x90.toByte(), 0), secure.transceive(
            V3PkiSession.sign(ByteArray(32)).toByteArray()))
        assertArrayEquals(byteArrayOf(0x90.toByte(), 0), secure.transceive(CommandApdu.selectMasterFile().toByteArray()))
        assertEquals(3, calls)
    }

    @Test fun collectsMultipleFragmentsIncluding6100() {
        val envelope = protectedSignature(ByteArray(256))
        var calls = 0
        val card = object : DnieCardChannel by base {
            override fun transceive(command: ByteArray): ByteArray = when (++calls) {
                1 -> envelope.copyOfRange(0, 20) + byteArrayOf(0x61, 0)
                2 -> {
                    assertEquals(256, CommandApdu.fromBytes(command).le)
                    envelope.copyOfRange(20, 256) + byteArrayOf(0x61, 0x23)
                }
                else -> envelope.copyOfRange(256, envelope.size)
            }
        }
        assertEquals(258, PaceSecureChannel(card, key, key).transceive(V3PkiSession.sign(ByteArray(32)).toByteArray()).size)
        assertEquals(3, calls)
    }

    @Test fun tamperedContinuationIsRejectedAndSessionInvalidated() {
        val envelope = protectedSignature(ByteArray(256))
        envelope[280] = (envelope[280].toInt() xor 1).toByte()
        var calls = 0
        val card = object : DnieCardChannel by base {
            override fun transceive(command: ByteArray) = if (++calls == 1)
                envelope.copyOfRange(0, 256) + byteArrayOf(0x61, 0x23)
                else envelope.copyOfRange(256, envelope.size)
        }
        val secure = PaceSecureChannel(card, key, key)
        assertThrows(CardProtocolException::class.java) { secure.transceive(V3PkiSession.sign(ByteArray(32)).toByteArray()) }
        assertThrows(IllegalStateException::class.java) { secure.transceive(CommandApdu.selectMasterFile().toByteArray()) }
    }

    @Test fun missingContinuationBytesCannotBeAcceptedAsSignature() {
        val envelope = protectedSignature(ByteArray(256))
        var calls = 0
        val card = object : DnieCardChannel by base {
            override fun transceive(command: ByteArray) = if (++calls == 1)
                envelope.copyOfRange(0, 256) + byteArrayOf(0x61, 0x23) else byteArrayOf(0x90.toByte(), 0)
        }
        assertThrows(CardProtocolException::class.java) {
            PaceSecureChannel(card, key, key).transceive(V3PkiSession.sign(ByteArray(32)).toByteArray())
        }
    }

    @Test fun endlessContinuationIsBounded() {
        var calls = 0
        val card = object : DnieCardChannel by base {
            override fun transceive(command: ByteArray): ByteArray { calls++; return byteArrayOf(0x61, 1) }
        }
        assertThrows(CardProtocolException::class.java) {
            PaceSecureChannel(card, key, key).transceive(CommandApdu.selectMasterFile().toByteArray())
        }
        assertEquals(65, calls)
    }

    @Test fun authenticatedFileNotFoundCanRepeatExternalStatus() {
        val channel = PaceSecureChannel(base, key, key)
        channel.wrapCommand(CommandApdu.selectMasterFile())
        assertArrayEquals(byteArrayOf(0x6A, 0x82.toByte()), channel.unwrapResponse(protectedStatus(0x6A82)))
    }

    @Test fun authenticatedErrorCanUseStandardExternal9000() {
        val channel = PaceSecureChannel(base, key, key)
        channel.wrapCommand(CommandApdu.selectMasterFile())
        assertArrayEquals(byteArrayOf(0x69, 0x82.toByte()), channel.unwrapResponse(protectedStatus(0x6982, 0x9000)))
    }

    @Test fun repeatedExternalErrorCannotBypassMacValidation() {
        val channel = PaceSecureChannel(base, key, key)
        channel.wrapCommand(CommandApdu.selectMasterFile())
        assertThrows(CardProtocolException::class.java) { channel.unwrapResponse(protectedStatus(0x6A82, tampered = true)) }
    }

    @Test fun mismatchedOuterAndAuthenticatedStatusIsRejected() {
        val channel = PaceSecureChannel(base, key, key)
        channel.wrapCommand(CommandApdu.selectMasterFile())
        assertThrows(CardProtocolException::class.java) { channel.unwrapResponse(protectedStatus(0x6A82, 0x6982)) }
    }

    @Test fun authenticatedErrorPreservesSessionForNextCommand() {
        val card = object : DnieCardChannel {
            private var calls = 0
            override fun transceive(command: ByteArray): ByteArray {
                calls++
                return if (calls == 1) protectedStatus(0x6A82)
                    else protectedStatus(0x9000, counter = calls * 2)
            }
            override fun isConnected() = true
            override fun setTimeout(timeoutMs: Int) {}
            override fun close() {}
        }
        val channel = PaceSecureChannel(card, key, key)
        assertArrayEquals(byteArrayOf(0x6A, 0x82.toByte()), channel.transceive(CommandApdu.selectMasterFile().toByteArray()))
        assertArrayEquals(byteArrayOf(0x90.toByte(), 0), channel.transceive(CommandApdu.selectMasterFile().toByteArray()))
    }

    @Test fun acceptsAuthenticatedStatusAndAdvancesSsc() {
        val channel = PaceSecureChannel(base, key, key)
        channel.wrapCommand(CommandApdu.selectMasterFile())
        val ssc = ByteArray(16).also { it[15] = 2 }
        val status = byteArrayOf(0x99.toByte(), 2, 0x90.toByte(), 0)
        val input = ssc + status + byteArrayOf(0x80.toByte()) + ByteArray(11)
        val mac = PaceProtocol.computeCmac(key, input)
        assertArrayEquals(byteArrayOf(0x90.toByte(), 0), channel.unwrapResponse(
            status + byteArrayOf(0x8E.toByte(), 8) + mac + byteArrayOf(0x90.toByte(), 0)))
    }

    @Test fun rejectsMissingMac() {
        assertThrows(CardProtocolException::class.java) {
            PaceSecureChannel(base, key, key).unwrapResponse(byteArrayOf(0x99.toByte(), 2, 0x90.toByte(), 0, 0x90.toByte(), 0))
        }
    }

    @Test fun rejectsForgedMac() {
        assertThrows(CardProtocolException::class.java) {
            PaceSecureChannel(base, key, key).unwrapResponse(byteArrayOf(0x99.toByte(), 2, 0x90.toByte(), 0, 0x8E.toByte(), 8) + ByteArray(8) + byteArrayOf(0x90.toByte(), 0))
        }
    }

    @Test fun unprotectedResponseInvalidatesSession() {
        val channel = PaceSecureChannel(base, key, key)
        assertThrows(CardProtocolException::class.java) { channel.transceive(CommandApdu.selectMasterFile().toByteArray()) }
        assertThrows(IllegalStateException::class.java) { channel.transceive(CommandApdu.selectMasterFile().toByteArray()) }
    }
}
