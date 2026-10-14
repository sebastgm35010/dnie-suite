package pe.gob.reniec.dnie.pdf

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.cert.X509Certificate

/** Public, untrusted claims. A match is never a signature/trust decision. No URLs or secrets. */
data class StampQrMetadata(val certificateSha256: String, val certificateSerial: String,
    val originalSha256: String, val originalSize: Long, val preview: Boolean = false) {
    companion object {
        fun derived(certificate: X509Certificate, digest: ByteArray, size: Long) = StampQrMetadata(
            StampQrCodec.hex(MessageDigest.getInstance("SHA-256").digest(certificate.encoded)),
            certificate.serialNumber.toString(16).uppercase(), StampQrCodec.hex(digest), size)
        val PREVIEW = StampQrMetadata("00".repeat(32), "0", "00".repeat(32), 1, true)
    }
}

data class StampQrRecord(val metadata: StampQrMetadata, val name: String, val dni: String?,
    val moment: String?, val requestedProfile: String)
data class StampFileDigest(val sha256: String, val size: Long)
enum class StampQrMatch { MATCH, DIFFERENT, PREVIEW }

/** Versioned canonical UTF-8 format. Full SHA-256 hashes use compact base64url (never truncated). */
object StampQrCodec {
    const val PREFIX = "DNIEQR1"
    const val MAX_TEXT = 1800
    const val MAX_FILE_BYTES = 256L * 1024 * 1024
    // java.util.Base64 is API 26; the app also supports Android 24/25.
    private val encoder = UrlBase64
    private val decoder = UrlBase64
    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02X".format(it.toInt() and 255) }
    private fun bytes(hex: String): ByteArray {
        require(hex.matches(Regex("[0-9A-F]{64}"))) { "Huella SHA-256 no válida" }
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    private fun text(value: String): String {
        require(value.toByteArray(Charsets.UTF_8).size in 1..256 && value.none { it.isISOControl() || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' }) { "Texto QR no admitido" }
        return value
    }
    private fun packed(value: String) = encoder.encodeToString(text(value).toByteArray(Charsets.UTF_8))
    private fun unpacked(value: String): String {
        val raw = decoder.decode(value)
        require(encoder.encodeToString(raw) == value) { "Codificación QR no canónica" }
        return text(Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString())
    }
    fun encode(record: StampQrRecord): String {
        val m = record.metadata
        require(m.certificateSerial.matches(Regex("0|[1-9A-F][0-9A-F]{0,63}"))) { "Serie no válida" }
        require(m.originalSize in 1..MAX_FILE_BYTES) { "Tamaño de original no válido" }
        require(record.requestedProfile in listOf("B", "T"))
        return buildList {
            add(PREFIX); add("M=" + if(m.preview) "P" else "F")
            add("C=" + encoder.encodeToString(bytes(m.certificateSha256)))
            add("S=" + m.certificateSerial)
            add("D=" + encoder.encodeToString(bytes(m.originalSha256)))
            add("L=" + m.originalSize); add("P=" + record.requestedProfile); add("N=" + packed(record.name))
            record.dni?.let { add("I=" + packed(it)) }; record.moment?.let { add("F=" + packed(it)) }
        }.joinToString("|").also { require(it.length <= MAX_TEXT) }
    }
    fun decode(payload: String): StampQrRecord {
        require(payload.length <= MAX_TEXT && payload.startsWith("$PREFIX|")) { "Este QR no es un sello DNIe compatible. Los QR antiguos solo contienen un resumen." }
        val fields = linkedMapOf<String,String>()
        for(part in payload.split('|').drop(1)) {
            val pair = part.split('=', limit=2)
            require(pair.size == 2 && pair[0] in setOf("M","C","S","D","L","P","N","I","F") && fields.put(pair[0],pair[1]) == null) { "Campos QR inválidos o repetidos" }
        }
        fun f(key: String) = requireNotNull(fields[key]) { "QR incompleto" }
        fun hash(key: String): String {
            val raw=decoder.decode(f(key));require(raw.size==32 && encoder.encodeToString(raw)==f(key)) { "Huella incompleta" };return hex(raw)
        }
        require(f("M") in listOf("P","F"))
        val result=StampQrRecord(StampQrMetadata(hash("C"),f("S"),hash("D"),f("L").toLong(),f("M")=="P"),
            unpacked(f("N")),fields["I"]?.let(::unpacked),fields["F"]?.let(::unpacked),f("P"))
        require(encode(result)==payload) { "QR no canónico" }
        return result
    }
    /** Streaming and bounded; caller owns and closes input. Cancellation checked per chunk. */
    fun digest(input: InputStream, checkCancelled: () -> Unit = {}): StampFileDigest {
        val md=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536);var length=0L
        while(true) {
            checkCancelled();val n=input.read(buffer);if(n<0) break
            length+=n;require(length<=MAX_FILE_BYTES) { "Archivo mayor de 256 MiB" };md.update(buffer,0,n)
        }
        return StampFileDigest(hex(md.digest()),length)
    }
    fun compareOriginal(record: StampQrRecord, digest: StampFileDigest) = when {
        record.metadata.preview -> StampQrMatch.PREVIEW
        record.metadata.originalSize == digest.size && record.metadata.originalSha256 == digest.sha256 -> StampQrMatch.MATCH
        else -> StampQrMatch.DIFFERENT
    }
    fun compareCertificate(record: StampQrRecord, certificate: X509Certificate) = when {
        record.metadata.preview -> StampQrMatch.PREVIEW
        record.metadata.certificateSha256 == hex(MessageDigest.getInstance("SHA-256").digest(certificate.encoded)) &&
            record.metadata.certificateSerial == certificate.serialNumber.toString(16).uppercase() -> StampQrMatch.MATCH
        else -> StampQrMatch.DIFFERENT
    }
}

/** Encoding only (not cryptography), RFC 4648 URL alphabet, no padding. */
private object UrlBase64 {
    private const val alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    fun encodeToString(bytes: ByteArray): String = buildString {
        var bits=0;var value=0
        for(byte in bytes) {
            value=(value shl 8) or (byte.toInt() and 255);bits+=8
            while(bits>=6) { bits-=6;append(alphabet[(value ushr bits) and 63]) }
        }
        if(bits>0) append(alphabet[(value shl (6-bits)) and 63])
    }
    fun decode(text: String): ByteArray {
        require(text.length%4!=1 && text.length<=1800)
        val out=ByteArray(text.length*6/8);var count=0;var bits=0;var value=0
        for(char in text) {
            val digit=alphabet.indexOf(char);require(digit>=0) { "Base64url no válida" }
            value=(value shl 6) or digit;bits+=6
            if(bits>=8) { bits-=8;out[count++]=(value ushr bits).toByte() }
        }
        return out
    }
}
