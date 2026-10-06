package pe.gob.reniec.dnie.domain.signing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ImportSigningDocumentsTest {
    private fun doc(name: String, size: Long = 1, pdf: Boolean = true) = SigningDocument(
        id = name, name = name, file = File(name), size = size,
        pages = if (pdf) listOf(PdfPageSize(100f, 100f)) else emptyList())

    @Test fun pdfReplacementIgnoresOldWorkspaceLimitsAndRejectsNonPdf() = runTest {
        val removed = mutableListOf<String>()
        val old = List(50) { doc("old$it", 50) }
        val useCase = ImportSigningDocuments(maxDocuments = 1, maxBytes = 4)
        val result = useCase.run(listOf(doc("new", 4)), old, true, { it }, { removed += it.id })
        assertEquals(listOf("new"), result.documents.map { it.id })
        assertTrue(result.errors.isEmpty()); assertTrue(removed.isEmpty())
        val rejected = useCase.run(listOf(doc("audio", pdf = false)), old, true, { it }, { removed += it.id })
        assertTrue(rejected.documents.isEmpty()); assertEquals(listOf("audio"), removed)
        assertEquals(1, rejected.errors.size)
    }

    @Test fun batchLimitsKeepAcceptedFilesAndDisposeOnlyRejectedInput() = runTest {
        val removed = mutableListOf<String>()
        val result = ImportSigningDocuments(maxBytes = 4).run(listOf(doc("a", 2), doc("b", 3), doc("c")),
            emptyList(), false, { it }, { removed += it.id })
        assertEquals(listOf("a"), result.documents.map { it.id })
        assertEquals(listOf("b"), removed)
        assertEquals(1, result.errors.size)
        var calls = 0
        val full = ImportSigningDocuments(maxDocuments = 1).run(listOf(doc("new")), listOf(doc("old")),
            false, { calls++; it }, {})
        assertEquals(0, calls); assertTrue(full.documents.isEmpty())
    }

    @Test fun cancellationDisposesAllOwnedInputsWithoutRetryingImport() = runTest {
        val removed = mutableListOf<String>()
        var calls = 0
        try {
            coroutineScope {
                ImportSigningDocuments().run(listOf(doc("a"), doc("b")), emptyList(), false,
                    { calls++; if (calls == 2) cancel(); it }, { removed += it.id })
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(2, calls); assertEquals(listOf("a", "b"), removed)
    }

    @Test fun oneReadFailureDoesNotAbortOtherInputsAndReportsItOnce() = runTest {
        var failures = 0
        val result = ImportSigningDocuments().run(listOf("bad", "good"), emptyList(), false,
            { if (it == "bad") error("Unreadable"); doc(it) }, {}, { failures++ })
        assertEquals(listOf("good"), result.documents.map { it.id })
        assertEquals(1, failures); assertEquals(listOf("Unreadable"), result.errors)
    }
}
