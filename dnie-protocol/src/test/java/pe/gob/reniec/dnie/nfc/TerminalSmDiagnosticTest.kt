package pe.gob.reniec.dnie.nfc

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.core.nfc.*
import pe.gob.reniec.dnie.core.apdu.CommandApdu

class TerminalSmDiagnosticTest {
    private val enc = ByteArray(32) { it.toByte() }
    private val mac = ByteArray(32) { (it+32).toByte() }
    private val channel = object : DnieCardChannel {
        override fun transceive(command: ByteArray): ByteArray = error("No physical command allowed")
        override fun isConnected() = true
        override fun setTimeout(timeoutMs: Int) {}
        override fun close() {}
    }
    private val select = CommandApdu(0,0xa4.toByte(),4,0x0c,byteArrayOf(1,2))
    @Test fun resetCounterIsDiagnosedButNeverAcceptedAndSessionStaysClosed() {
        val pc = PaceSecureChannel(channel,enc,mac)
        val endpoint = TerminalSecureMessaging(enc,mac,"test-context:1")
        repeat(3) {
            endpoint.decode(pc.wrapCommand(select))
            pc.unwrapResponse(endpoint.encode(byteArrayOf(0x90.toByte(),0)))
        }
        val restarted = PaceSecureChannel(channel,enc,mac)
        val failure = assertThrows(TerminalSmIntegrityException::class.java) { endpoint.decode(restarted.wrapCommand(select)) }
        assertEquals(7L,failure.expectedSsc)
        assertEquals(listOf(1L),failure.matchingDiagnosticSsc)
        assertEquals("test-context:1",failure.contextId)
        assertThrows(IllegalStateException::class.java) { endpoint.decode(pc.wrapCommand(select)) }
    }
    @Test fun differentKeysAreNotMisreportedAsCounterMismatch() {
        val pc = PaceSecureChannel(channel,enc,ByteArray(32) { 77 })
        val endpoint = TerminalSecureMessaging(enc,mac)
        val failure = assertThrows(TerminalSmIntegrityException::class.java) { endpoint.decode(pc.wrapCommand(select)) }
        assertEquals(1L,failure.expectedSsc)
        assertTrue(failure.matchingDiagnosticSsc.isEmpty())
    }
    @Test fun skippedCommandMatchesDiagnosticOffsetAndStillRejects() {
        val pc = PaceSecureChannel(channel,enc,mac)
        val endpoint = TerminalSecureMessaging(enc,mac)
        pc.wrapCommand(select) // The terminal advanced without delivering a command.
        val failure = assertThrows(TerminalSmIntegrityException::class.java) { endpoint.decode(pc.wrapCommand(select)) }
        assertEquals(1L,failure.expectedSsc)
        assertEquals(listOf(2L),failure.matchingDiagnosticSsc)
    }
}
