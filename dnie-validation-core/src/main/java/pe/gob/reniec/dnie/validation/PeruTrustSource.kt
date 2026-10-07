package pe.gob.reniec.dnie.validation

import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.util.Date

fun interface TrustResourceSource { fun download(url: String): ByteArray }
class IndecopiTrustSource : TrustResourceSource {
    override fun download(url: String): ByteArray {
        require(url in setOf(TSL_URL, SIGNER_CRL_URL))
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5000; connection.readTimeout = 5000
        connection.instanceFollowRedirects = false
        try {
            require(connection.responseCode == 200 && connection.contentLengthLong <= MAX_BYTES)
            val deadline = System.nanoTime() + 12_000_000_000L
            return connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    if (Thread.currentThread().isInterrupted || System.nanoTime() > deadline) throw java.io.InterruptedIOException()
                    val count = input.read(buffer); if (count < 0) break
                    require(out.size() + count <= MAX_BYTES); out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
        } finally { connection.disconnect() }
    }
    companion object {
        const val TSL_URL = "https://iofe.indecopi.gob.pe/TSL/tsl-pe.xml"
        const val SIGNER_CRL_URL = "https://recursos.indecopi.gob.pe/iofe/crl/indecopi_tsl.crl"
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}

/** Rollback record contains public list metadata only; Android persists this independently of UI. */
data class TrustedListRecord(val sequence: Int, val sha256: String)
interface TrustedListRecordStore {
    fun read(): TrustedListRecord?
    fun save(record: TrustedListRecord)
}
class MemoryTrustedListRecordStore : TrustedListRecordStore {
    private var value: TrustedListRecord? = null
    override fun read() = value
    override fun save(record: TrustedListRecord) { value = record }
}
internal object TrustedListRollbackPolicy {
    fun check(previous: TrustedListRecord?, current: TrustedListRecord) {
        require(current.sequence >= maxOf(25, previous?.sequence ?: 25))
        if (previous?.sequence == current.sequence) require(previous.sha256 == current.sha256)
    }
}

/** One provider per process. Cache expires with both list and signer CRL, at most one hour. */
fun interface TrustedListSource { fun current(now: Date): VerifiedTrustedList }
class PeruTrustedListProvider(private val source: TrustResourceSource = IndecopiTrustSource(),
    private val records: TrustedListRecordStore = MemoryTrustedListRecordStore()) : TrustedListSource {
    private var cached: VerifiedTrustedList? = null
    private var validUntil = 0L
    @Synchronized override fun current(now: Date): VerifiedTrustedList {
        cached?.let { if (now.time < validUntil && !now.before(it.issued)) return it }
        val root = trustStep(TrustStage.AAC_RESOURCE) {
            val factory = CertificateFactory.getInstance("X.509")
            val rootBytes = requireNotNull(javaClass.getResourceAsStream("/trust/indecopi-aac-root.der")).use { it.readBytes() }
            val rootHash = MessageDigest.getInstance("SHA-256").digest(rootBytes).hex()
            require(rootHash == "6cf85e75ca1cf9d560fdc5ebaff30cea157cde223a068c3e1e051d123d5f9375")
            val root = factory.generateCertificate(rootBytes.inputStream()) as java.security.cert.X509Certificate
            require(CertificateChecks.inspect(root, now, false).acceptable)
            root
        }
        val factory = CertificateFactory.getInstance("X.509")
        val previous = trustStep(TrustStage.TSL_STATE) { records.read() }
        val xml = trustStep(TrustStage.TSL_DOWNLOAD) { source.download(IndecopiTrustSource.TSL_URL) }
        val list = TrustedListVerifier(setOf(TrustAnchor(root, null))).verify(
            xml, now, maxOf(25, previous?.sequence ?: 25))
        // Authorize the current TSL role, not every certificate issued by the AAC root.
        // Bootstrap: independently published root -> verified signer certificate -> explicit signer pin.
        // Rotation requires a reviewed application trust-policy update; no learning keys from new XML.
        trustStep(TrustStage.TSL_SIGNER_PIN) {
            require(MessageDigest.getInstance("SHA-256").digest(list.signer.encoded).hex() ==
                "bc32323c7ba0af62bdef9ca7e7f499bb71eeb4fa092b9017e15acc4d0bf612ed")
        }
        val record = TrustedListRecord(list.sequence, list.digest.hex())
        trustStep(TrustStage.TSL_ROLLBACK) { TrustedListRollbackPolicy.check(previous, record) }
        trustStep(TrustStage.SIGNER_CRL_DOWNLOAD) {
            require(IndecopiTrustSource.SIGNER_CRL_URL in CertificateLocations.completeCrlUrls(list.signer))
        }
        val crlBytes = trustStep(TrustStage.SIGNER_CRL_DOWNLOAD) { source.download(IndecopiTrustSource.SIGNER_CRL_URL) }
        val state = trustStep(TrustStage.SIGNER_CRL_VALIDATION) {
            val crl = factory.generateCRL(crlBytes.inputStream()) as java.security.cert.X509CRL
            DirectCrlValidator.evaluate(list.signer, root, crl, now).also {
                require(it.status == RevocationStatus.GOOD) { "Firmante de TSL sin estado verificable" }
            }
        }
        trustStep(TrustStage.TSL_STATE) { records.save(record) }
        validUntil = minOf(list.expires.time, requireNotNull(state.nextUpdate).time, now.time + 3600000L, list.signer.notAfter.time)
        val authenticated = list.copy(authorityEvidenceUntil = Date(validUntil))
        cached = authenticated
        return authenticated
    }
}
internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
