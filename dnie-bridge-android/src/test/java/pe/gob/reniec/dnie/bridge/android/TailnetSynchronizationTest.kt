package pe.gob.reniec.dnie.bridge.android

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TailnetSynchronizationTest {
    @Test fun fragmentedSynchronizationProducesFreshTokenAndOnlySendsRequest() {
        val token = ByteArray(32) { it.toByte() }
        val bytes = "DNISYNC1".toByteArray() + token
        val fragmented = object : ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b,off,minOf(1,len))
        }
        val output = ByteArrayOutputStream()
        assertArrayEquals(token,TailnetSynchronization.request(fragmented,output))
        assertArrayEquals("DNISYNC1".toByteArray(),output.toByteArray())
    }
    @Test fun truncatedOrWrongProtocolCannotContinueIntoCardSession() {
        for (bytes in listOf(ByteArray(3), "BADSYNC1".toByteArray()+ByteArray(32))) {
            assertThrows(IllegalStateException::class.java) { TailnetSynchronization.request(ByteArrayInputStream(bytes),ByteArrayOutputStream()) }
        }
    }
}
