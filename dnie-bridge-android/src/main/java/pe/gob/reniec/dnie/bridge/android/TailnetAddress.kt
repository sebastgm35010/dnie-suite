package pe.gob.reniec.dnie.bridge.android

import java.net.InetAddress

/** TCP test profile stays inside IPv4 tailnet addresses; no DNS fallback to public/LAN. */
internal object TailnetAddress {
    fun isTailnet(address: InetAddress): Boolean {
        val bytes = address.address
        return bytes.size == 4 && (bytes[0].toInt() and 255) == 100 &&
            (bytes[1].toInt() and 255) in 64..127
    }
    fun validHost(host: String): Boolean =
        Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.[a-z0-9-]+\\.ts\\.net").matches(host) ||
            (Regex("[0-9.]{7,15}").matches(host) && runCatching { isTailnet(InetAddress.getByName(host)) }.getOrDefault(false))
}
