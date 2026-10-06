package pe.gob.reniec.dnie.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.domain.vault.VaultBatchImportUseCase

class VaultBatchImportUseCaseTest {
    @Test fun independentFailureDoesNotStopRemainingFiles() = runTest {
        val completed = mutableListOf<Int>(); val progress = mutableListOf<Int>()
        val result = VaultBatchImportUseCase().run(listOf(1, 2, 3), {
            if (it == 2) error("Unreadable source") else completed += it
        }, { done, total, failed -> assertEquals(3, total); progress += done })
        assertEquals(listOf(1, 3), completed); assertEquals(listOf(1, 2, 3), progress)
        assertEquals(2, result.completed); assertEquals(1, result.failed)
    }
    @Test fun cancellationStopsNextFilesAndPreservesAlreadyCommitted() = runTest {
        val completed = mutableListOf<Int>()
        try {
            VaultBatchImportUseCase().run(listOf(1, 2, 3), {
                if (it == 2) throw CancellationException() else completed += it
            }, { _, _, _ -> })
            fail("Cancellation expected")
        } catch (_: CancellationException) {}
        assertEquals(listOf(1), completed)
    }
    @Test fun maximumBatchIsCheckedBeforeFirstEncryption() = runTest {
        var called = false
        try { VaultBatchImportUseCase().run(List(1001) { it }, { called = true }, { _, _, _ -> }); fail() }
        catch (_: IllegalArgumentException) {}
        assertFalse(called)
    }
}
