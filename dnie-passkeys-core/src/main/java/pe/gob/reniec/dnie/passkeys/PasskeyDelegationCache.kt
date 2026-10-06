package pe.gob.reniec.dnie.passkeys

/** Positive DAL decisions only, scoped to exact RP, package and current signing certificate. */
class PasskeyDelegationCache(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    data class Identity(val rp: String, val packageName: String, val fingerprint: String)
    private val approvals = linkedMapOf<Identity, Long>()
    @Synchronized fun contains(identity: Identity): Boolean {
        approvals.entries.removeAll { now() >= it.value }
        return identity in approvals
    }
    @Synchronized fun approve(identity: Identity) {
        approvals.entries.removeAll { now() >= it.value }
        approvals.remove(identity)
        while (approvals.size >= 32) approvals.remove(approvals.keys.first())
        approvals[identity] = now() + 60_000
    }
    @Synchronized fun clear() { approvals.clear() }
}
