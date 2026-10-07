package pe.gob.reniec.dnie.signing.link

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface

enum class SuiteCardState { ABSENT, DETECTED, PREPARING, READY, IN_USE, REMOVED, FAILED, CLOSED }

/** Owns physical NFC/PACE independently of TLS. Presence checks never send APDU. */
class SuiteCardSlot : AutoCloseable {
    private val mutable = MutableStateFlow(SuiteCardState.ABSENT)
    val state = mutable.asStateFlow()
    private var card: DnieTransceiverInterface? = null
    private var signal = CompletableDeferred<Unit>()
    private var selected = false
    private var present: () -> Boolean = { true }
    private var inUse = false
    private var offering = false
    private var closed = false
    fun offer(factory: () -> Pair<DnieTransceiverInterface, () -> Boolean>): Boolean {
        synchronized(this) {
            if (closed || inUse || offering) return false
            if (card != null && runCatching(present).getOrDefault(false)) return false
            if (card != null) invalidateLocked(SuiteCardState.REMOVED)
            offering = true
        }
        // Factory may connect NFC on IO: never hold the lifecycle lock during connect.
        val next = try { factory() } catch (failure: Exception) {
            synchronized(this) { offering = false; if (!closed) mutable.value = SuiteCardState.FAILED }
            throw failure
        }
        val accepted = synchronized(this) {
            offering = false
            if (closed) false else {
                card = next.first; present = next.second; selected = false
                mutable.value = SuiteCardState.DETECTED
                signal.complete(Unit); true
            }
        }
        if (!accepted) next.first.close()
        return accepted
    }
    @Synchronized fun pollPresence() {
        if (closed || offering || inUse || card == null) return
        if (!runCatching(present).getOrDefault(false)) invalidateLocked(SuiteCardState.REMOVED)
    }
    @Synchronized fun finishJob() {
        inUse = false
        if (card != null) {
            if (!runCatching(present).getOrDefault(false)) invalidateLocked(SuiteCardState.REMOVED)
            else mutable.value = if (selected) SuiteCardState.READY else SuiteCardState.DETECTED
        }
    }
    suspend fun acquire(): DnieTransceiverInterface {
        var owned: DnieTransceiverInterface? = null
        while (owned == null) {
            val wait = synchronized(this) {
                check(!closed)
                if (card != null && !runCatching(present).getOrDefault(false)) invalidateLocked(SuiteCardState.REMOVED)
                signal
            }
            wait.await()
            owned = synchronized(this) {
                check(!closed)
                if (card != null && runCatching(present).getOrDefault(false)) {
                    inUse = true; mutable.value = SuiteCardState.IN_USE; card
                } else {
                    if (card != null) invalidateLocked(SuiteCardState.REMOVED)
                    null
                }
            }
        }
        val current = owned ?: error("Card unavailable")
        return object : DnieTransceiverInterface by current {
            override suspend fun selectPkiApplet(): Result<Unit> {
                synchronized(this@SuiteCardSlot) {
                    if (closed || card !== current) return Result.failure(IllegalStateException("Card session ended"))
                    if (selected) return Result.success(Unit)
                    mutable.value = SuiteCardState.PREPARING
                }
                return current.selectPkiApplet().also { result -> synchronized(this@SuiteCardSlot) {
                    if (!closed && card === current) {
                        if (result.isSuccess) { selected = true; mutable.value = SuiteCardState.IN_USE }
                        else mutable.value = SuiteCardState.FAILED
                    }
                } }
            }
            override fun close() {} // Job releases credentials; slot owns the physical card.
        }
    }
    @Synchronized fun invalidate() { if (!closed) invalidateLocked(SuiteCardState.FAILED) }
    private fun invalidateLocked(reason: SuiteCardState) {
        runCatching { card?.close() }; card = null; selected = false; inUse = false
        // Don't cancel a pending BEGIN waiting for another physical discovery.
        if (signal.isCompleted) signal = CompletableDeferred()
        mutable.value = reason
    }
    @Synchronized override fun close() {
        if (!closed) {
            closed = true; signal.cancel(); runCatching { card?.close() }; card = null; selected = false
            mutable.value = SuiteCardState.CLOSED
        }
    }
}
