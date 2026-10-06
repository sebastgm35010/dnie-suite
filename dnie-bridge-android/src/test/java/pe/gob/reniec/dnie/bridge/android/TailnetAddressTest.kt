package pe.gob.reniec.dnie.bridge.android

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class TailnetAddressTest {
    @Test fun onlyTailnetAddressesAndMagicDnsNamesAreAccepted() {
        assertTrue(TailnetAddress.validHost("sebas-laptop.tail2752e9.ts.net"))
        assertTrue(TailnetAddress.validHost("100.104.106.125"))
        for (host in listOf("127.0.0.1", "192.168.1.5", "8.8.8.8", "100.63.255.255", "100.128.0.1", "https://sebas-laptop.tail2752e9.ts.net", "evil.ts.net.attacker.com")) {
            assertFalse(host, TailnetAddress.validHost(host))
        }
        assertFalse(TailnetAddress.isTailnet(InetAddress.getByName("::1")))
    }
    @Test fun importExplicitlySelectsDirectTransportWithoutChangingLegacyDefault() {
        val template = """{"version":1,"host":"sebas-laptop.tail2752e9.ts.net","port":39443,"certificateSha256":"${"AB".repeat(32)}","pairingTokenBase64":"${"A".repeat(43)}=","expiresAt":"2030-01-01T00:00:00Z","transport":"tailscale_tcp"}"""
        BridgeProvision.parse(template,0).use { assertEquals("tailscale_tcp",it.transport) }
        assertThrows(IllegalArgumentException::class.java) { BridgeProvision.parse(template.replace("sebas-laptop.tail2752e9.ts.net","192.168.1.5"),0) }
        assertThrows(IllegalArgumentException::class.java) { BridgeProvision.parse(template.replace("tailscale_tcp","unknown"),0) }
        BridgeProvision.parse(template.replace("sebas-laptop.tail2752e9.ts.net","127.0.0.1").replace(",\"transport\":\"tailscale_tcp\"",""),0).use { assertEquals("tls",it.transport) }
    }
}
