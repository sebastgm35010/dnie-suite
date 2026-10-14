package pe.gob.reniec.dnie.bridge
/** Physical connection choice; LAN and USB networking always use certificate-pinned TLS. */
enum class BridgeConnectionMethod(val title: String, val usesPairing: Boolean) {
    TAILSCALE("Tailscale", false), LOCAL_NETWORK("Red · Wi-Fi / Tailscale", true), USB_NETWORK("Cable USB", true);
    fun accepts(transport: String): Boolean = when (this) {
        TAILSCALE -> transport == "tailscale_sync" || transport == "tailscale_tcp"
        LOCAL_NETWORK -> transport == "tls" || transport == "tls_reverse"
        USB_NETWORK -> transport == "usb_accessory"
    }
}
