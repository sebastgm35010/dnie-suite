package pe.gob.reniec.dnie.data.network

import android.content.Context
import android.net.wifi.WifiManager
import pe.gob.reniec.dnie.network.DiscoveryService
import pe.gob.reniec.dnie.network.PhoneDiscovery

/** Owns the foreground discovery offer and Wi-Fi receive filter lifetime together. */
class AndroidNetworkPresence(private val context: Context) {
    fun offer(service: DiscoveryService,pin: ByteArray,token: ByteArray): AutoCloseable = receive {PhoneDiscovery.offer(service,pin,token)}
    fun bridge(pairing: pe.gob.reniec.dnie.bridge.android.BridgePairing): AutoCloseable = receive {pairing.discoveryOffer()}
    private fun receive(register: ()->AutoCloseable): AutoCloseable {
        val wifi=context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock=runCatching {wifi?.createMulticastLock("dnie-network-presence")?.apply {setReferenceCounted(false);acquire()}}.getOrNull()
        val presence=try {register()}catch(failure: Exception){runCatching {lock?.release()};throw failure}
        return AutoCloseable {presence.close();runCatching {lock?.release()} }
    }
}
