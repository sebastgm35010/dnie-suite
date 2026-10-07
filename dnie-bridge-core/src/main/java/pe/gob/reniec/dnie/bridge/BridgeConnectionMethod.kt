package pe.gob.reniec.dnie.bridge
/** Physical connection choice; LAN and USB networking always use certificate-pinned TLS. */
enum class BridgeConnectionMethod(val title: String, val usesPairing: Boolean) {
    TAILSCALE("Tailscale", false), LOCAL_NETWORK("Wi-Fi local", true), USB_NETWORK("Cable USB", true);
    fun accepts(transport: String): Boolean = when (this) {
        TAILSCALE -> transport == "tailscale_sync" || transport == "tailscale_tcp"
        LOCAL_NETWORK, USB_NETWORK -> transport == "tls"
    }
}
