package pe.gob.reniec.dnie.bridge.android

import pe.gob.reniec.dnie.bridge.BridgeCard
import pe.gob.reniec.dnie.core.nfc.PaceTranslationEndpoint

/** Two independent PACE sessions. Construct only after physical PACE succeeds. */
class TranslatedPaceCard(private val physical: BridgeCard, can: String,
    private val diagnostic: (String, String) -> Unit = { _, _ -> }) : BridgeCard {
    private val password=can.toCharArray()
    private var closed=false
    private var endpoint: PaceTranslationEndpoint?=openEndpoint()
    private fun openEndpoint()=PaceTranslationEndpoint(String(password), physical::transmit).also { it.onContext = diagnostic }
    override val atr get()=physical.atr
    override fun transmit(command: ByteArray): ByteArray {
        check(!closed)
        if(endpoint==null){physical.reset().fill(0);endpoint=openEndpoint()}
        return checkNotNull(endpoint).transmit(command)
    }
    override fun reset(): ByteArray {
        check(!closed);endpoint?.close();endpoint=null
        return try {physical.reset().also {endpoint=openEndpoint()}}
        catch(failure: Exception){close();throw failure}
    }
    override fun powerOff(){check(!closed);endpoint?.close();endpoint=null;physical.powerOff()}
    override fun checkPresence(){check(!closed);physical.checkPresence()}
    override fun close(){if(!closed){closed=true;endpoint?.close();endpoint=null;password.fill('\u0000');physical.close()}}
}
