package pe.gob.reniec.dnie.nfc

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.nfc.*

class V3PkiSessionTest {
    private fun ok(sw: Int = 0x9000) = ResponseApdu.fromBytes(byteArrayOf((sw shr 8).toByte(), sw.toByte()))

    @Test fun selectsIasThenPkiThenAuthenticatesThenSelectsPkiSecurely() {
        val sent = mutableListOf<String>()
        var secure = false
        val result = V3PkiSession.open("123456", { cmd, _ ->
            sent.add((if (secure) "SM:" else "PLAIN:") + cmd.toHexString("")); ok()
        }) { profile ->
            assertEquals(2, sent.size)
            assertEquals("0.4.0.127.0.7.2.2.4.4.4", profile.oid)
            assertEquals(13, profile.parameterId)
            secure = true
        }
        assertTrue(result)
        assertEquals(listOf("PLAIN:00A4040C10A000000077030C60000000FE00000500",
            "PLAIN:00A4040C0EE828BD080FD25047656E65726963",
            "SM:00A4040C0EE828BD080FD25047656E65726963"), sent)
    }

    @Test fun absentIasDoesNotTryPace() {
        assertFalse(V3PkiSession.open("123456", { _, _ -> ok(0x6A82) }) { error("PACE must not run") })
    }

    @Test fun failedPkiOrAuthenticationStopsWithoutProbingIcaoOrPin() {
        var count = 0
        assertThrows(CardProtocolException::class.java) {
            V3PkiSession.open("123456", { _, _ -> if (++count == 1) ok() else ok(0x6985) }) {
                error("PACE must not run")
            }
        }
        assertEquals(2, count)
        count = 0
        assertThrows(PaceException::class.java) {
            V3PkiSession.open("123456", { _, _ -> count++; ok() }) { throw PaceException("bad token") }
        }
        assertEquals(2, count)
    }

    @Test fun v3PinAndSigningCommandsMatchReniecProfile() {
        assertEquals("002000030C313233343536FFFFFFFFFFFF", V3PkiSession.verifyPin("123456").toHexString(""))
        assertEquals("002241B606840182800142", V3PkiSession.signatureEnvironment().toHexString(""))
        val hash = ByteArray(32) { it.toByte() }
        val pso = V3PkiSession.sign(hash)
        assertArrayEquals(hash, pso.data) // no legacy DigestInfo prefix
        assertEquals("002A9E9A20", pso.toHexString("").take(10))
        assertThrows(IllegalArgumentException::class.java) { V3PkiSession.verifyPin("１２３４") }
        assertThrows(IllegalArgumentException::class.java) { V3PkiSession.sign(ByteArray(31)) }
    }
}
