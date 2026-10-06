package pe.gob.reniec.dnie.bridge.android

import pe.gob.reniec.dnie.bridge.BridgeCard
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel

/** Reopens the NFC transport on reset. This is not a guaranteed RF power cycle. */
class TransparentNfcCard(atr: ByteArray, private val factory: () -> DnieCardChannel) : BridgeCard {
    private val mappedAtr = atr.copyOf()
    private var channel: DnieCardChannel? = null
    private var closed = false
    override val atr: ByteArray get() = mappedAtr.copyOf()
    init { reset() }
    override fun transmit(command: ByteArray): ByteArray {
        check(!closed)
        if (channel == null) reset().fill(0)
        return checkNotNull(channel).transceive(command)
    }
    override fun reset(): ByteArray {
        check(!closed)
        channel?.close(); channel = null
        try { channel = factory(); return atr }
        catch (failure: Exception) { close(); throw failure }
    }
    override fun powerOff() { check(!closed); channel?.close(); channel = null }
    override fun checkPresence() { check(!closed); channel?.let { check(it.isConnected()) } }
    override fun close() {
        if (!closed) { closed = true; channel?.close(); channel = null; mappedAtr.fill(0) }
    }
}
