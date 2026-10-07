package pe.gob.reniec.dnie.desktop.continuous

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.*
import java.util.*

/** DPAPI CurrentUser. No secrets in command arguments/environment; payload uses stdin.
 * The ephemeral PowerShell helper never receives command/source from a remote peer.
 */
class WindowsPrivateStore {
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
        val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded).start()
        process.outputStream.use { it.write(Base64.getEncoder().encode(bytes)) }
        val output = process.inputStream.use { it.readNBytes(131073) }
        try {
            check(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0 && output.size <= 131072)
            return Base64.getDecoder().decode(output)
        } finally { output.fill(0); process.destroyForcibly() }
    }
    companion object {
        fun restrict(path: Path) {
            val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
            if (acl != null) acl.acl = listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                .setPrincipal(Files.getOwner(path)).setPermissions(EnumSet.allOf(AclEntryPermission::class.java)).build())
            else Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        }
    }
}
