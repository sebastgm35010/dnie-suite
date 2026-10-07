package pe.gob.reniec.dnie.signing.link

import kotlinx.coroutines.CompletableDeferred
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface

/** Single owner of physical card/PACE. Jobs borrow it; failures invalidate it. */
class SuiteCardSlot : AutoCloseable {
    private var card: DnieTransceiverInterface? = null
    private var signal = CompletableDeferred<Unit>()
    private var selected = false
    private var present: () -> Boolean = { true }
    private var inUse = false
    private var closed = false
    @Synchronized fun offer(factory: () -> Pair<DnieTransceiverInterface, () -> Boolean>): Boolean {
        if (closed || inUse) return false
        if (card != null && (!selected || present())) return false
        if (card != null) invalidate()
        val next = factory()
        card = next.first; present = next.second; selected = false
        signal.complete(Unit); return true
    }
    @Synchronized fun finishJob() { inUse = false }
    suspend fun acquire(): DnieTransceiverInterface {
        val wait = synchronized(this) { check(!closed); signal }
        wait.await()
        val owned = synchronized(this) { check(!closed); inUse = true; card ?: error("Card removed") }
        return object : DnieTransceiverInterface by owned {
            override suspend fun selectPkiApplet(): Result<Unit> {
                if (synchronized(this@SuiteCardSlot) { selected }) return Result.success(Unit)
                return owned.selectPkiApplet().also { if (it.isSuccess) synchronized(this@SuiteCardSlot) { selected = true } }
            }
            override fun close() {} // Endpoint owns job credentials; slot owns physical transport.
        }
    }
    @Synchronized fun invalidate() {
        runCatching { card?.close() }; card = null; selected = false; inUse = false
        signal.cancel(); signal = CompletableDeferred()
    }
    @Synchronized override fun close() {
        if (!closed) { closed = true; signal.cancel(); runCatching { card?.close() }; card = null; selected = false }
    }
}
