package pe.gob.reniec.dnie.nfc

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.apdu.MockIsoDepCardChannel
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.core.nfc.V3PkiProbe

class V3PkiProbeTest {
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun response(sw: String = "9000", bytes: ByteArray = byteArrayOf()) =
        ResponseApdu.fromBytes(bytes + hex(sw))

    @Test fun failedIasStopsBeforeAdfOrReadOrPin() {
        val sent = mutableListOf<CommandApdu>()
        val found = V3PkiProbe({ cmd, _ -> sent.add(cmd); response("6A82") }, ApduLogger()).explore("TEST")
        assertTrue(found.isEmpty())
        assertEquals(1, sent.size)
        assertEquals("00A4040C10A000000077030C60000000FE00000500", sent.single().toHexString(""))
    }

    @Test fun failedAdfStopsBeforeReadingFiles() {
        val sent = mutableListOf<CommandApdu>()
        V3PkiProbe({ cmd, _ -> sent.add(cmd); response(if (sent.size == 1) "9000" else "6A82") },
            ApduLogger()).explore("TEST")
        assertEquals(2, sent.size)
        assertEquals("00A4040C0EE828BD080FD25047656E65726963", sent[1].toHexString(""))
    }

    @Test fun readsFullCertificatesInOfficialOrderAndAdvancesByActualPayload() {
        val cert = MockIsoDepCardChannel().signatureCertDer
        val sent = mutableListOf<CommandApdu>()
        val logger = ApduLogger()
        val found = V3PkiProbe({ cmd, _ ->
            sent.add(cmd)
            if (cmd.ins == 0xB0.toByte()) {
                val offset = ((cmd.p1.toInt() and 255) shl 8) or (cmd.p2.toInt() and 255)
                // Deliberately return fewer bytes than Le without indicating EOF.
                val end = minOf(offset + 71, cert.size)
                response(if (end == cert.size) "6282" else "9000", cert.copyOfRange(offset, end))
            } else response()
        }, logger).explore("TEST")
        assertEquals(listOf("CE82", "CE81", "CE83"), found.keys.toList())
        found.values.forEach { assertArrayEquals(cert, it) }
        val efs = sent.filter { it.ins == 0xA4.toByte() && it.p1 == 2.toByte() }
        assertEquals(listOf("00A4020C02CE82", "00A4020C02CE81", "00A4020C02CE83"), efs.map { it.toHexString("") })
        assertTrue(sent.all { it.ins in listOf(0xA4.toByte(), 0xB0.toByte()) })
        assertTrue(sent.filter { it.ins == 0xB0.toByte() }.all { it.le!! <= 223 })
        assertTrue(logger.getHistory().any { it.description.contains("X.509 completo") })
    }

    @Test fun truncatedEofNeverReportsCertificateSuccess() {
        val cert = MockIsoDepCardChannel().signatureCertDer
        val logger = ApduLogger()
        val found = V3PkiProbe({ cmd, _ ->
            if (cmd.ins == 0xB0.toByte()) response("6282", cert.copyOf(50)) else response()
        }, logger).explore("TEST")
        assertTrue(found.isEmpty())
        assertFalse(logger.getHistory().any { it.description.contains("CE82 X.509 completo") })
    }

    @Test fun handlesFcpContinuationAndCorrectedReadLength() {
        val cert = MockIsoDepCardChannel().signatureCertDer
        val sent = mutableListOf<CommandApdu>()
        var needsCorrection = true
        val found = V3PkiProbe({ cmd, _ ->
            sent.add(cmd)
            when (cmd.ins.toInt() and 255) {
                0xA4 -> if (cmd.p1 == 2.toByte()) response("6110") else response()
                0xC0 -> response(bytes = hex("6200"))
                else -> {
                    val offset = ((cmd.p1.toInt() and 255) shl 8) or (cmd.p2.toInt() and 255)
                    if (needsCorrection) { needsCorrection = false; response("6C40") }
                    else response(bytes = cert.copyOfRange(offset, minOf(offset + cmd.le!!, cert.size)))
                }
            }
        }, ApduLogger()).explore("TEST")
        assertEquals(3, found.size)
        assertTrue(sent.any { it.ins == 0xC0.toByte() })
        assertTrue(sent.any { it.ins == 0xB0.toByte() && it.le == 64 })
    }
}
