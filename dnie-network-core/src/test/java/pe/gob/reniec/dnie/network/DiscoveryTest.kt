package pe.gob.reniec.dnie.network

import java.net.*
import org.junit.Test
import org.junit.Assert.*

class DiscoveryTest {
    private val token=ByteArray(32) {it.toByte()}
    private val pin=ByteArray(32) {(255-it).toByte()}
    private val nonce=ByteArray(32) {(32+it).toByte()}
    private fun hex(bytes: ByteArray)=bytes.joinToString("") {"%02X".format(it.toInt() and 255)}
    private fun query()=DiscoveryWire.query(DiscoveryService.BRIDGE,pin,nonce,token)
    @Test fun fixedVectorMatchesIndependentPythonAndCSharpImplementations() {
        val query=query()
        assertEquals("444E4946494E443101FFFEFDFCFBFAF9F8F7F6F5F4F3F2F1F0EFEEEDECEBEAE9E8E7E6E5E4E3E2E1E0202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F48758F7FC802107C165D4CE5D179BEABCC8CBAEEDFDF716D3A5D4338ECA01745",hex(query))
        val reply=DiscoveryWire.reply(query,DiscoveryService.BRIDGE,pin,token,listOf(InetAddress.getByName("192.168.1.11"),InetAddress.getByName("100.68.70.85")))
        assertEquals("444E49464E44523101FFFEFDFCFBFAF9F8F7F6F5F4F3F2F1F0EFEEEDECEBEAE9E8E7E6E5E4E3E2E1E0202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F02C0A8010B64444655F266F1BA2F8FDC24BE9E251CD8433E3194E50229108C7D6F25E26A28586A22FD",hex(reply))
        assertEquals(listOf("192.168.1.11","100.68.70.85"),DiscoveryWire.addresses(reply,query,token)!!.map {it.hostAddress})
        assertFalse(contains(query,token));assertFalse(contains(reply,token))
    }
    private fun contains(bytes: ByteArray,part: ByteArray)=(0..bytes.size-part.size).any {bytes.copyOfRange(it,it+part.size).contentEquals(part)}
    @Test fun rejectsTamperingWrongIdentityWrongSecretAndCrossServiceReflection() {
        val query=query()
        for(i in query.indices) {val bad=query.clone();bad[i]=(bad[i].toInt() xor 1).toByte();assertFalse(DiscoveryWire.acceptsQuery(bad,DiscoveryService.BRIDGE,pin,token))}
        assertFalse(DiscoveryWire.acceptsQuery(query,DiscoveryService.SUITE,pin,token))
        assertFalse(DiscoveryWire.acceptsQuery(query,DiscoveryService.BRIDGE,pin,ByteArray(32)))
        val reply=DiscoveryWire.reply(query,DiscoveryService.BRIDGE,pin,token,emptyList())
        for(i in reply.indices) {val bad=reply.clone();bad[i]=(bad[i].toInt() xor 1).toByte();assertNull(DiscoveryWire.addresses(bad,query,token))}
        assertNull(DiscoveryWire.addresses(query,query,token))
    }
    @Test fun rejectsReplayAgainstFreshChallengeAndOversizedOrTruncatedFrames() {
        val query=query();val reply=DiscoveryWire.reply(query,DiscoveryService.BRIDGE,pin,token,emptyList())
        val fresh=DiscoveryWire.query(DiscoveryService.BRIDGE,pin,DiscoveryWire.nonce(),token)
        assertNull(DiscoveryWire.addresses(reply,fresh,token))
        assertNull(DiscoveryWire.addresses(reply+byteArrayOf(0),query,token))
        assertNull(DiscoveryWire.addresses(reply.copyOf(99),query,token))
        assertFalse(DiscoveryWire.acceptsQuery(query+byteArrayOf(0),DiscoveryService.BRIDGE,pin,token))
    }
    @Test fun neverReturnsPublicLoopbackIpv6OrMoreThanEightHints() {
        val query=query();val addresses=listOf("8.8.8.8","127.0.0.1","::1","100.63.0.1","100.128.0.1")+(1..15).map {"192.168.1.$it"}
        val reply=DiscoveryWire.reply(query,DiscoveryService.BRIDGE,pin,token,addresses.map(InetAddress::getByName))
        assertEquals(138,reply.size);assertEquals(8,DiscoveryWire.addresses(reply,query,token)!!.size)
        assertTrue(DiscoveryWire.allowed("100.64.0.1"));assertTrue(DiscoveryWire.allowed("100.127.255.254"))
        assertFalse(DiscoveryWire.allowed("192.168.999.1"))
    }
    @Test fun liveLookupAuthenticatesNewAddressesAndOfferClosesWithoutLeakingKeys() {
        val port=DatagramSocket(0).use {it.localPort}
        val responder=AuthenticatedDiscoveryResponder(port,true)
        var address="192.168.1.11"
        val registration=responder.offer(DiscoveryService.BRIDGE,pin,token) {listOf(InetAddress.getByName(address))}
        try {
            fun find()=DiscoveryClient.find(DiscoveryService.BRIDGE,pin,token,listOf(InetAddress.getLoopbackAddress()),400,port,true)
            assertTrue(find().any {it.hostAddress==address})
            address="100.68.70.85";assertTrue(find().any {it.hostAddress==address})
            val wrong=DiscoveryClient.find(DiscoveryService.SUITE,pin,token,listOf(InetAddress.getLoopbackAddress()),200,port,true)
            assertTrue(wrong.isEmpty())
            assertThrows(InterruptedException::class.java) {DiscoveryClient.find(DiscoveryService.BRIDGE,pin,token,emptyList(),200,port,true) {throw InterruptedException()}}
            registration.close();assertTrue(find().isEmpty())
            assertArrayEquals(ByteArray(32) {it.toByte()},token)
        } finally {registration.close();responder.close()}
    }
}
