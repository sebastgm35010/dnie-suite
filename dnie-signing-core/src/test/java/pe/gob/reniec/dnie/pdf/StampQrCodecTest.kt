package pe.gob.reniec.dnie.pdf

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class StampQrCodecTest {
    private val metadata=StampQrMetadata("AB".repeat(32),"1ABC", "CD".repeat(32),1234)
    private fun record()=StampQrRecord(metadata,"ANA MARÍA | QUISPE: Ñ","12345678","2026-10-14","T")
    @Test fun fullHashesUnicodeAndDelimiterNamesRoundTripCanonically() {
        val source=record();val encoded=StampQrCodec.encode(source)
        assertEquals(source,StampQrCodec.decode(encoded))
        assertTrue(encoded.contains("N="+Base64.getUrlEncoder().withoutPadding().encodeToString(source.name.toByteArray())))
        assertEquals(64,StampQrCodec.decode(encoded).metadata.certificateSha256.length)
        assertEquals(64,StampQrCodec.decode(encoded).metadata.originalSha256.length)
    }
    @Test fun malformedUnknownDuplicateTruncatedAndNonCanonicalFieldsAreRejected() {
        val payload=StampQrCodec.encode(record())
        val invalid=listOf(payload+"|M=F",payload+"|URL=https://example.com",payload.replace("L=1234","L=01234"),
            payload.replace("L=1234","L=9223372036854775808"),payload.replace("L=1234","L=-1"),
            payload.replace("P=T","P=LTA"),payload.replace("S=1ABC","S=01ABC"),payload.replace("M=F","M=X"),
            payload.replace(Regex("C=[^|]+"),"C=AAAA"),payload.replace(Regex("N=[^|]+"),"N=_w"),
            payload.replace("|D=","|Z="),payload.dropLast(1),"https://example.com", "DNI:123|NOMBRE:ANA|PAdES", "x".repeat(1801))
        invalid.forEach { bad -> assertThrows(bad,Exception::class.java) { StampQrCodec.decode(bad) } }
    }
    @Test fun previewNeverMatchesAndFileComparisonIncludesLength() {
        val digest=StampFileDigest(metadata.originalSha256,metadata.originalSize)
        assertEquals(StampQrMatch.MATCH,StampQrCodec.compareOriginal(record(),digest))
        assertEquals(StampQrMatch.DIFFERENT,StampQrCodec.compareOriginal(record(),digest.copy(size=1235)))
        assertEquals(StampQrMatch.DIFFERENT,StampQrCodec.compareOriginal(record(),digest.copy(sha256="00".repeat(32))))
        assertEquals(StampQrMatch.PREVIEW,StampQrCodec.compareOriginal(record().copy(metadata=metadata.copy(preview=true)),digest))
    }
    @Test fun streamHashIsExactAcrossChunksAndCancellationStopsReads() {
        val bytes=ByteArray(65536*3+11) { (it%251).toByte() }
        val digest=StampQrCodec.digest(bytes.inputStream())
        assertEquals(bytes.size.toLong(),digest.size)
        assertEquals(StampQrCodec.hex(MessageDigest.getInstance("SHA-256").digest(bytes)),digest.sha256)
        var chunks=0
        assertThrows(InterruptedException::class.java) { StampQrCodec.digest(bytes.inputStream()) { if(++chunks==2) throw InterruptedException() } }
        assertEquals(2,chunks)
    }
    @Test fun refusesControlCharactersOversizeTextAndHashesOfWrongLength() {
        for(name in listOf("ANA\nADMIN", "ANA\u202EADMIN", "Ñ".repeat(129),""))
            assertThrows(IllegalArgumentException::class.java) { StampQrCodec.encode(record().copy(name=name)) }
        assertThrows(IllegalArgumentException::class.java) { StampQrCodec.encode(record().copy(metadata=metadata.copy(originalSha256="AB"))) }
    }
    @Test fun smallStampCannotSilentlyOmitQrOrProduceUnscannableModules() {
        val options=PdfSigningOptions(appearance=SignatureAppearance(badge=StampBadgeType.QR_VERIFICATION))
        val measure: (String,Boolean,Float)->Float={ text,_,size->text.length*size*.5f }
        for(width in listOf(90f,115f)) assertThrows(Exception::class.java) {
            StampLayoutEngine.layout(width,60f,"ANA","123",options,0,measure)
        }
    }
}
