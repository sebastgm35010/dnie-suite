package pe.gob.reniec.dnie.bridge.android

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel

class BridgeConnectionTest {
    @Test fun cancelBeforeTlsNeverTouchesCardAndClosesOnce() {
        var transmitted = 0
        var closed = 0
        val physical = object : DnieCardChannel {
            override fun transceive(command: ByteArray): ByteArray { transmitted++; error("Must not run") }
            override fun close() { closed++ }
            override fun isConnected() = false
            override fun setTimeout(timeoutMs: Int) {}
        }
        BridgePairing("127.0.0.1", 39443, ByteArray(32), ByteArray(32), Long.MAX_VALUE).use { pairing ->
            val session = BridgeConnection(pairing) { physical }
            session.close()
            session.close()
            assertThrows(IllegalStateException::class.java) { session.run("123456", byteArrayOf(1)) {} }
            assertEquals(0, transmitted)
            assertEquals(0, closed) // No physical adapter created before peer authentication.
        }
    }
}
