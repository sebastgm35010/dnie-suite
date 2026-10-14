package pe.gob.reniec.dnie.network

import java.net.*
import java.util.UUID

/** One bounded UDP socket, many independent services/identities. No offers after close. */
class AuthenticatedDiscoveryResponder(private val port: Int=DiscoveryWire.PORT,private val allowLoopback: Boolean=false): AutoCloseable {
    private data class Offer(val service: DiscoveryService,val pin: ByteArray,val token: ByteArray,val addresses: ()->List<InetAddress>)
    private val lock=Any()
    private val offers=linkedMapOf<UUID,Offer>()
    private var socket: DatagramSocket?=null
    fun offer(service: DiscoveryService,pin: ByteArray,token: ByteArray,addresses: ()->List<InetAddress>): AutoCloseable = synchronized(lock) {
        require(pin.size==32 && token.size==32 && offers.size<8)
        if(socket==null) {
            val server=DatagramSocket(null)
            try {server.reuseAddress=false;server.bind(InetSocketAddress(this@AuthenticatedDiscoveryResponder.port))}
            catch(failure: Exception){server.close();throw failure}
            socket=server
            Thread({serve(server)},"dnie-network-discovery").apply {isDaemon=true;start()}
        }
        val id=UUID.randomUUID();offers[id]=Offer(service,pin.clone(),token.clone(),addresses)
        AutoCloseable { synchronized(lock) {
            offers.remove(id)?.let {it.pin.fill(0);it.token.fill(0)}
            if(offers.isEmpty()) {socket?.close();socket=null}
        } }
    }
    private fun serve(server: DatagramSocket) {
        while(!server.isClosed) try {
            val packet=DatagramPacket(ByteArray(DiscoveryWire.QUERY_SIZE+1),DiscoveryWire.QUERY_SIZE+1);server.receive(packet)
            if(packet.length!=DiscoveryWire.QUERY_SIZE || !(DiscoveryWire.allowed(packet.address) || allowLoopback && packet.address.isLoopbackAddress))continue
            val query=packet.data.copyOf(packet.length)
            synchronized(lock) {
                for(offer in offers.values) if(DiscoveryWire.acceptsQuery(query,offer.service,offer.pin,offer.token)) {
                    val response=DiscoveryWire.reply(query,offer.service,offer.pin,offer.token,offer.addresses())
                    server.send(DatagramPacket(response,response.size,packet.address,packet.port));break
                }
            }
        } catch(_: Exception) {if(server.isClosed)return}
    }
    override fun close()=synchronized(lock) {
        offers.values.forEach {it.pin.fill(0);it.token.fill(0)};offers.clear();socket?.close();socket=null
    }
}

object PhoneDiscovery {
    private val responder=AuthenticatedDiscoveryResponder()
    fun addresses(): List<InetAddress> = NetworkInterface.getNetworkInterfaces().toList().filter {it.isUp && !it.isLoopback}
        .flatMap {it.inetAddresses.toList()}.filter(DiscoveryWire::allowed).distinct()
    fun offer(service: DiscoveryService,pin: ByteArray,token: ByteArray): AutoCloseable = responder.offer(service,pin,token,::addresses)
}
