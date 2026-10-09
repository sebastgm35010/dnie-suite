package pe.gob.reniec.dnie.signing.link

import java.net.InetAddress

/** Routing scope only. A permitted address does NOT authenticate a PC: pinned TLS does. */
object ReverseTlsPeer {
    fun requirePrivate(address: InetAddress): String {
        val host = requireNotNull(address.hostAddress)
        require(SigningProvision.privateAddress(host)) { "Expected a private IPv4 peer" }
        return host
    }
}
