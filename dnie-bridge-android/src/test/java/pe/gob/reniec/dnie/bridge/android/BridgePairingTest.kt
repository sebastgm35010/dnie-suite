package pe.gob.reniec.dnie.bridge.android

import org.junit.Assert.*
import org.junit.Test

class BridgePairingTest {
    @Test fun secretOwnedAndCleared() {
        val token = ByteArray(32) { 1 }
        val pin = ByteArray(32) { 2 }
        val pairing = BridgePairing("127.0.0.1", 39443, pin, token, Long.MAX_VALUE)
        token.fill(3)
        pin.fill(4)
        assertEquals(1.toByte(), pairing.secret[0])
        assertEquals(2.toByte(), pairing.pin[0])
        pairing.close()
        assertTrue(pairing.secret.all { it == 0.toByte() })
    }
    @Test fun invalidProvisionRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BridgePairing("localhost", 39443, ByteArray(31), ByteArray(32), 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BridgePairing("localhost", 0, ByteArray(32), ByteArray(32), 0)
        }
    }
    @Test fun expiredPairingNeverTouchesCard() {
        val pairing = BridgePairing("127.0.0.1", 39443, ByteArray(32), ByteArray(32), 0)
        val client = PinnedBridgeClient(pairing)
        var touched = false
        assertThrows(IllegalStateException::class.java) {
            client.run { touched = true; error("Must not connect to card") }
        }
        assertFalse(touched)
        pairing.close()
    }
}
