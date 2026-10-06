package pe.gob.reniec.dnie.bridge.android

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import pe.gob.reniec.dnie.bridge.BridgeCard
import java.io.File

/** Opt-in real .NET/JVM interoperability; all card bytes are synthetic, no NFC required. */
class WindowsInteropTest {
    @Test fun tlsAndReadPolicyAgainstWindowsFixture() {
        val path = System.getenv("DNIE_BRIDGE_FIXTURE")
        assumeTrue("Run Windows fixture and set DNIE_BRIDGE_FIXTURE", path != null)
        var opens = 0
        var commands = 0
        var closes = 0
        File(checkNotNull(path)).inputStream().use { BridgeProvision.read(it) }.use { pairing ->
            PinnedBridgeClient(pairing).run {
                opens++
                object : BridgeCard {
                    override val atr = byteArrayOf(0x3b, 0x80.toByte(), 0x80.toByte(), 1, 1)
                    override fun transmit(command: ByteArray): ByteArray {
                        commands++
                        return when (command.joinToString("") { "%02X".format(it) }) {
                            "00A4020C02CE81" -> byteArrayOf(0x90.toByte(), 0)
                            "00B0000003" -> byteArrayOf(1, 2, 3, 0x90.toByte(), 0)
                            else -> error("Unexpected card command")
                        }
                    }
                    override fun close() { closes++ }
                }
            }
        }
        assertEquals(1, opens)
        assertEquals(2, commands) // VERIFY must have been rejected without reaching card.
        assertEquals(1, closes)
    }
}
