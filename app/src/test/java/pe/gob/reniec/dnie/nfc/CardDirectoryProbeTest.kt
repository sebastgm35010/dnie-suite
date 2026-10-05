package pe.gob.reniec.dnie.nfc

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.core.nfc.CardDirectoryProbe

class CardDirectoryProbeTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val directory = hex("61184F07A000000247100150074D592041505053510450151001")
    private fun response(sw: String = "9000", data: ByteArray = byteArrayOf()) = ResponseApdu.fromBytes(data + hex(sw))

    @Test fun extractsAidLabelAndPath() {
        val app = CardDirectoryProbe.parseApplications(directory).single()
        assertArrayEquals(hex("A0000002471001"), app.aid)
        assertEquals("MY APPS", app.label)
        assertArrayEquals(hex("50151001"), app.path)
    }

    @Test fun nestedTemplatesDeduplicateAidsAndIgnorePadding() {
        val nested = byteArrayOf(0x6F, directory.size.toByte()) + directory
        assertEquals(1, CardDirectoryProbe.parseApplications(nested + directory + hex("FFFF0000")).size)
    }

    @Test fun rejectsTruncatedDirectoryRatherThanInventingAid() {
        assertThrows(IllegalArgumentException::class.java) { CardDirectoryProbe.parseApplications(directory.dropLast(1).toByteArray()) }
    }

    @Test fun rejectsDuplicateAidFieldAndIndefiniteBerLength() {
        assertThrows(IllegalArgumentException::class.java) {
            CardDirectoryProbe.parseApplications(hex("61124F07A00000024710014F07A0000002471001"))
        }
        assertThrows(IllegalArgumentException::class.java) { CardDirectoryProbe.parseApplications(hex("61804F07A00000024710010000")) }
    }

    @Test fun readsTransparentDirectoryAndResetsMfBeforeEachDf() {
        val commands = mutableListOf<CommandApdu>()
        val logger = ApduLogger()
        val probe = CardDirectoryProbe({ cmd, _ ->
            commands.add(cmd)
            when {
                cmd.ins == 0xB0.toByte() -> response(data = directory)
                cmd.data.contentEquals(hex("3F00")) || cmd.data.contentEquals(hex("2F00")) -> response()
                else -> response("6A82")
            }
        }, logger)
        assertEquals(1, probe.explore().size)
        assertTrue(commands.any { it.p1 == 4.toByte() && it.data.contentEquals(hex("A0000002471001")) })
        for (i in commands.indices.filter { commands[it].p1 == 1.toByte() }) {
            assertArrayEquals(hex("3F00"), commands[i - 1].data)
        }
        assertTrue(commands.all { it.ins in listOf(0xA4.toByte(), 0xB0.toByte(), 0xB2.toByte()) })
        assertTrue(logger.getHistory().any { it.description.contains("AID=A0000002471001") })
    }

    @Test fun readsRecordStructuredDirectoryAndRetriesExactLength() {
        val commands = mutableListOf<CommandApdu>()
        var recordReads = 0
        val probe = CardDirectoryProbe({ cmd, _ ->
            commands.add(cmd)
            when {
                cmd.ins == 0xB0.toByte() -> response("6981")
                cmd.ins == 0xB2.toByte() && cmd.p1 == 1.toByte() -> {
                    recordReads++
                    if (recordReads == 1) response("6C1A") else response(data = directory)
                }
                cmd.ins == 0xB2.toByte() -> response("6A83")
                cmd.data.contentEquals(hex("3F00")) || cmd.data.contentEquals(hex("2F00")) -> response()
                else -> response("6A82")
            }
        }, ApduLogger())
        assertEquals(1, probe.explore().size)
        assertTrue(commands.any { it.ins == 0xB2.toByte() && it.le == 26 })
        assertTrue(commands.any { it.ins == 0xB2.toByte() && it.p1 == 2.toByte() })
    }

    @Test fun unavailableDirectoryStillExploresDfs() {
        val commands = mutableListOf<CommandApdu>()
        val probe = CardDirectoryProbe({ cmd, _ ->
            commands.add(cmd)
            if (cmd.data.contentEquals(hex("3F00"))) response() else response("6A82")
        }, ApduLogger())
        assertTrue(probe.explore().isEmpty())
        assertTrue(commands.any { it.data.contentEquals(hex("5011")) })
        assertTrue(commands.any { it.data.contentEquals(hex("7001")) })
        assertTrue(commands.any { it.data.contentEquals(hex("5015")) })
    }

    @Test fun partialReadFailureNeverSelectsAnIncompleteAid() {
        val commands = mutableListOf<CommandApdu>()
        val firstBlock = directory + ByteArray(223 - directory.size)
        val probe = CardDirectoryProbe({ cmd, _ ->
            commands.add(cmd)
            when {
                cmd.ins == 0xB0.toByte() && cmd.p2 == 0.toByte() -> response(data = firstBlock)
                cmd.ins == 0xB0.toByte() -> response("6982")
                cmd.data.contentEquals(hex("3F00")) || cmd.data.contentEquals(hex("2F00")) -> response()
                else -> response("6A82")
            }
        }, ApduLogger())
        assertTrue(probe.explore().isEmpty())
        assertFalse(commands.any { it.p1 == 4.toByte() })
        assertTrue(commands.any { it.ins == 0xB0.toByte() && (it.p2.toInt() and 255) == 223 })
    }
}
