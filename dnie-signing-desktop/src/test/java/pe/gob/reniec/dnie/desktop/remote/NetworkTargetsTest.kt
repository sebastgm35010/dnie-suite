package pe.gob.reniec.dnie.desktop.remote

import org.junit.Test
import org.junit.Assert.*

class NetworkTargetsTest {
    @Test fun onlyBoundedPrivateTailnetLiteralHintsCanLeaveLocalDaemonMetadata() {
        val metadata="""{"TailscaleIPs":["100.68.70.85","8.8.8.8","100.63.0.1","100.128.0.1","100.999.0.1","127.0.0.1"]}"""
        assertEquals(listOf("100.68.70.85"),NetworkTargets.extractTailnetIps(metadata).map {it.hostAddress})
        assertThrows(IllegalArgumentException::class.java) {NetworkTargets.extractTailnetIps("x".repeat(1024*1024+1))}
        assertTrue(NetworkTargets.tailnetHost("phone.tail1234.ts.net"))
        assertFalse(NetworkTargets.tailnetHost("https://phone.tail1234.ts.net"))
        assertTrue(NetworkTargets.resolveHint("8.8.8.8").isEmpty())
    }
}
