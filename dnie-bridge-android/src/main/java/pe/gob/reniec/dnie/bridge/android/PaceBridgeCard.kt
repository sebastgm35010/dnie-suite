package pe.gob.reniec.dnie.bridge.android

import pe.gob.reniec.dnie.bridge.BridgeCard
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel
import pe.gob.reniec.dnie.core.nfc.PaceBridgeChannel

/** mappedAtr must describe this NFC connection, never an invented contact-card identity. */
class PaceBridgeCard private constructor(
    private val secure: DnieCardChannel,
    mappedAtr: ByteArray
) : BridgeCard {
    private val atrBytes = mappedAtr.copyOf()
    override val atr: ByteArray get() = atrBytes.copyOf()
    override fun transmit(command: ByteArray) = secure.transceive(command)
    override fun close() { secure.close(); atrBytes.fill(0) }
    companion object {
        fun open(freshNfc: DnieCardChannel, can: String, mappedAtr: ByteArray): PaceBridgeCard {
            require(mappedAtr.size in 1..33)
            return PaceBridgeCard(PaceBridgeChannel.open(freshNfc, can), mappedAtr)
        }
    }
}
