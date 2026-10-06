package pe.gob.reniec.dnie.data.diagnostics

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.diagnostics.*
import org.json.JSONObject
import java.io.OutputStream
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

data class DiagnosticRow(val id: Long, val time: Long, val category: String, val name: String, val details: String)
data class DiagnosticPage(val rows: List<DiagnosticRow>, val total: Long, val error: String?)

/** Append-only SQLite, no expiry/rotation. Writes run off UI; only explicit clear removes events. */
class DiagnosticStore(context: Context, databaseName: String = "temporary-diagnostics.db") : DiagnosticSink {
    private val lock = Any()
    private val epoch = AtomicLong()
    private val dropped = AtomicLong()
    @Volatile private var problem: String? = null
    private val helper = object : SQLiteOpenHelper(context.applicationContext, databaseName, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, category TEXT NOT NULL, name TEXT NOT NULL, details TEXT NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Unsupported diagnostics version") }
    }
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(8192),
        ThreadFactory { task -> Thread(task, "dnie-diagnostic-writer").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    init { helper.setWriteAheadLoggingEnabled(true) }

    override fun append(event: DiagnosticEvent) {
        val generation = epoch.get()
        try { worker.execute {
            synchronized(lock) {
                if (generation != epoch.get()) return@synchronized
                try {
                    val gap = dropped.getAndSet(0)
                    if (gap > 0) write(DiagnosticEvent(System.currentTimeMillis(), "diagnostics", "queue_overflow", mapOf("dropped" to gap.toString())))
                    write(event); problem = null
                } catch (failure: Exception) { problem = "No se pudo persistir el diagnóstico (${failure.javaClass.simpleName})." }
            }
        } } catch (_: RejectedExecutionException) { dropped.incrementAndGet(); problem = "Cola de diagnóstico saturada; se registrará la cantidad de eventos perdidos." }
    }
    private fun write(event: DiagnosticEvent) {
        helper.writableDatabase.insertOrThrow("events", null, ContentValues().apply {
            put("time", event.time); put("category", event.category); put("name", event.name)
            put("details", JSONObject(event.fields).toString())
        })
    }
    fun flush(timeoutMs: Long = 2000): Boolean = try { worker.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS); true } catch (_: Exception) { false }
    suspend fun page(before: Long? = null, category: String? = null): DiagnosticPage = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val where = mutableListOf<String>(); val args = mutableListOf<String>()
            if (before != null) { where += "id < ?"; args += before.toString() }
            if (category != null) { where += "category = ?"; args += category }
            val db = helper.readableDatabase
            val rows = db.query("events", null, where.joinToString(" AND ").ifEmpty { null }, args.toTypedArray(), null, null, "id DESC", "300").use { cursor ->
                buildList { while (cursor.moveToNext()) add(DiagnosticRow(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3), cursor.getString(4))) }
            }
            val total = db.rawQuery("SELECT COUNT(*) FROM events", null).use { it.moveToFirst(); it.getLong(0) }
            DiagnosticPage(rows, total, problem)
        }
    }
    suspend fun clear() = withContext(Dispatchers.IO) { synchronized(lock) {
        epoch.incrementAndGet(); dropped.set(0); helper.writableDatabase.delete("events", null, null)
        helper.writableDatabase.execSQL("VACUUM"); problem = null
    } }
    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        check(flush(10000)) { "Diagnostics writer did not finish" }
        val until = synchronized(lock) { helper.readableDatabase.rawQuery("SELECT COALESCE(MAX(id),0) FROM events", null).use { it.moveToFirst(); it.getLong(0) } }
        // Cursor is consumed as a stream, rather than loading the entire history into RAM.
        helper.readableDatabase.rawQuery("SELECT id,time,category,name,details FROM events WHERE id <= ? ORDER BY id", arrayOf(until.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val line = JSONObject().apply { put("id", cursor.getLong(0)); put("time", cursor.getLong(1)); put("category", cursor.getString(2));
                    put("event", cursor.getString(3)); put("fields", JSONObject(cursor.getString(4))) }.toString() + "\n"
                output.write(line.toByteArray(Charsets.UTF_8))
            }
        }
    }
    suspend fun close() = withContext(Dispatchers.IO) { flush(); worker.shutdown(); worker.awaitTermination(3, TimeUnit.SECONDS); synchronized(lock) { helper.close() } }
}
