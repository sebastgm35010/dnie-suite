package pe.gob.reniec.dnie.bridge

/** Serialized unrestricted APDU forwarding. The supplied card owns physical/secure adaptation. */
class TransparentBridge(private val card: BridgeCard) : BridgeProcessor {
    private var closed = false
    @Synchronized override fun process(frame: ByteArray): ByteArray {
        check(!closed)
        require(frame.isNotEmpty())
        return try {
            if (frame.size == 1) when (frame[0].toInt() and 255) {
                4 -> card.atr.copyOf()
                5 -> { card.checkPresence(); byteArrayOf(1) }
                0 -> { card.powerOff(); byteArrayOf(1) }
                1, 2 -> card.reset()
                else -> error("Unknown control")
            } else card.transmit(frame)
        } catch (failure: Exception) {
            close()
            throw IllegalStateException("Transparent card transport failed", failure)
        }
    }
    @Synchronized override fun close() { if (!closed) { closed = true; card.close() } }
}
