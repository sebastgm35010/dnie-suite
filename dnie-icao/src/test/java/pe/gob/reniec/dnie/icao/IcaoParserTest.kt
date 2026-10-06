package pe.gob.reniec.dnie.icao

import org.junit.Assert.*
import org.junit.Test

class IcaoParserTest {
    @Test fun readsTd1AndChecksAllDigits() {
        val id = IcaoParser.parse(IcaoFile.DG1, dg1()) as IcaoContent.Identity
        assertEquals("D23145890", id.documentNumber)
        assertEquals("ERIKSSON", id.surnames)
        assertEquals("ANNA MARIA", id.givenNames)
        assertEquals("740812", id.birthDateMrz)
        assertTrue(id.checkDigits.toString(), id.checkDigits.values.all { it })
    }
    @Test fun readsTd3PassportWithoutGuessingCentury() {
        val mrz = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\nL898902C36UTO7408122F1204159ZE184226B<<<<<10"
        val id = IcaoParser.parse(IcaoFile.DG1, dg1(mrz)) as IcaoContent.Identity
        assertEquals("L898902C3", id.documentNumber)
        assertTrue(id.checkDigits.values.all { it })
    }
    @Test fun invalidCheckDigitPreservesIdentityWithWarning() {
        val id = IcaoParser.parse(IcaoFile.DG1, dg1(td1.replace("7408122", "7408123"))) as IcaoContent.Identity
        assertFalse(id.checkDigits.getValue("Fecha de nacimiento"))
        assertEquals("ANNA MARIA", id.givenNames)
    }
    @Test fun extractsFaceWithFeaturePoints() {
        val parsed = IcaoParser.parse(IcaoFile.DG2, face(features = 2)) as IcaoContent.Images
        assertArrayEquals(jpeg, parsed.images.single().bytes)
        assertEquals(120, parsed.images.single().width)
        assertEquals(160, parsed.images.single().height)
    }
    @Test fun extractsJpeg2000Face() {
        val parsed = IcaoParser.parse(IcaoFile.DG2, face(jp2)) as IcaoContent.Images
        assertEquals("image/jp2", parsed.images.single().mimeType)
        assertArrayEquals(jp2, parsed.images.single().bytes)
    }
    @Test fun extractsHandwrittenSignature() {
        val data = tlv(0x67, tlv(0x02, byteArrayOf(1)) + tlv(0x5F43, jpeg))
        val parsed = IcaoParser.parse(IcaoFile.DG7, data) as IcaoContent.Images
        assertArrayEquals(jpeg, parsed.images.single().bytes)
    }
    @Test fun parsesOptionalDetailsAndPackedDates() {
        val data = tlv(0x6B, tlv(0x5F0E, "PEREZ<<ANA".toByteArray()) + tlv(0x5F2B, byteArrayOf(0x19, 0x90.toByte(), 0x12, 0x31)))
        val parsed = IcaoParser.parse(IcaoFile.DG11, data) as IcaoContent.Details
        assertEquals("PEREZ  ANA", parsed.fields["Nombre completo"])
        assertEquals("19901231", parsed.fields["Fecha de nacimiento completa"])
    }
    @Test fun preservesRepeatedNamesInOptionalTemplate() {
        val parsed = IcaoParser.parse(IcaoFile.DG12, tlv(0x6C, tlv(0xA0,
            tlv(0x02, byteArrayOf(2)) + tlv(0x5F1A, "ANA".toByteArray()) + tlv(0x5F1A, "LUIS".toByteArray())))) as IcaoContent.Details
        assertEquals("ANA · LUIS", parsed.fields["Otras personas en el documento"])
    }
    @Test fun inventoryIncludesUnknownGroupsWithoutDroppingThem() {
        val parsed = IcaoParser.parse(IcaoFile.COM, inventory(0x61, 0x75, 0x67, 0x6E)) as IcaoContent.Inventory
        assertEquals(setOf(0x61, 0x75, 0x67, 0x6E), parsed.tags)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTruncatedTlv() {
        BerTlv.nodes(byteArrayOf(0x61, 4, 1))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsIndefiniteLength() {
        BerTlv.header(byteArrayOf(0x61, 0x80.toByte(), 0, 0))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedFile() {
        BerTlv.header(byteArrayOf(0x75, 0x83.toByte(), 0x7F, -1, -1))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTruncatedFaceRecord() {
        val data = face().dropLast(2).toByteArray()
        IcaoParser.parse(IcaoFile.DG2, data)
    }
}
