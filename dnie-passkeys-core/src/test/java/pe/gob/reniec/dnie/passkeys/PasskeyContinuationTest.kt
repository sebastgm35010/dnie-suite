package pe.gob.reniec.dnie.passkeys

import org.junit.Assert.*
import org.junit.Test

class PasskeyContinuationTest {
    private fun PasskeyContinuation.issueSelection(entries: List<Pair<PasskeyAuthorizationScope, PasskeyRecord>>) = selectionToken(issue(entries), 0)
    private val scope = PasskeyAuthorizationScope("example.com", "app:signer", "https://example.com", "challenge", "hash")
    private val options = PasskeyGetOptions("example.com", emptyList(), "challenge")
    private fun record() = PasskeyRecord(ByteArray(32) { 1 }, "example.com", byteArrayOf(2), "Test", "Test", byteArrayOf(3, 4), byteArrayOf(5))
    @Test fun singleUseSurvivesOriginalCatalogCloseAndDoesNotModifyOriginal() {
        val grant = PasskeyContinuation(); val original = record()
        val token = grant.issueSelection(listOf(scope to original)); original.destroy()
        val selected = requireNotNull(grant.consume(token, scope, options, original.encodedId))
        assertArrayEquals(byteArrayOf(3, 4), selected.privateKey)
        assertNull(grant.consume(token, scope, options, original.encodedId))
        selected.destroy(); assertArrayEquals(byteArrayOf(0, 0), selected.privateKey)
    }
    @Test fun expiresAtDeadline() {
        var clock = 0L; val grant = PasskeyContinuation({ clock }, 60_000); val r = record()
        val token = grant.issueSelection(listOf(scope to r)); clock = 60_000
        assertNull(grant.consume(token, scope, options, r.encodedId)); r.destroy()
    }
    @Test fun deniesChangedCallerSiteOriginHashOrChallengeAndConsumesFailedAttempt() {
        listOf(scope.copy(appIdentity = "other:signer"), scope.copy(rp = "other.com"), scope.copy(origin = "https://other.com"),
            scope.copy(clientHash = "other"), scope.copy(challenge = "other")).forEach { changed ->
            val grant = PasskeyContinuation(); val r = record(); val token = grant.issueSelection(listOf(scope to r))
            assertNull(grant.consume(token, changed, options.copy(challenge = changed.challenge), r.encodedId))
            assertNull(grant.consume(token, scope, options, r.encodedId)); r.destroy()
        }
    }
    @Test fun deniesUnlistedCredentialAndNarrowedAllowlist() {
        listOf("unknown", record().encodedId).forEach { id ->
            val grant = PasskeyContinuation(); val r = record(); val token = grant.issueSelection(listOf(scope to r))
            assertNull(grant.consume(token, scope, options.copy(allowed = listOf("different")), id)); r.destroy()
        }
    }
    @Test fun cancellationReplacementAndOldExpiryCannotReleaseNewGrant() {
        val grant = PasskeyContinuation(); val r = record(); val first = grant.issueSelection(listOf(scope to r))
        val second = grant.issueSelection(listOf(scope to r)); grant.cancel(first)
        assertNull(grant.consume(first, scope, options, r.encodedId))
        requireNotNull(grant.consume(second, scope, options, r.encodedId)).destroy()
        val third = grant.issueSelection(listOf(scope to r)); grant.cancel(third)
        assertNull(grant.consume(third, scope, options, r.encodedId)); r.destroy()
    }
    @Test fun supportsChallengeAbsentOnlyDuringDiscovery() {
        val grant = PasskeyContinuation(); val r = record(); val token = grant.issueSelection(listOf(scope.copy(challenge = null) to r))
        requireNotNull(grant.consume(token, scope, options, r.encodedId)).destroy(); r.destroy()
    }
    @Test fun concurrentSelectionsCannotBothConsume() {
        val grant = PasskeyContinuation(); val r = record(); val token = grant.issueSelection(listOf(scope to r))
        val selected = java.util.Collections.synchronizedList(mutableListOf<PasskeyRecord>())
        val threads = List(8) { Thread { grant.consume(token, scope, options, r.encodedId)?.let(selected::add) } }
        threads.forEach(Thread::start); threads.forEach(Thread::join)
        assertEquals(1, selected.size); selected.forEach(PasskeyRecord::destroy); r.destroy()
    }
    @Test fun accountSelectionTokenCannotSelectAnotherDisplayedAccount() {
        val grant = PasskeyContinuation(); val first = record()
        val second = PasskeyRecord(ByteArray(32) { 9 }, "example.com", byteArrayOf(2), "Other", "Other", byteArrayOf(3), byteArrayOf(5))
        val group = grant.issue(listOf(scope to first, scope to second))
        val token = grant.selectionToken(group, 0)
        assertNull(grant.consume(token, scope, options, second.encodedId))
        assertNull(grant.consume(token, scope, options, first.encodedId))
        first.destroy(); second.destroy()
    }
}
