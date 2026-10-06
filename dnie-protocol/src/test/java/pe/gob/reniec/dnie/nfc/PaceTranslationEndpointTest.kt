package pe.gob.reniec.dnie.nfc

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.crypto.PaceSecurityInfo
import pe.gob.reniec.dnie.core.nfc.*
import pe.gob.reniec.dnie.core.logging.ApduLogger

class PaceTranslationEndpointTest {
    private fun decode(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val plainIas = decode("00A4040C10A000000077030C60000000FE00000500")
    private val plainPki = decode("00A4040C0EE828BD080FD25047656E65726963")
    private val plainCertificate = decode("00A4020402CE82")
    private val plainRead = decode("00B00000FF")
    private val profile = PaceSecurityInfo("0.4.0.127.0.7.2.2.4.4.4", byteArrayOf(4,0,0x7f,0,7,2,2,4,4,4),2,13)
    private inner class Fixture : AutoCloseable {
        val forwarded = mutableListOf<ByteArray>()
        var result = byteArrayOf(0x90.toByte(),0)
        var omitDomain = false
        val endpoint = PaceTranslationEndpoint("123456") { forwarded.add(it.copyOf()); result.copyOf() }
        val wire = object : DnieCardChannel {
            override fun transceive(command: ByteArray): ByteArray {
                val parsed = CommandApdu.fromBytes(command)
                val observed = if (omitDomain && parsed.cla == 0.toByte() && parsed.ins == 0x22.toByte() &&
                    parsed.p1 == 0xc1.toByte() && parsed.p2 == 0xa4.toByte()) {
                    CommandApdu(parsed.cla,parsed.ins,parsed.p1,parsed.p2,
                        parsed.data.copyOfRange(0,parsed.data.size-3)).toByteArray()
                } else command
                return endpoint.transmit(observed)
            }
            override fun isConnected() = true
            override fun setTimeout(timeoutMs: Int) {}
            override fun close() = endpoint.close()
        }
        fun authenticate(can: String = "123456") = PaceHandler(ApduLogger(enabled=false)).performPace(wire,can,profileSecurityInfo=profile)
        override fun close() = endpoint.close()
    }
    @Test fun reniecImplicitDomainCompletesMutualPaceAndProtectedRead() {
        Fixture().use { f ->
            f.omitDomain = true
            val pc = f.authenticate()
            assertTrue(f.forwarded.isEmpty())
            val read = CommandApdu(0,0xb0.toByte(),0,0,le=16).toByteArray()
            assertArrayEquals(f.result,pc.transceive(read))
            assertArrayEquals(read,f.forwarded.single())
        }
    }
    @Test fun implicitDomainDoesNotAllowWrongCanToAuthenticate() {
        Fixture().use { f ->
            f.omitDomain = true
            assertThrows(PaceException::class.java) { f.authenticate("654321") }
            assertTrue(f.forwarded.isEmpty())
        }
    }
    @Test fun otherDomainPasswordOidAndUnknownObjectsRemainRejected() {
        Fixture().use { f ->
            val base = SmTlv.encode(0x80,profile.oidValueBytes) + SmTlv.encode(0x83,byteArrayOf(2))
            val variants = listOf(
                base + SmTlv.encode(0x84,byteArrayOf(12)),
                SmTlv.encode(0x80,profile.oidValueBytes) + SmTlv.encode(0x83,byteArrayOf(1)),
                SmTlv.encode(0x80,byteArrayOf(1)) + SmTlv.encode(0x83,byteArrayOf(2)),
                base + SmTlv.encode(0x85,byteArrayOf(13)),
                base + SmTlv.encode(0x83,byteArrayOf(2))
            )
            variants.forEach { data ->
                val command = CommandApdu(0,0x22,0xc1.toByte(),0xa4.toByte(),data).toByteArray()
                assertArrayEquals(byteArrayOf(0x6a,0x80.toByte()),f.endpoint.transmit(command))
            }
            assertTrue(f.forwarded.isEmpty())
        }
    }
    @Test fun fullMutualPaceTerminatesBeforeLogicalCardThenTranslatesBothDirections() {
        Fixture().use { f ->
            val terminal = f.authenticate()
            assertTrue(f.forwarded.isEmpty())
            val command = CommandApdu(0,0xa4.toByte(),2,4,byteArrayOf(1,0x1c),le=256).toByteArray()
            f.result = ByteArray(107) { it.toByte() } + byteArrayOf(0x90.toByte(),0)
            repeat(3) { assertArrayEquals(f.result, terminal.transceive(command)) }
            assertEquals(3,f.forwarded.size)
            f.forwarded.forEach { assertArrayEquals(command,it) }
        }
    }
    @Test fun plainPublicCertificatesUseIndependentPhysicalSmAndKeepPcCounter() {
        Fixture().use { chip ->
            val physical = chip.authenticate()
            PaceTranslationEndpoint("123456",physical::transceive).use { endpoint ->
                val wire = object : DnieCardChannel {
                    override fun transceive(command: ByteArray) = endpoint.transmit(command)
                    override fun isConnected() = true
                    override fun setTimeout(timeoutMs: Int) {}
                    override fun close() {}
                }
                val pc = PaceHandler(ApduLogger(enabled=false)).performPace(wire,"123456",profileSecurityInfo=profile)
                val protectedRead = CommandApdu(0,0xb0.toByte(),0,0,le=16).toByteArray()
                assertArrayEquals(chip.result,pc.transceive(protectedRead))
                listOf(plainIas,plainPki,plainCertificate).forEach { assertArrayEquals(chip.result,endpoint.transmit(it)) }
                chip.result=byteArrayOf(1,2,3,0x62,0x82.toByte())
                assertArrayEquals(chip.result,endpoint.transmit(plainRead))
                assertArrayEquals(chip.result,pc.transceive(protectedRead))
                assertEquals(6,chip.forwarded.size)
                assertArrayEquals(plainRead,chip.forwarded[4])
                // Protected activity invalidates the plain file-selection state.
                assertArrayEquals(byteArrayOf(0x69,0x85.toByte()),endpoint.transmit(plainRead))
            }
        }
    }
    @Test fun plaintextPinSignWriteAndOtherFilesRemainBlockedAfterPace() {
        Fixture().use { f ->
            val pc=f.authenticate()
            val blocked=listOf(plainRead,plainPki,plainCertificate,
                decode("0020000103313233"),decode("002A9E9A0100"),decode("00D600000100"),
                decode("00A4040C07A0000002471001"),decode("00A40204024F00"))
            blocked.forEach { assertArrayEquals(byteArrayOf(0x69,0x85.toByte()),f.endpoint.transmit(it)) }
            assertTrue(f.forwarded.isEmpty())
            // Also reject sensitive commands while a public certificate is selected.
            val sensitive=blocked.drop(3)
            sensitive.forEach { command ->
                listOf(plainIas,plainPki,plainCertificate).forEach { f.endpoint.transmit(it) }
                val before=f.forwarded.size
                assertArrayEquals(byteArrayOf(0x69,0x85.toByte()),f.endpoint.transmit(command))
                assertEquals(before,f.forwarded.size)
            }
            assertArrayEquals(f.result,pc.transceive(CommandApdu(0,0xb0.toByte(),0,0,le=16).toByteArray()))
        }
    }
    @Test fun failedPublicSelectionCannotOpenCertificateReadRoute() {
        Fixture().use { f ->
            f.authenticate()
            f.result=byteArrayOf(0x6a,0x82.toByte())
            assertArrayEquals(f.result,f.endpoint.transmit(plainIas))
            assertArrayEquals(byteArrayOf(0x69,0x85.toByte()),f.endpoint.transmit(plainPki))
            assertArrayEquals(byteArrayOf(0x69,0x85.toByte()),f.endpoint.transmit(plainRead))
            assertEquals(1,f.forwarded.size)
        }
    }
    @Test fun newPaceIsAssignedANewContextAndObserverFailureDoesNotBreakAuthentication() {
        Fixture().use { f ->
            val contexts = mutableListOf<String>()
            f.endpoint.onContext = { event,context -> if(event=="pc_pace_ready") contexts.add(context) }
            f.authenticate()
            f.authenticate()
            assertEquals(2,contexts.size)
            assertNotEquals(contexts[0],contexts[1])
            f.endpoint.onContext = { _,_ -> throw IllegalStateException("Broken logger") }
            f.authenticate()
        }
    }
    @Test fun authenticatedApplicationErrorAndExtendedPayloadSurviveTranslation() {
        Fixture().use { f ->
            val terminal = f.authenticate()
            val command = CommandApdu(0,0x2a,0x80.toByte(),0x86.toByte(),ByteArray(257){it.toByte()},le=65536,extended=true).toByteArray()
            f.result=byteArrayOf(0x69,0x82.toByte())
            assertArrayEquals(f.result,terminal.transceive(command))
            assertArrayEquals(command,f.forwarded.single())
            f.result=ByteArray(256){it.toByte()} + byteArrayOf(0x90.toByte(),0)
            assertArrayEquals(f.result,terminal.transceive(CommandApdu(0,0xb0.toByte(),0,0,le=256).toByteArray()))
        }
    }
    @Test fun wrongCanCannotAuthenticateAndNeverTouchesPhysicalChannel() {
        Fixture().use { f ->
            assertThrows(PaceException::class.java) { f.authenticate("654321") }
            assertTrue(f.forwarded.isEmpty())
            f.authenticate()
            assertTrue(f.forwarded.isEmpty())
        }
    }
    @Test fun alteredMacIsRejectedBeforeAnyLogicalTransmit() {
        Fixture().use { f ->
            val terminal=f.authenticate()
            val sm=terminal.wrapCommand(CommandApdu(0,0xb0.toByte(),0,0,le=16))
            sm[sm.size-2]=(sm[sm.size-2].toInt() xor 1).toByte()
            assertThrows(IllegalArgumentException::class.java) { f.endpoint.transmit(sm) }
            assertTrue(f.forwarded.isEmpty())
        }
    }
    @Test fun replayCounterIsRejected() {
        Fixture().use { f ->
            val terminal=f.authenticate()
            val sm=terminal.wrapCommand(CommandApdu(0,0xb0.toByte(),0,0,le=16))
            val response=f.endpoint.transmit(sm)
            terminal.unwrapResponse(response)
            assertThrows(IllegalArgumentException::class.java) { f.endpoint.transmit(sm) }
            assertEquals(1,f.forwarded.size)
        }
    }
    @Test fun twoIndependentEncryptedSessionsCarryExactLogicalCommands() {
        Fixture().use { chip ->
            val physical = chip.authenticate()
            val pcEndpoint = PaceTranslationEndpoint("123456", physical::transceive)
            val pcWire = object : DnieCardChannel {
                override fun transceive(command: ByteArray) = pcEndpoint.transmit(command)
                override fun isConnected() = true
                override fun setTimeout(timeoutMs: Int) {}
                override fun close() = pcEndpoint.close()
            }
            pcEndpoint.use {
                val pc = PaceHandler(ApduLogger(enabled=false)).performPace(pcWire,"123456",profileSecurityInfo=profile)
                chip.result = ByteArray(256){it.toByte()} + byteArrayOf(0x90.toByte(),0)
                val read = CommandApdu(0,0xb0.toByte(),0,0,le=256).toByteArray()
                repeat(4){assertArrayEquals(chip.result,pc.transceive(read))}
                assertEquals(4,chip.forwarded.size)
                chip.forwarded.forEach { assertArrayEquals(read,it) }
            }
        }
    }
    @Test fun nestedPaceCannotRekeyPhysicalSessionAndOldCountersRemainUsable() {
        Fixture().use { f ->
            val pc = f.authenticate()
            val request = CommandApdu.mseSetAtPace(profile.oidValueBytes,13,2).toByteArray()
            assertArrayEquals(byteArrayOf(0x6a,0x80.toByte()),pc.transceive(request))
            assertTrue(f.forwarded.isEmpty())
            assertArrayEquals(f.result,pc.transceive(CommandApdu(0,0xb0.toByte(),0,0,le=16).toByteArray()))
            assertEquals(1,f.forwarded.size)
        }
    }
    @Test fun oddGetDataUsesEncryptedDo85InBothDirectionsAndContinuesCounters() {
        Fixture().use { chip ->
            val physical = chip.authenticate()
            PaceTranslationEndpoint("123456", physical::transceive).use { endpoint ->
                val wire = object : DnieCardChannel {
                    override fun transceive(command: ByteArray) = endpoint.transmit(command)
                    override fun isConnected() = true
                    override fun setTimeout(timeoutMs: Int) {}
                    override fun close() {}
                }
                val pc = PaceHandler(ApduLogger(enabled=false)).performPace(wire,"123456",profileSecurityInfo=profile)
                val request = CommandApdu(0,0xcb.toByte(),0x3f,0xff.toByte(),byteArrayOf(0x5c,2,0x7f,0x21),le=65536,extended=true)
                val protected = pc.wrapCommand(request)
                val objects = SmTlv.parse(CommandApdu.fromBytes(protected).data)
                assertTrue(objects.containsKey(0x85))
                assertFalse(objects.containsKey(0x87))
                assertEquals(16,objects[0x85]!!.size)
                chip.result = byteArrayOf(0x53,2,1,2,0x90.toByte(),0)
                val response = endpoint.transmit(protected)
                assertTrue(SmTlv.parse(response.copyOfRange(0,response.size-2)).containsKey(0x85))
                assertArrayEquals(chip.result,pc.unwrapResponse(response))
                assertArrayEquals(request.toByteArray(),chip.forwarded.single())
                assertArrayEquals(chip.result,pc.transceive(CommandApdu(0,0xb0.toByte(),0,0,le=16).toByteArray()))
            }
        }
    }
    @Test fun corruptedDo85NeverReachesPhysicalCard() {
        Fixture().use { f ->
            val pc = f.authenticate()
            val wrapped = pc.wrapCommand(CommandApdu(0,0xcb.toByte(),0x3f,0xff.toByte(),byteArrayOf(0x5c,0),le=256))
            wrapped[7] = (wrapped[7].toInt() xor 1).toByte()
            assertThrows(IllegalArgumentException::class.java) { f.endpoint.transmit(wrapped) }
            assertTrue(f.forwarded.isEmpty())
        }
    }
}
