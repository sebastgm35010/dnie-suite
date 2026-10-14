package pe.gob.reniec.dnie.network

import java.net.Inet4Address
import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class DiscoveryService(val id: Byte, val tcpPort: Int) { BRIDGE(1,39444), SUITE(2,39446) }

/** Routing hints only. TLS pin and encrypted token authentication remain mandatory. */
object DiscoveryWire {
    const val PORT=39445
    const val QUERY_SIZE=105
    const val MAX_REPLY=138
    private val queryMagic="DNIFIND1".toByteArray(Charsets.US_ASCII)
    private val replyMagic="DNIFNDR1".toByteArray(Charsets.US_ASCII)
    fun allowed(address: InetAddress): Boolean {
        if(address !is Inet4Address)return false
        val b=address.address.map {it.toInt() and 255}
        return b[0]==10 || b[0]==172 && b[1] in 16..31 || b[0]==192 && b[1]==168 || b[0]==100 && b[1] in 64..127
    }
    fun allowed(text: String): Boolean = text.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) &&
        text.split('.').all {it.toInt() in 0..255} && allowed(InetAddress.getByName(text))
    fun nonce()=ByteArray(32).also(SecureRandom()::nextBytes)
    private fun mac(token: ByteArray,body: ByteArray): ByteArray {
        require(token.size==32)
        return Mac.getInstance("HmacSHA256").run {init(SecretKeySpec(token,"HmacSHA256"));doFinal(body)}
    }
    fun query(service: DiscoveryService,pin: ByteArray,nonce: ByteArray,token: ByteArray): ByteArray {
        require(pin.size==32 && nonce.size==32)
        val body=queryMagic+byteArrayOf(service.id)+pin+nonce
        return body+mac(token,body)
    }
    fun acceptsQuery(packet: ByteArray,service: DiscoveryService,pin: ByteArray,token: ByteArray): Boolean =
        packet.size==QUERY_SIZE && packet.copyOfRange(0,8).contentEquals(queryMagic) && packet[8]==service.id &&
            MessageDigest.isEqual(packet.copyOfRange(9,41),pin) && MessageDigest.isEqual(packet.copyOfRange(73,105),mac(token,packet.copyOfRange(0,73)))
    fun reply(query: ByteArray,service: DiscoveryService,pin: ByteArray,token: ByteArray,addresses: List<InetAddress>): ByteArray {
        require(acceptsQuery(query,service,pin,token))
        val ips=addresses.filter(::allowed).distinct().take(8)
        val body=replyMagic+byteArrayOf(service.id)+pin+query.copyOfRange(41,73)+byteArrayOf(ips.size.toByte())+
            ips.fold(ByteArray(0)) {out,address->out+address.address}
        return body+mac(token,body)
    }
    fun addresses(reply: ByteArray,query: ByteArray,token: ByteArray): List<InetAddress>? {
        if(query.size!=QUERY_SIZE || reply.size !in 106..MAX_REPLY || !reply.copyOfRange(0,8).contentEquals(replyMagic) ||
            !reply.copyOfRange(8,73).contentEquals(query.copyOfRange(8,73))) return null
        val count=reply[73].toInt() and 255
        if(count>8 || reply.size!=106+4*count || !MessageDigest.isEqual(reply.takeLast(32).toByteArray(),mac(token,reply.copyOfRange(0,reply.size-32))))return null
        val ips=(0 until count).map {InetAddress.getByAddress(reply.copyOfRange(74+4*it,78+4*it))}
        return ips.takeIf {it.all(::allowed)}
    }
}
