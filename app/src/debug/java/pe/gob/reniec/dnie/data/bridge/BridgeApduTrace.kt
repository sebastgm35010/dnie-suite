package pe.gob.reniec.dnie.data.bridge

import org.json.JSONObject
import pe.gob.reniec.dnie.bridge.BridgeCard
import java.io.File
import java.util.UUID

/** Private, opt-in diagnostic transcript. Recording failures never alter card communication. */
class BridgeApduTrace(private val card: BridgeCard, private val file: File, private val profile: String = "pace_android_logical") : BridgeCard {
    companion object {
        /** Opening can fail before any card exists. Persist types only, never exception messages. */
        fun openingFailure(file: File, failure: Throwable, profile: String = "pace_android_logical") {
            try {
                if (file.length() >= 50L * 1024 * 1024) return
                val types = generateSequence(failure) { it.cause }.take(4)
                    .joinToString(" > ") { it.javaClass.simpleName }
                val row = JSONObject().put("time", System.currentTimeMillis())
                    .put("session", UUID.randomUUID().toString()).put("sequence", 0)
                    .put("profile", profile).put("event", "opening_failed")
                    .put("failureType", types)
                file.appendText(row.toString() + "\n", Charsets.UTF_8)
            } catch (_: Exception) { /* Diagnostics must never replace the original failure. */ }
        }
    }
    private val session = UUID.randomUUID().toString()
    private var sequence = 0
    private var disabled = false
    override val atr: ByteArray get() = card.atr
    init { record("session_started") }
    private fun hex(bytes: ByteArray): String {
        val digits = "0123456789ABCDEF"
        val chars = CharArray(bytes.size * 2)
        bytes.forEachIndexed { index, byte ->
            val value = byte.toInt() and 255
            chars[index * 2] = digits[value ushr 4]; chars[index * 2 + 1] = digits[value and 15]
        }
        return String(chars)
    }
    private fun record(event: String, bytes: ByteArray? = null, redacted: Boolean = false, failureType: String? = null,
        integrity: pe.gob.reniec.dnie.core.nfc.TerminalSmIntegrityException? = null) {
        if (disabled) return
        try {
            if (file.length() >= 50L * 1024 * 1024) {
                disabled = true
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("bridge", "apdu_trace_stopped", "state" to "size_limit")
                return
            }
            val row = JSONObject().put("time", System.currentTimeMillis()).put("session", session)
                .put("sequence", sequence).put("event", event).put("profile", profile)
            if (failureType != null) row.put("failureType", failureType)
            if (integrity != null) row.put("smDiagnostic",JSONObject()
                .put("stage","pc_command_mac").put("context",integrity.contextId)
                .put("expectedSsc",integrity.expectedSsc ?: JSONObject.NULL)
                .put("matchingSsc",org.json.JSONArray(integrity.matchingDiagnosticSsc)))
            if (bytes != null) row.put("bytes", bytes.size).put("hex", if (redacted) hex(bytes.take(4).toByteArray()) else hex(bytes))
                .put("redacted", redacted)
            file.appendText(row.toString() + "\n", Charsets.UTF_8)
        } catch (_: Exception) {
            disabled = true
            pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("bridge", "apdu_trace_stopped", "state" to "write_failed")
        }
    }
    override fun transmit(command: ByteArray): ByteArray {
        sequence++
        val pin = command.size >= 2 && (command[1].toInt() and 255) in setOf(0x20, 0x24, 0x26, 0x28, 0x2C)
        record("command", command, pin)
        return try { card.transmit(command).also { record("response", it) } }
        catch (failure: Exception) {
            record("transport_failed", failureType = failure.javaClass.simpleName,
                integrity = generateSequence<Throwable>(failure) { it.cause }.filterIsInstance<pe.gob.reniec.dnie.core.nfc.TerminalSmIntegrityException>().firstOrNull())
            throw failure
        }
    }
    override fun reset(): ByteArray { record("reset_requested"); return card.reset().also { record("reset_completed") } }
    override fun powerOff() { record("power_off_requested"); card.powerOff(); record("power_off_completed") }
    override fun checkPresence() = card.checkPresence()
    override fun close() { try { card.close() } finally { record("session_closed") } }
}
