package pe.gob.reniec.dnie

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.data.diagnostics.DiagnosticStore
import pe.gob.reniec.dnie.diagnostics.DiagnosticEvent
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DiagnosticsRuntimeInstrumentedTest {
    @Test fun concurrentBurstIsDurableOrderedAndClearDoesNotResurrectOldEvents() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "diagnostic-test-${UUID.randomUUID()}.db"
        val store = DiagnosticStore(context, name)
        try {
            val writers = Executors.newFixedThreadPool(4)
            repeat(4) { writer -> writers.execute {
                repeat(250) { store.append(DiagnosticEvent(1, "fixture", "sample", mapOf("count" to "$writer"))) }
            } }
            writers.shutdown(); assertTrue(writers.awaitTermination(20, TimeUnit.SECONDS))
            assertTrue(store.flush(10000))
            val bytes = ByteArrayOutputStream()
            store.export(bytes)
            val rows = bytes.toString("UTF-8").lineSequence().filter { it.isNotBlank() }.map { JSONObject(it) }.toList()
            assertEquals(1000, rows.size)
            assertTrue(rows.zipWithNext().all { (a, b) -> a.getLong("id") < b.getLong("id") })
            assertTrue(rows.all { it.getJSONObject("fields").has("monotonicNs") })
            repeat(100) { store.append(DiagnosticEvent(2, "fixture", "old", emptyMap())) }
            store.clear()
            store.append(DiagnosticEvent(3, "fixture", "after_clear", emptyMap()))
            assertTrue(store.flush(10000))
            val current = store.page()
            assertEquals(1L, current.total); assertEquals("after_clear", current.rows.single().name)
        } finally { store.close(); context.deleteDatabase(name) }
    }

    @Test fun directExportHasCompletionAndProviderPermission() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = Uri.parse("content://${context.packageName}.diagnostic-export/events")
        val provider = requireNotNull(context.packageManager.resolveContentProvider(uri.authority!!, 0))
        assertEquals("android.permission.DUMP", provider.readPermission)
        assertTrue(provider.exported)
        val lines = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readLines() }
        assertTrue(lines.isNotEmpty())
        assertEquals("export_complete", JSONObject(lines.last()).getString("type"))
        var rejected = false
        try { context.contentResolver.openOutputStream(uri)?.close() } catch (_: Exception) { rejected = true }
        assertTrue("Export cannot accept writes", rejected)
    }
}
