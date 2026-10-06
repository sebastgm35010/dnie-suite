package pe.gob.reniec.dnie.bridge

import java.io.Closeable

interface BridgeCard : Closeable {
    val atr: ByteArray
    fun transmit(command: ByteArray): ByteArray
    fun reset(): ByteArray = throw IllegalStateException("Fresh NFC session required")
    fun powerOff(): Unit = throw IllegalStateException("Power control unavailable")
    fun checkPresence() {}
}

/** First profile: certificate SELECT/READ only. PIN, signing, PACE and SM from PC forbidden. */
object BridgeReadPolicy {
    private val apps = setOf("A000000077030C60000000FE00000500", "E828BD080FD25047656E65726963")
    private val files = setOf(0xCE81, 0xCE82, 0xCE83)
    fun allows(apdu: ByteArray): Boolean {
        fun u(i: Int) = apdu[i].toInt() and 255
        if (apdu.size < 4 || u(0) != 0) return false
        return when (u(1)) {
            0xB0 -> apdu.size == 5 && u(2) < 0x80
            0xA4 -> when {
                u(2) == 4 && u(3) == 12 && apdu.size >= 6 && u(4) > 0 &&
                    apdu.size == 5 + u(4) -> apdu.copyOfRange(5, apdu.size)
                        .joinToString("") { "%02X".format(it) } in apps
                u(2) == 2 && u(3) in setOf(4, 12) && apdu.size == 7 && u(4) == 2 ->
                    ((u(5) shl 8) or u(6)) in files
                else -> false
            }
            else -> false
        }
    }
}

/** Serialized controls; a reset succeeds only after the adapter rebuilds its card session. */
interface BridgeProcessor : Closeable { fun process(frame: ByteArray): ByteArray? }

class ReadOnlyBridge(private val card: BridgeCard) : BridgeProcessor {
    private var closed = false
    private var certificateSelected = false
    @Synchronized override fun process(frame: ByteArray): ByteArray? {
        check(!closed) { "Bridge session closed" }
        require(frame.isNotEmpty())
        if (frame.size == 1) {
            return when (frame[0].toInt() and 255) {
                4 -> card.atr.copyOf().also { require(it.size in 1..33) }
                5 -> try {
                    card.checkPresence()
                    byteArrayOf(1) // Never changes card selection or SSC.
                } catch (_: Exception) { close(); throw IllegalStateException("Card session lost") }
                0, 1, 2 -> try {
                    certificateSelected = false
                    if (frame[0].toInt() == 0) { card.powerOff(); byteArrayOf(1) }
                    else card.reset().also { require(it.size in 2..33) }
                } catch (_: Exception) { close(); throw IllegalStateException("Card restart failed") }
                else -> { close(); throw IllegalArgumentException("Unknown control") }
            }
        }
        if (!BridgeReadPolicy.allows(frame)) return byteArrayOf(0x69, 0x85.toByte())
        val instruction = frame[1].toInt() and 255
        if (instruction == 0xB0 && !certificateSelected) return byteArrayOf(0x69, 0x85.toByte())
        if (instruction == 0xA4) certificateSelected = false
        return try {
            card.transmit(frame).also {
                require(it.size in 2..65535)
                if (instruction == 0xA4 && frame[2].toInt() == 2 &&
                    it[it.size - 2] == 0x90.toByte() && it.last() == 0.toByte()) certificateSelected = true
            }
        } catch (failure: Exception) {
            close()
            // Do not carry an NFC exception/payload into transport logs.
            throw IllegalStateException("Card session lost")
        }
    }
    @Synchronized override fun close() {
        if (!closed) { closed = true; card.close() }
    }
}
