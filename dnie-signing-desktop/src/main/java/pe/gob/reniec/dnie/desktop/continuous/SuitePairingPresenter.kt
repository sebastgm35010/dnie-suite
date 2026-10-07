package pe.gob.reniec.dnie.desktop.continuous

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Optional presentation, separate from pairing/transport. Uses bundled script and installed QRCoder. */
object SuitePairingPresenter {
    fun openQr(request: Path): Boolean = runCatching {
        val jar = Path.of(SuitePairingPresenter::class.java.protectionDomain.codeSource.location.toURI())
        val script = jar.parent.parent.resolve("tools/make-pairing-qr.ps1")
        if (!Files.isRegularFile(script)) return false
        val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", script.toString(), "-PairingFile", request.toString())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try { process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0 }
        finally { if (process.isAlive) process.destroyForcibly() }
    }.getOrDefault(false)
}
