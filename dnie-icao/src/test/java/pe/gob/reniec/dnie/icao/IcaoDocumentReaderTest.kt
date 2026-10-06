package pe.gob.reniec.dnie.icao

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.logging.ApduLogger

class IcaoDocumentReaderTest {
    private fun reader(channel: FilesChannel, logger: ApduLogger = ApduLogger()) =
        IcaoDocumentReader(channel, "123456", logger) { c, _ -> c }

    @Test fun absentAndRestrictedBlocksDoNotPreventImagesOrIdentity() = runBlocking {
        val channel = FilesChannel(mapOf(0x011E to inventory(0x61, 0x75, 0x67, 0x6B), 0x0101 to dg1(),
            0x0102 to face(), 0x0107 to tlv(0x67, tlv(0x5F43, jpeg))), statuses = mapOf(0x010B to 0x6982))
        val result = reader(channel).read()
        assertEquals(BlockStatus.AVAILABLE, result.blocks.getValue(IcaoFile.DG1).status)
        assertEquals(BlockStatus.RESTRICTED, result.blocks.getValue(IcaoFile.DG11).status)
        assertEquals(BlockStatus.AVAILABLE, result.blocks.getValue(IcaoFile.DG2).status)
        assertEquals(BlockStatus.AVAILABLE, result.blocks.getValue(IcaoFile.DG7).status)
        assertEquals(BlockStatus.ABSENT, result.blocks.getValue(IcaoFile.DG12).status)
        assertTrue(channel.closed)
    }
    @Test fun parserErrorIsLocalAndMissingInventoryFallsBackToProbes() = runBlocking {
        val channel = FilesChannel(mapOf(0x0101 to dg1("INVALID"), 0x0102 to face()))
        val result = reader(channel).read()
        assertEquals(BlockStatus.ERROR, result.blocks.getValue(IcaoFile.DG1).status)
        assertNotNull(result.blocks.getValue(IcaoFile.DG1).rawBytes)
        assertEquals(BlockStatus.AVAILABLE, result.blocks.getValue(IcaoFile.DG2).status)
    }
    @Test fun disconnectPreservesCompletedBlocksAndStopsAllFurtherApdus() = runBlocking {
        val channel = FilesChannel(mapOf(0x0101 to dg1()), disconnectOn = 0x0102)
        val result = reader(channel).read()
        assertEquals(BlockStatus.AVAILABLE, result.blocks.getValue(IcaoFile.DG1).status)
        assertEquals(BlockStatus.INTERRUPTED, result.blocks.getValue(IcaoFile.DG2).status)
        assertEquals(BlockStatus.INTERRUPTED, result.blocks.getValue(IcaoFile.DG7).status)
        assertEquals(0x0102, channel.selected)
        assertNotNull(result.sessionMessage)
        assertTrue(channel.closed)
    }
    @Test fun plaintextPersonalDataNeverEntersDiagnosticHistory() = runBlocking {
        val logger = ApduLogger()
        reader(FilesChannel(mapOf(0x0101 to dg1())), logger).read()
        val logs = logger.getHistory().joinToString { it.apduHex + it.description }
        assertFalse(logs.contains("ERIKSSON"))
        assertFalse(logs.contains("45 52 49 4B 53 53 4F 4E"))
        assertFalse(logs.contains("123456"))
    }
    @Test fun readsMoreThan64kUsingOddReadBinaryAndActualOffsets() = runBlocking {
        val raw = tlv(0x6D, ByteArray(70_000) { (it % 251).toByte() })
        val channel = FilesChannel(mapOf(0x010D to raw), maxChunk = 117)
        val fileReader = IcaoFileReader { cmd, _ -> ResponseApdu.fromBytes(channel.transceive(cmd.toByteArray())) }
        assertArrayEquals(raw, fileReader.read(byteArrayOf(1, 13), 0x6D, "DG13"))
        val odd = channel.commands.filter { it.ins == 0xB1.toByte() }
        assertTrue(odd.isNotEmpty())
        assertTrue(odd.any { it.data[1] == 3.toByte() })
        assertTrue(channel.commands.none { it.ins == 0xB0.toByte() && (it.p1.toInt() and 128) != 0 })
    }
    @Test fun correctedLeIsRetriedOnce() = runBlocking {
        val raw = dg1()
        val channel = FilesChannel(mapOf(0x0101 to raw), maxChunk = 64)
        var corrected = false
        val reader = IcaoFileReader { cmd, _ ->
            if (cmd.ins == 0xB0.toByte() && !corrected) {
                corrected = true
                ResponseApdu.error(0x6C40)
            } else ResponseApdu.fromBytes(channel.transceive(cmd.toByteArray()))
        }
        assertArrayEquals(raw, reader.read(byteArrayOf(1, 1), 0x61, "DG1"))
        assertEquals(64, channel.commands.first { it.ins == 0xB0.toByte() }.le)
    }
    @Test fun unexpectedTagFailsLocallyInsteadOfMislabelingIdentity() = runBlocking {
        val channel = FilesChannel(mapOf(0x0101 to face(), 0x0102 to face()))
        val result = reader(channel).read()
        assertEquals(BlockStatus.ERROR, result.blocks.getValue(IcaoFile.DG1).status)
        assertEquals(BlockStatus.AVAILABLE, result.blocks.getValue(IcaoFile.DG2).status)
    }
    @Test fun emitsEachBlockIndependently() = runBlocking {
        val snapshots = mutableListOf<IcaoDocument>()
        reader(FilesChannel(mapOf(0x0101 to dg1()))).read { snapshots += it }
        assertTrue(snapshots.any { !it.completed && it.blocks[IcaoFile.DG1]?.status == BlockStatus.AVAILABLE })
        assertTrue(snapshots.last().completed)
    }
    @Test fun freshSessionUsesIcaoAndCardAccessAndNeverSelectsIasPki() = runBlocking {
        val channel = FilesChannel(mapOf(0x011C to tlv(0x31, byteArrayOf())))
        val result = IcaoDocumentReader(channel, "123456", ApduLogger()).read()
        assertNotNull(result.sessionMessage)
        assertArrayEquals(byteArrayOf(-96, 0, 0, 2, 71, 16, 1), channel.commands.first().data)
        assertTrue(channel.commands.none { it.ins == 0x22.toByte() || it.ins == 0x20.toByte() })
        assertTrue(channel.commands.any { it.ins == 0xB1.toByte() && it.data.contentEquals(byteArrayOf(0x54, 2, 0, 0)) })
        assertTrue(channel.closed)
    }
    @Test(expected = IllegalArgumentException::class) fun eofBeforeDeclaredLengthIsRejected(): Unit = runBlocking {
        val reader = IcaoFileReader { cmd, _ ->
            if (cmd.ins == 0xA4.toByte()) ResponseApdu.success()
            else ResponseApdu.error(0x6282, byteArrayOf(0x61, 100, 0))
        }
        reader.read(byteArrayOf(1, 1), 0x61, "DG1")
        Unit
    }
}
