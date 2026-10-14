package pe.gob.reniec.dnie.desktop.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class SuiteNetworkAddressTest {
    @Test fun routeToPhoneTakesPriorityEvenWhenOldInterfaceRemainsAssigned() {
        val assigned = listOf("192.168.1.8", "100.104.106.125")
        assertEquals("192.168.1.8", SuiteNetworkAddress.select("192.168.1.5", assigned, "192.168.1.8"))
        assertEquals("192.168.1.8", SuiteNetworkAddress.select("100.104.106.125", assigned, "192.168.1.8"))
        assertEquals("100.104.106.125", SuiteNetworkAddress.select("", assigned, "100.104.106.125"))
        assertEquals("", SuiteNetworkAddress.select("192.168.1.5", emptyList(), null))
    }
}
