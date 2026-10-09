package pe.gob.reniec.dnie.bridge.android

import pe.gob.reniec.dnie.core.nfc.DnieCardChannel
import pe.gob.reniec.dnie.bridge.BridgeCard
import pe.gob.reniec.dnie.bridge.BridgeMode
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** Owns a fresh NFC transport even while TLS or PACE is in progress. Run/close on IO. */
class BridgeConnection(
    pairing: BridgePairing,
    private val decorate: (BridgeCard) -> BridgeCard = { it },
    private val smDiagnostic: (String, String) -> Unit = { _, _ -> },
    settings: pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings = pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings(),
    transportSocket: (() -> java.net.Socket)? = null,
    private val cancelPendingCard: () -> Unit = {},
    private val physicalFactory: () -> DnieCardChannel
) : Closeable {
    private val client = PinnedBridgeClient(pairing, settings, transportSocket)
    private val closed = AtomicBoolean()
    @Volatile private var physical: DnieCardChannel? = null

    fun run(can: String, atr: ByteArray, mode: BridgeMode, onEvent: (String) -> Unit = {}, onPhase: (Phase) -> Unit) =
        run(can, atr, onEvent, mode.windowsPace, mode.translatePace, onPhase)

    fun run(can: String, atr: ByteArray, onEvent: (String) -> Unit = {}, windowsPace: Boolean = false, translatePace: Boolean = false, onPhase: (Phase) -> Unit) {
        runWithCard(can, { atr }, onEvent, windowsPace, translatePace, false, onPhase)
    }

    fun runAwaitingCard(can: String, mode: BridgeMode, awaitAtr: () -> ByteArray,
        onEvent: (String) -> Unit = {}, onPhase: (Phase) -> Unit) =
        runWithCard(can, awaitAtr, onEvent, mode.windowsPace, mode.translatePace, true, onPhase)

    private fun runWithCard(can: String, awaitAtr: () -> ByteArray, onEvent: (String) -> Unit,
        windowsPace: Boolean, translatePace: Boolean, deferred: Boolean, onPhase: (Phase) -> Unit) {
        try {
            check(!closed.get())
            onPhase(Phase.AUTHENTICATING_PC)
            client.run(onEvent, restricted = false) {
                check(!closed.get())
                if (deferred) onPhase(Phase.WAITING_CARD)
                val atr = awaitAtr()
                if (!windowsPace) onPhase(Phase.OPENING_PACE)
                val factory = {
                    val next = physicalFactory()
                    physical = next
                    if (closed.get()) { next.close(); error("Session cancelled") }
                    next
                }
                val base = if (windowsPace) TransparentNfcCard(atr, factory) else RestartablePaceCard.open(can, atr, factory, onEvent)
                val card = if (translatePace && !windowsPace) TranslatedPaceCard(base, can, smDiagnostic) else base
                if (closed.get()) { card.close(); error("Session cancelled") }
                onPhase(if (windowsPace) Phase.RAW_ACTIVE else Phase.PACE_ACTIVE)
                try { decorate(card) } catch (failure: Exception) { card.close(); throw failure }
                    .also { if (deferred) atr.fill(0) }
            }
        } finally { close() }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            cancelPendingCard()
            try { client.close() } finally { physical?.close() }
        }
    }

    enum class Phase { AUTHENTICATING_PC, WAITING_CARD, OPENING_PACE, PACE_ACTIVE, RAW_ACTIVE }
}
