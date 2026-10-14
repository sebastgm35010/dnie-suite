package pe.gob.reniec.dnie.desktop.orchestration

import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BridgeReplyTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun actualWindowsDescendantDoesNotHoldAdapterCallOpen() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val powershell=java.nio.file.Path.of(System.getenv("SystemRoot"),"System32/WindowsPowerShell/v1.0/powershell.exe")
        val helper=folder.newFile("adapter.cmd").toPath()
        Files.writeString(helper,"@echo off\r\nstart \"\" /b \"$powershell\" -NoProfile -NonInteractive -Command \"Start-Sleep -Seconds 5\"\r\necho DNIE1\tOPENED\r\nexit /b 0\r\n")
        val started=System.nanoTime()
        WindowsBridgeGateway(helper).openManager()
        assertTrue("Reply must finish before descendant closes stdout",System.nanoTime()-started<3_000_000_000L)
    }
    @Test fun exitedAdapterDoesNotWaitForDescendantToCloseStdout() {
        val line="DNIE1\tOPENED\r\n".toByteArray()
        val inherited=object: ByteArrayInputStream(line) {
            override fun read(b: ByteArray,off: Int,len: Int): Int {
                check(len<=available()) { "Must not wait for EOF owned by a still-running manager" }
                return super.read(b,off,len)
            }
        }
        assertEquals("DNIE1\tOPENED",WindowsBridgeGateway.readExitedReply(inherited))
    }
    @Test fun missingPartialOversizedAndMultipleRepliesAreRejected() {
        for(value in listOf("","DNIE1\tOPENED","x".repeat(257)+"\n","DNIE1\tOPENED\nextra\n"))
            assertThrows(IllegalArgumentException::class.java) { WindowsBridgeGateway.readExitedReply(ByteArrayInputStream(value.toByteArray())) }
    }
    @Test fun statusReplyRetainsStrictDecode() {
        val line=WindowsBridgeGateway.readExitedReply(ByteArrayInputStream("DNIE1\tAVAILABLE\tSTOPPED\t0\n".toByteArray()))
        assertEquals(BridgeHostState.STOPPED,WindowsBridgeGateway.decode(line).host)
    }
}
