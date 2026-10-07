package pe.gob.reniec.dnie.bridge
/** Bounded transport settings. No secret, UI or platform reference. */
data class BridgeRuntimeSettings(
    val connectTimeoutMs: Int = 15_000,
    val readTimeoutMs: Int = 120_000,
    val nfcTimeoutMs: Int = 10_000
) {
    init {
        require(connectTimeoutMs in 5_000..30_000)
        require(readTimeoutMs in 30_000..300_000)
        require(nfcTimeoutMs in 5_000..30_000)
    }
}
