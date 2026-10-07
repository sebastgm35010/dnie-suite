package pe.gob.reniec.dnie.bridge
import org.junit.Assert.*
import org.junit.Test
class BridgeConnectionMethodTest {
    @Test fun localAndUsbCannotUseClearTailnetTransport() {
        for (method in listOf(BridgeConnectionMethod.LOCAL_NETWORK, BridgeConnectionMethod.USB_NETWORK)) {
            assertTrue(method.usesPairing)
            assertTrue(method.accepts("tls"))
            assertFalse(method.accepts("tailscale_tcp"))
            assertFalse(method.accepts("tailscale_sync"))
        }
    }
    @Test fun tailnetHasExplicitTransportSelection() {
        assertFalse(BridgeConnectionMethod.TAILSCALE.usesPairing)
        assertTrue(BridgeConnectionMethod.TAILSCALE.accepts("tailscale_sync"))
        assertTrue(BridgeConnectionMethod.TAILSCALE.accepts("tailscale_tcp"))
        assertFalse(BridgeConnectionMethod.TAILSCALE.accepts("tls"))
    }
}
