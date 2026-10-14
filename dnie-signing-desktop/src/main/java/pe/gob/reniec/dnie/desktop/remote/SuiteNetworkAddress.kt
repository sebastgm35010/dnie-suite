package pe.gob.reniec.dnie.desktop.remote

import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import pe.gob.reniec.dnie.signing.link.SigningProvision

/** Endpoint choice only; never substitutes TLS identity or retries signed requests. */
object SuiteNetworkAddress {
    fun choose(preferred: String, phone: String): String {
        val assigned = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }
            .mapNotNull { it.hostAddress }.filter(SigningProvision::privateAddress)
        val routed = if (SigningProvision.privateAddress(phone)) runCatching {
            DatagramSocket().use { it.connect(InetAddress.getByName(phone), SigningProvision.PHONE_PORT); it.localAddress.hostAddress }
        }.getOrNull() else null
        return select(preferred, assigned, routed)
    }
    fun select(preferred: String, assigned: List<String>, routed: String?): String =
        routed?.takeIf { it in assigned } ?: preferred.takeIf { it in assigned }
        ?: assigned.firstOrNull().orEmpty()
}
