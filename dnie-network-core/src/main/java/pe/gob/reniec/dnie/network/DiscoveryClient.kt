package pe.gob.reniec.dnie.network

import java.net.*

/** Fresh challenge per lookup; never accepts unsolicited/replayed or unauthenticated hints. */
object DiscoveryClient {
    fun broadcasts(): List<InetAddress> = NetworkInterface.getNetworkInterfaces().toList().filter {it.isUp && !it.isLoopback}
        .flatMap {it.interfaceAddresses}.filter {DiscoveryWire.allowed(it.address)}.mapNotNull {it.broadcast}.distinct()
    fun find(service: DiscoveryService,pin: ByteArray,token: ByteArray,targets: List<InetAddress>,
        timeoutMs: Int=1200,port: Int=DiscoveryWire.PORT,allowLoopback: Boolean=false,checkCancelled: ()->Unit={}): List<InetAddress> {
        require(timeoutMs in 100..5000 && targets.size<=64)
        val query=DiscoveryWire.query(service,pin,DiscoveryWire.nonce(),token)
        DatagramSocket().use {socket ->
            socket.broadcast=true;socket.soTimeout=150
            targets.distinct().forEach {target->runCatching {socket.send(DatagramPacket(query,query.size,target,port))}}
            val until=System.nanoTime()+timeoutMs*1_000_000L
            while(System.nanoTime()<until) {
                checkCancelled()
                val response=DatagramPacket(ByteArray(DiscoveryWire.MAX_REPLY+1),DiscoveryWire.MAX_REPLY+1)
                try {socket.receive(response)}catch(_: SocketTimeoutException){continue}catch(_: SocketException){continue}
                if(!DiscoveryWire.allowed(response.address) && !(allowLoopback && response.address.isLoopbackAddress))continue
                val found=DiscoveryWire.addresses(response.data.copyOf(response.length),query,token) ?: continue
                return (listOf(response.address)+found).distinct()
            }
        }
        return emptyList()
    }
}
