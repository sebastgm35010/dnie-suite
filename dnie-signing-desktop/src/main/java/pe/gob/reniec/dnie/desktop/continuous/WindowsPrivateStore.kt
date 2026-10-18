package pe.gob.reniec.dnie.desktop.continuous

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** DPAPI CurrentUser. No secrets in command arguments/environment; payload uses stdin.
 * The ephemeral PowerShell helper never receives command/source from a remote peer.
 */
class WindowsPrivateStore(private val timeoutMillis: Long = 15_000) {
    init { require(timeoutMillis in 50..60_000) }
    fun protect(bytes: ByteArray) = transform(bytes, "Protect")
    fun unprotect(bytes: ByteArray) = transform(bytes, "Unprotect")
    private fun transform(bytes: ByteArray, operation: String): ByteArray {
        check(System.getProperty("os.name").startsWith("Windows"))
        val script = """
            Add-Type -AssemblyName System.Security
            try {
              ${'$'}raw=[Convert]::FromBase64String([Console]::In.ReadToEnd())
              ${'$'}out=[Security.Cryptography.ProtectedData]::$operation(${'$'}raw,${'$'}null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
              [Console]::Out.Write([Convert]::ToBase64String(${'$'}out))
            } catch { exit 1 } finally { if (${'$'}raw) { [Array]::Clear(${'$'}raw,0,${'$'}raw.Length) } }
        """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val input = Base64.getEncoder().encode(bytes)
        val output = runBoundedProcess(
            listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded),
            input,
            timeoutMillis
        )
        try {
            return Base64.getDecoder().decode(output)
        } finally {
            output.fill(0)
        }
    }
    companion object {
        fun runBoundedProcess(command: List<String>, stdinBytes: ByteArray, timeoutMillis: Long = 15_000): ByteArray {
            val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            fun remainingNanos(): Long {
                val left = deadlineNanos - System.nanoTime()
                check(left > 0L) { "El asistente DPAPI superó el tiempo límite" }
                return left
            }
            val process = ProcessBuilder(command).start()
            val writer = CompletableFuture.runAsync {
                try {
                    process.outputStream.use { it.write(stdinBytes) }
                } finally {
                    stdinBytes.fill(0)
                }
            }
            val reader = CompletableFuture.supplyAsync {
                process.inputStream.use { it.readNBytes(131073) }
            }
            var output = byteArrayOf()
            try {
                try {
                    writer.get(remainingNanos(), TimeUnit.NANOSECONDS)
                } catch (e: Exception) {
                    throw IllegalStateException("El asistente DPAPI bloqueó la entrada o superó el tiempo límite", e)
                }
                check(process.waitFor(remainingNanos(), TimeUnit.NANOSECONDS)) { "El asistente DPAPI superó el tiempo límite" }
                output = try {
                    reader.get(remainingNanos(), TimeUnit.NANOSECONDS)
                } catch (e: Exception) {
                    throw IllegalStateException("El asistente DPAPI bloqueó la salida o superó el tiempo límite", e)
                }
                check(process.exitValue() == 0 && output.size <= 131072)
                return output
            } catch (failure: Throwable) {
                output.fill(0)
                throw failure
            } finally {
                stdinBytes.fill(0)
                process.destroyForcibly()
                if (reader.isDone) {
                    runCatching {
                        val extra = reader.getNow(byteArrayOf())
                        if (extra !== output) extra.fill(0)
                    }
                } else {
                    reader.thenAccept { it.fill(0) }
                }
            }
        }
        fun restrict(path: Path) {
            runCatching {
                val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
                if (acl != null) acl.acl = listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                    .setPrincipal(Files.getOwner(path)).setPermissions(EnumSet.allOf(AclEntryPermission::class.java)).build())
                else Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
            }
        }
    }
}
