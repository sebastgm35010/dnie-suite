package pe.gob.reniec.dnie.domain.scan

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import pe.gob.reniec.dnie.core.nfc.*
import pe.gob.reniec.dnie.domain.model.*
import pe.gob.reniec.dnie.icao.*

interface PublicCertificateReader : AutoCloseable {
    suspend fun read(progress: (String)->Unit): CertificateSnapshot
}
data class CertificateSnapshot(val certificates: Map<CertificateType,CertificateInfo>, val issues: Set<CertificateType> = emptySet(), val interrupted: Boolean = false)
data class UnifiedScanResult(val certificates: CertificateSnapshot?,val identity: IcaoDocument?,
    val certificateIssue: String?=null,val identityIssue: String?=null)

/** One physical presentation, independent sequential sessions. No Android, UI, shared SM, PIN or signing. */
class UnifiedDniScan(initialCertificate: PublicCertificateReader?=null) : AutoCloseable {
    private val ownership=Any()
    private var active: AutoCloseable?=initialCertificate
    private var cancelled=false
    private val released=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<AutoCloseable,Boolean>())
    private fun own(resource: AutoCloseable) = synchronized(ownership) {
        if(cancelled) { if(released.add(resource)) resource.close();throw CancellationException("Lectura cancelada") }
        active=resource
    }
    private fun release(resource: AutoCloseable) {
        val close=synchronized(ownership) { if(active===resource) { active=null;released.add(resource) } else false }
        if(close) resource.close()
    }
    override fun close() {
        val resource=synchronized(ownership) { cancelled=true;active?.takeIf { released.add(it) }.also { active=null } }
        resource?.close()
    }
    suspend fun read(certificates: ()->PublicCertificateReader,identity: (() -> IcaoReader)?,
                     progress: (String)->Unit={},onCertificates: (CertificateSnapshot)->Unit={},
                     onIdentityProgress: (IcaoDocument)->Unit={}): UnifiedScanResult {
        var result: CertificateSnapshot?=null; var certIssue: String?=null
        try {
            val reader=certificates();own(reader)
            try { result=reader.read(progress) } finally { release(reader) }
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            certIssue=e.message ?: "No se pudieron leer los certificados (${e.javaClass.simpleName})."
            if(isFatal(e)) return UnifiedScanResult(result,null,certIssue,"Lectura detenida por pérdida de transporte o integridad.")
        }
        result?.let(onCertificates)
        if(result?.interrupted==true) return UnifiedScanResult(result,null,
            "Certificados parciales: pérdida de transporte o integridad.","ICAO no se inició; se conservan los certificados completos.")
        coroutineContext.ensureActive()
        if(identity==null) return UnifiedScanResult(result,null,certIssue)
        progress("Abriendo una sesión nueva de identidad ICAO… Mantén el DNI inmóvil.")
        return try {
            val reader=identity();own(reader)
            val document=try { reader.read { current ->
                onIdentityProgress(current)
                val block=current.blocks.values.firstOrNull { it.status==BlockStatus.READING }
                progress(block?.let { "Leyendo ${it.file.title}… Mantén el DNI inmóvil." } ?: "Leyendo identidad ICAO…")
            } } finally { release(reader) }
            UnifiedScanResult(result,document,certIssue,document.sessionMessage)
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { UnifiedScanResult(result,null,certIssue,"No se pudo leer ICAO (${e.javaClass.simpleName}); se conservan los certificados.") }
    }
    companion object {
        fun isFatal(error: Throwable): Boolean = generateSequence(error) { it.cause }.take(16).any {
            it is IOException || it is CardDisconnectedException || it is SecureMessagingIntegrityException || it is TerminalSmIntegrityException
        }
    }
}
