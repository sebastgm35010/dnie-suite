package pe.gob.reniec.dnie.passkeys

import org.junit.Assert.*
import org.junit.Test

class PasskeyDelegationCacheTest {
    private val identity = PasskeyDelegationCache.Identity("example.com", "app", "signer")
    @Test fun positiveApprovalExpiresWithoutSlidingOnReads() {
        var clock = 0L; val cache = PasskeyDelegationCache { clock }
        assertFalse(cache.contains(identity)); cache.approve(identity)
        clock = 59_999; assertTrue(cache.contains(identity))
        clock = 60_000; assertFalse(cache.contains(identity))
    }
    @Test fun requiresExactRpPackageAndCurrentSigner() {
        val cache = PasskeyDelegationCache(); cache.approve(identity)
        assertFalse(cache.contains(identity.copy(rp = "other.com")))
        assertFalse(cache.contains(identity.copy(packageName = "other")))
        assertFalse(cache.contains(identity.copy(fingerprint = "rotated")))
        cache.clear(); assertFalse(cache.contains(identity))
    }
    @Test fun boundsMemoryAndEvictsOldestApproval() {
        val cache = PasskeyDelegationCache()
        repeat(33) { cache.approve(identity.copy(packageName = "app$it")) }
        assertFalse(cache.contains(identity.copy(packageName = "app0")))
        assertTrue(cache.contains(identity.copy(packageName = "app32")))
    }
}
