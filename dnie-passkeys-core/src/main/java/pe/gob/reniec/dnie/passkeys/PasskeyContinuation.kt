package pe.gob.reniec.dnie.passkeys

import java.util.UUID

/** Verified identity and request scope; no PIN, CAN or NFC session. Never serialized. */
data class PasskeyAuthorizationScope(val rp: String, val appIdentity: String, val origin: String,
    val challenge: String?, val clientHash: String?)

/** Boundary for authorization sources. Device delegation can implement this separately in the future. */
interface PasskeyContinuationAuthorizer {
    fun consume(token: String, scope: PasskeyAuthorizationScope, options: PasskeyGetOptions, id: String): PasskeyRecord?
    fun cancel(token: String? = null)
}

/** One in-process grant for account selection, not a general unlocked session. */
class PasskeyContinuation(private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    val lifetimeMs: Long = 60_000) : PasskeyContinuationAuthorizer {
    init { require(lifetimeMs in 1..60_000) }
    private data class Grant(val token: String, val deadline: Long, val entries: List<Pair<PasskeyAuthorizationScope, PasskeyRecord>>)
    private var grant: Grant? = null
    private fun selectionToken(active: Grant, index: Int) = PasskeyEncoding.encode(java.security.MessageDigest.getInstance("SHA-256")
        .digest("${active.token}:$index".toByteArray(Charsets.UTF_8)))
    @Synchronized fun selectionToken(token: String, index: Int): String {
        val active = requireNotNull(grant)
        require(token == active.token && index in active.entries.indices)
        return selectionToken(active, index)
    }
    @Synchronized fun issue(entries: List<Pair<PasskeyAuthorizationScope, PasskeyRecord>>): String {
        cancel()
        require(entries.size in 1..50)
        val copied = mutableListOf<Pair<PasskeyAuthorizationScope, PasskeyRecord>>()
        try {
            entries.forEach { (scope, r) ->
                require(scope.rp == r.rpId)
                copied += scope to PasskeyRecord(r.id.copyOf(), r.rpId, r.userId.copyOf(), r.username,
                    r.displayName, r.privateKey.copyOf(), r.publicKey.copyOf(), r.backupEligible, r.backedUp)
            }
            val token = UUID.randomUUID().toString()
            grant = Grant(token, now() + lifetimeMs, copied)
            return token
        } catch (failure: Throwable) { copied.forEach { it.second.destroy() }; throw failure }
    }
    @Synchronized override fun consume(token: String, scope: PasskeyAuthorizationScope, options: PasskeyGetOptions, id: String): PasskeyRecord? {
        val active = grant ?: return null
        if (now() >= active.deadline) { cancel(); return null }
        val index = active.entries.indices.firstOrNull { token == selectionToken(active, it) } ?: return null
        grant = null // Consume before checking or signing; mismatches cannot retry the grant.
        val selected = active.entries[index].takeIf { (allowed, record) ->
            options.challenge != null && scope.challenge == options.challenge &&
                allowed.rp == scope.rp && scope.rp == options.rpId && allowed.appIdentity == scope.appIdentity &&
                allowed.origin == scope.origin && allowed.clientHash == scope.clientHash &&
                (allowed.challenge == null || allowed.challenge == options.challenge) &&
                record.encodedId == id && record.rpId == options.rpId &&
                (options.allowed.isEmpty() || id in options.allowed)
        }?.second
        active.entries.forEach { if (it.second !== selected) it.second.destroy() }
        return selected
    }
    @Synchronized override fun cancel(token: String?) {
        if (token != null && token != grant?.token && grant?.let { active -> active.entries.indices.any { token == selectionToken(active, it) } } != true) return
        grant?.entries?.forEach { it.second.destroy() }; grant = null
    }
}
