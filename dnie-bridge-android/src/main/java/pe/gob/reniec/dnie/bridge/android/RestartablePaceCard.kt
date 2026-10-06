package pe.gob.reniec.dnie.bridge.android

import pe.gob.reniec.dnie.bridge.BridgeCard
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel
import pe.gob.reniec.dnie.core.nfc.PaceBridgeChannel

/** Each restart retires SM and opens PACE on a fresh adapter of the same discovered tag. */
class RestartablePaceCard private constructor(
    can: String, atr: ByteArray,
    private val factory: () -> DnieCardChannel,
    private val establish: (DnieCardChannel, String) -> DnieCardChannel,
    private val event: (String) -> Unit
) : BridgeCard {
    private val secret = can.toCharArray()
    private val mappedAtr = atr.copyOf()
    @Volatile private var secure: DnieCardChannel? = null
    @Volatile private var closed = false
    override val atr: ByteArray get() { check(!closed); return mappedAtr.copyOf() }
    override fun transmit(command: ByteArray): ByteArray {
        check(!closed)
        // PC/SC may power down between clients without sending a new reset first.
        // Rebuild PACE before forwarding the next logical APDU, never reuse old SSC.
        if (secure == null) reset().fill(0)
        return checkNotNull(secure).transceive(command)
    }
    override fun reset(): ByteArray {
        check(!closed)
        event("reset_started")
        secure?.close(); secure = null
        val physical = factory()
        try {
            val opened = establish(physical, String(secret))
            if (closed) { opened.close(); error("Session cancelled") }
            secure = opened
            event("pace_ready")
            return atr
        } catch (failure: Exception) {
            physical.close(); close()
            event("reset_failed")
            throw IllegalStateException("Card restart failed", failure)
        }
    }
    override fun powerOff() {
        check(!closed)
        secure?.close(); secure = null
        event("card_powered_off")
    }
    override fun checkPresence() {
        check(!closed)
        // isConnected reflects a loss already known by Android, without issuing any APDU.
        secure?.let { check(it.isConnected()) { "Card disconnected" } }
    }
    override fun close() {
        closed = true
        secure?.close(); secure = null
        secret.fill('\u0000'); mappedAtr.fill(0)
    }
    companion object {
        fun open(can: String, atr: ByteArray, factory: () -> DnieCardChannel,
                 event: (String) -> Unit = {},
                 establish: (DnieCardChannel, String) -> DnieCardChannel = PaceBridgeChannel::open): RestartablePaceCard {
            require(can.length == 6 && can.all { it in '0'..'9' } && atr.size in 2..33)
            val card = RestartablePaceCard(can, atr, factory, establish, event)
            try { card.reset().fill(0); return card }
            catch (failure: Exception) { card.close(); throw failure }
        }
    }
}
