package pe.gob.reniec.dnie.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import pe.gob.reniec.dnie.network.*
import pe.gob.reniec.dnie.data.network.AndroidNetworkPresence

/** Only synthetic public test credentials; no NFC, real enrollment or PC/SC operations. */
class NetworkDiscoveryInstrumentedTest {
    private val token=ByteArray(32) {it.toByte()}
    private val pin=ByteArray(32) {(255-it).toByte()}
    @Test fun twoIndependentServicesDiscoverLocallyAndCloseIndependently() {
        val address=PhoneDiscovery.addresses().firstOrNull {it.hostAddress?.startsWith("192.168.")==true}
        assumeTrue("Requires a Wi-Fi private address",address!=null)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bridge=AndroidNetworkPresence(context).offer(DiscoveryService.BRIDGE,pin,token)
        val suite=AndroidNetworkPresence(context).offer(DiscoveryService.SUITE,pin,token)
        try {
            assertTrue(DiscoveryClient.find(DiscoveryService.BRIDGE,pin,token,listOf(address!!),2000).isNotEmpty())
            bridge.close()
            assertTrue(DiscoveryClient.find(DiscoveryService.BRIDGE,pin,token,listOf(address),250).isEmpty())
            assertTrue(DiscoveryClient.find(DiscoveryService.SUITE,pin,token,listOf(address),2000).isNotEmpty())
            assertTrue(DiscoveryClient.find(DiscoveryService.SUITE,pin,ByteArray(32),listOf(address),250).isEmpty())
        } finally {bridge.close();suite.close()}
    }
    @Test fun syntheticPcDiscoveryWindow() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("networkWindow")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val offer=AndroidNetworkPresence(context).offer(DiscoveryService.BRIDGE,pin,token)
        try {
            InstrumentationRegistry.getInstrumentation().sendStatus(2,android.os.Bundle().apply {putString("stream","DISCOVERY_READY\n")})
            val until=android.os.SystemClock.elapsedRealtime()+40_000
            while(android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(100)
        } finally {offer.close()}
    }
}
