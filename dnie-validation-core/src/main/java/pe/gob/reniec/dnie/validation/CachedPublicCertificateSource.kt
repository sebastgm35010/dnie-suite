package pe.gob.reniec.dnie.validation

/** Cache public payloads, not validation decisions. Every use rechecks signature, scope and dates. */
class CachedPublicCertificateSource(private val source: ReniecPublicSource = ReniecPublicSource()) : PublicCertificateSource, OcspResponseSource {
    private data class Entry(val bytes: ByteArray, val expires: Long)
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var size = 0
    @Synchronized override fun download(url: String): ByteArray {
        require(ReniecPublicSource.allowed(url))
        val now = System.nanoTime()
        entries[url]?.let { if (now < it.expires) return it.bytes.clone() }
        entries.remove(url)?.let { size -= it.bytes.size }
        val bytes = source.download(url)
        if (bytes.size <= 2 * 1024 * 1024) {
            while (entries.isNotEmpty() && (size + bytes.size > 4 * 1024 * 1024 || entries.size >= 16)) {
                val first = entries.keys.first(); size -= entries.remove(first)!!.bytes.size
            }
            entries[url] = Entry(bytes.clone(), now + 300_000_000_000L); size += bytes.size
        }
        return bytes
    }
    override fun query(url: String, request: ByteArray): ByteArray = source.query(url, request)
}
