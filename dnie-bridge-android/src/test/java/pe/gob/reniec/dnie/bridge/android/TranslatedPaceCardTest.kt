package pe.gob.reniec.dnie.bridge.android

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.bridge.BridgeCard
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.crypto.PaceSecurityInfo
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.core.nfc.*

class TranslatedPaceCardTest {
    private class Card : BridgeCard {
        override val atr=byteArrayOf(0x3b,0)
        var resets=0;var powers=0;var commands=0;var closed=false
        override fun transmit(command: ByteArray): ByteArray {commands++;return byteArrayOf(0x90.toByte(),0)}
        override fun reset(): ByteArray {resets++;return atr}
        override fun powerOff(){powers++}
        override fun close(){closed=true}
    }
    private fun authenticate(card: BridgeCard): PaceSecureChannel {
        val wire=object : DnieCardChannel {
            override fun transceive(command: ByteArray)=card.transmit(command)
            override fun isConnected()=true
            override fun setTimeout(timeoutMs: Int){}
            override fun close()=card.close()
        }
        val profile=PaceSecurityInfo("0.4.0.127.0.7.2.2.4.4.4",byteArrayOf(4,0,0x7f,0,7,2,2,4,4,4),2,13)
        return PaceHandler(ApduLogger(enabled=false)).performPace(wire,"123456",profileSecurityInfo=profile)
    }
    @Test fun powerOffRequiresPhysicalRestartBeforeNewPcAuthentication() {
        val physical=Card()
        TranslatedPaceCard(physical,"123456").use { translated ->
            authenticate(translated)
            assertEquals(0,physical.commands)
            translated.powerOff()
            val pc=authenticate(translated)
            assertEquals(1,physical.resets);assertEquals(1,physical.powers)
            assertArrayEquals(byteArrayOf(0x90.toByte(),0),pc.transceive(CommandApdu(0,0xb0.toByte(),0,0,le=16).toByteArray()))
            assertEquals(1,physical.commands)
        }
        assertTrue(physical.closed)
    }
    @Test fun resetRetiresPcKeysBeforeAnotherProtectedCommand() {
        val physical=Card()
        TranslatedPaceCard(physical,"123456").use { translated ->
            val pc=authenticate(translated)
            translated.reset()
            val stale=pc.wrapCommand(CommandApdu(0,0xb0.toByte(),0,0,le=16))
            assertArrayEquals(byteArrayOf(0x69,0x85.toByte()),translated.transmit(stale))
            assertEquals(0,physical.commands)
            val fresh = authenticate(translated)
            assertArrayEquals(byteArrayOf(0x90.toByte(),0),fresh.transceive(CommandApdu(0,0xb0.toByte(),0,0,le=16).toByteArray()))
            assertEquals(1,physical.commands)
        }
    }
    @Test fun staleCommandAfterPowerOffIsRejectedThenNewPaceSucceeds() {
        val physical = Card()
        TranslatedPaceCard(physical,"123456").use { translated ->
            val old = authenticate(translated)
            translated.powerOff()
            val stale = old.wrapCommand(CommandApdu(0,0x20,0,3,byteArrayOf(1,2,3)))
            assertArrayEquals(byteArrayOf(0x69,0x85.toByte()),translated.transmit(stale))
            assertEquals(0,physical.commands)
            assertEquals(1,physical.resets)
            val fresh = authenticate(translated)
            assertArrayEquals(byteArrayOf(0x90.toByte(),0),fresh.transceive(CommandApdu(0,0xb0.toByte(),0,0,le=16).toByteArray()))
            assertEquals(1,physical.commands)
            assertEquals(1,physical.resets)
        }
    }
}
