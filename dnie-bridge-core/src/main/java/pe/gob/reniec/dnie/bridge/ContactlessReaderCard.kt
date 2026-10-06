package pe.gob.reniec.dnie.bridge

/** Reader-local GET DATA must not enter the card's PACE session or advance its SSC. */
class ContactlessReaderCard(private val card: BridgeCard, discoveredUid: ByteArray) : BridgeCard {
    private val uid = discoveredUid.copyOf()
    private var closed = false
    override val atr: ByteArray get() = card.atr
    override fun transmit(command: ByteArray): ByteArray {
        check(!closed)
        if (command.size >= 2 && command[0] == 0xFF.toByte() && command[1] == 0xCA.toByte()) {
            if (command.size != 5) return byteArrayOf(0x67, 0)
            if (command[2] != 0.toByte() || command[3] != 0.toByte())
                return byteArrayOf(0x6A, 0x81.toByte()) // No complete ATS is available from historicalBytes.
            if (uid.size !in setOf(4, 7, 10)) return byteArrayOf(0x6A, 0x81.toByte())
            val le = command[4].toInt() and 255
            if (le != 0 && le < uid.size) return byteArrayOf(0x6C, uid.size.toByte())
            return uid + byteArrayOf(0x90.toByte(), 0)
        }
        return card.transmit(command)
    }
    override fun reset() = card.reset()
    override fun powerOff() = card.powerOff()
    override fun checkPresence() = card.checkPresence()
    override fun close() {
        if (!closed) { closed = true; try { card.close() } finally { uid.fill(0) } }
    }
}
