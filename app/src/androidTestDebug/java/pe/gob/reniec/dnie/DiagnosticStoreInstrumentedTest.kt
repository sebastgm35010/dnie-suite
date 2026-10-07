package pe.gob.reniec.dnie

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.data.diagnostics.DiagnosticStore
import pe.gob.reniec.dnie.diagnostics.DiagnosticEvent
import java.io.ByteArrayOutputStream
import java.util.UUID

class DiagnosticStoreInstrumentedTest {
    @Test fun persistsAcrossRepositoryRestartAndDeletesOnlyOnExplicitClear() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "diagnostic-test-${UUID.randomUUID()}.db"
        var store = DiagnosticStore(context, name)
        try {
            repeat(350) { store.append(DiagnosticEvent(it.toLong(), "test", "event", mapOf("count" to it.toString()))) }
            assertTrue(store.flush(10000)); assertEquals(350L, store.page().total)
            assertEquals(300, store.page().rows.size)
            store.close(); store = DiagnosticStore(context, name)
            assertEquals(350L, store.page().total)
            val firstPage = store.page(); assertEquals(50, store.page(firstPage.rows.last().id).rows.size)
            val output = ByteArrayOutputStream(); store.export(output)
            assertEquals(350, output.toString("UTF-8").lineSequence().filter { it.isNotEmpty() }.count())
            repeat(100) { store.append(DiagnosticEvent(1, "test", "before_clear", emptyMap())) }
            store.clear(); store.flush(10000)
            assertEquals(0L, store.page().total)
        } finally { store.close(); context.deleteDatabase(name) }
    }
}
