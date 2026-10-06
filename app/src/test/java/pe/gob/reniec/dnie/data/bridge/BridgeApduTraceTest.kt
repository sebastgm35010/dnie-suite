package pe.gob.reniec.dnie.data.bridge

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import pe.gob.reniec.dnie.bridge.BridgeCard
import java.nio.file.Files

class BridgeApduTraceTest {
    @Test fun integrityDiagnosticIsExportedWithoutExceptionMessagesOrKeys() {
        val file = Files.createTempFile("bridge-sm-diagnostic", ".jsonl").toFile()
        val card = object : BridgeCard {
            override val atr = byteArrayOf(0x3b,0)
            override fun transmit(command: ByteArray): ByteArray = throw IllegalStateException("private-message",
                pe.gob.reniec.dnie.core.nfc.TerminalSmIntegrityException("context:1",119L,listOf(1L)))
            override fun close() {}
        }
        try {
            BridgeApduTrace(card,file).use { trace ->
                assertThrows(IllegalStateException::class.java) { trace.transmit(byteArrayOf(0x0c,0xa4.toByte(),4,0x0c)) }
            }
            val text = file.readText()
            assertFalse(text.contains("private-message"))
            val info = file.readLines().map(::JSONObject).first { it.getString("event") == "transport_failed" }.getJSONObject("smDiagnostic")
            assertEquals("pc_command_mac",info.getString("stage"))
            assertEquals(119L,info.getLong("expectedSsc"))
            assertEquals(1L,info.getJSONArray("matchingSsc").getLong(0))
            assertEquals("context:1",info.getString("context"))
        } finally { file.delete() }
    }
    @Test fun windowsPaceTraceIsDistinguishedFromLogicalAndroidPace() {
        val file = Files.createTempFile("bridge-raw", ".jsonl").toFile()
        try {
            BridgeApduTrace(Card(), file, "pace_windows_raw").use {
                it.transmit(byteArrayOf(0x0c, 0xa4.toByte(), 2, 4, 0))
            }
            assertTrue(file.readLines().all { JSONObject(it).getString("profile") == "pace_windows_raw" })
        } finally { file.delete() }
    }
    @Test fun openingFailureIsExportableWithoutAnyCardOrSecretMessage() {
        val file = Files.createTempFile("bridge-opening", ".jsonl").toFile()
        try {
            BridgeApduTrace.openingFailure(file, IllegalStateException("secret-value", java.io.IOException("secret-value")))
            val text = file.readText()
            assertFalse(text.contains("secret-value"))
            val row = JSONObject(text.trim())
            assertEquals("opening_failed", row.getString("event"))
            assertEquals("IllegalStateException > IOException", row.getString("failureType"))
        } finally { file.delete() }
    }
    private class Card : BridgeCard {
        override val atr = byteArrayOf(0x3b, 0)
        var command = byteArrayOf()
        override fun transmit(command: ByteArray): ByteArray {
            this.command = command.copyOf(); return byteArrayOf(1, 2, 0x90.toByte(), 0)
        }
        override fun close() {}
    }
    @Test fun pinIsForwardedIntactButNeverStoredAndResponseRemainsExact() {
        val file = Files.createTempFile("bridge-trace", ".jsonl").toFile()
        try {
            val card = Card()
            BridgeApduTrace(card, file).use { trace ->
                val pin = byteArrayOf(0, 0x20, 0, 1, 2, 0xf1.toByte(), 0xf2.toByte())
                assertArrayEquals(byteArrayOf(1, 2, 0x90.toByte(), 0), trace.transmit(pin))
                assertArrayEquals(pin, card.command)
            }
            val text = file.readText()
            assertFalse(text.contains("F1F2"))
            val rows = file.readLines().map(::JSONObject)
            val command = rows.first { it.getString("event") == "command" }
            assertEquals("00200001", command.getString("hex")); assertTrue(command.getBoolean("redacted"))
            assertEquals("01029000", rows.first { it.getString("event") == "response" }.getString("hex"))
        } finally { file.delete() }
    }
    @Test fun recordingFailureDoesNotBlockOrChangeCommunication() {
        val directory = Files.createTempDirectory("bridge-trace-failure").toFile()
        try {
            val card = Card()
            BridgeApduTrace(card, directory).use { trace ->
                val command = byteArrayOf(0x80.toByte(), 0xff.toByte(), 1, 2)
                assertArrayEquals(byteArrayOf(1, 2, 0x90.toByte(), 0), trace.transmit(command))
                assertArrayEquals(command, card.command)
            }
        } finally { directory.delete() }
    }
}
