package pe.gob.reniec.dnie.signing.link

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class ReverseTlsPeerTest {
    @Test fun routeAllowsChangedPrivateAndTailnetAddressesButDoesNotAdmitPublicPeers() {
        listOf("192.168.1.5", "192.168.20.8", "10.1.2.3", "172.31.1.2", "100.104.106.125").forEach {
            assertEquals(it, ReverseTlsPeer.requirePrivate(InetAddress.getByName(it)))
        }
        listOf("8.8.8.8", "127.0.0.1", "100.128.0.1", "172.32.0.1", "::1").forEach {
            assertThrows(IllegalArgumentException::class.java) { ReverseTlsPeer.requirePrivate(InetAddress.getByName(it)) }
        }
    }
}
