package pe.gob.reniec.dnie.desktop.orchestration

import java.nio.file.Files
import java.nio.file.Path
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Optional native adapter. Fixed verbs and bounded output; no shell, credentials or APDU. */
class WindowsBridgeGateway(private val helper: Path?) : BridgeGateway {
    override fun status(): BridgeSnapshot {
        if (helper == null || !Files.isRegularFile(helper)) return BridgeSnapshot(BridgeAvailability.OPTIONAL_MODULE_ABSENT)
        return runCatching { decode(call("status", 10)) }.getOrElse { BridgeSnapshot(BridgeAvailability.UNAVAILABLE) }
    }
    override fun openManager() { check(call("open-manager", 10) == "DNIE1\tOPENED") }
    override fun stop(): BridgeSnapshot = decode(call("stop", 40))
    private fun call(command: String, seconds: Long): String {
        val executable=checkNotNull(helper)
        require(command in setOf("status", "stop", "open-manager") && Files.isRegularFile(executable))
        val process=ProcessBuilder(executable.toAbsolutePath().toString(),command)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try {
            // Native helper emits at most one small line; discard excess without logging it.
            check(process.waitFor(seconds,TimeUnit.SECONDS)) { "El adaptador del puente no respondió" }
            check(process.exitValue()==0) { "No se pudo operar el puente" }
            return readExitedReply(process.inputStream)
        } finally { if (process.isAlive) process.destroyForcibly();process.inputStream.close();process.outputStream.close() }
    }
    companion object {
        internal fun readExitedReply(input: InputStream): String {
            // The exited adapter has flushed its one-line response. A manager
            // it launched may still own the inherited stdout write handle, so
            // readNBytes(257)/read-to-EOF can hang for the manager's lifetime.
            // Read only bytes ready now, never wait for a descendant to close.
            val ready=input.available();require(ready in 1..256)
            val bytes=input.readNBytes(ready)
            require(bytes.size==ready && bytes.last()==10.toByte() && bytes.indexOf(10.toByte())==bytes.lastIndex)
            return String(bytes,Charsets.UTF_8).trimEnd('\r','\n')
        }
        internal fun decode(line: String): BridgeSnapshot {
            val fields=line.split('\t');require(fields.size==4 && fields[0]=="DNIE1")
            val available=BridgeAvailability.valueOf(fields[1]);val state=BridgeHostState.valueOf(fields[2])
            require(fields[3] in setOf("0","1"))
            return BridgeSnapshot(available,state,fields[3]=="1")
        }
    }
}
