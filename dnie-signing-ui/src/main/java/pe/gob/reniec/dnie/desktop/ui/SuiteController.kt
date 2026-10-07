package pe.gob.reniec.dnie.desktop.ui

import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import kotlinx.coroutines.runBlocking
import pe.gob.reniec.dnie.desktop.application.DesktopSuiteSession
import pe.gob.reniec.dnie.desktop.model.*
import pe.gob.reniec.dnie.pdf.desktop.DesktopPdfPreview
import pe.gob.reniec.dnie.signing.link.SuiteCardUnavailableException

/** Schedules use cases off EDT. No PDF commands, APDU, TLS or signing logic in Swing views. */
class SuiteController : AutoCloseable {
    private val jobs=Executors.newSingleThreadExecutor { Thread(it,"suite-ui-jobs") }
    private val previews=Executors.newSingleThreadExecutor { Thread(it,"suite-ui-preview") }
    private val generation=AtomicLong()
    private val connections=AtomicLong()
    @Volatile private var session: DesktopSuiteSession?=null
    @Volatile private var closed=false
    private fun ui(action: ()->Unit) { SwingUtilities.invokeLater { if(!closed) action() } }
    fun connect(pc: String,phone: String,workspace: Path,identity: Path,
                pairing: (DesktopSuiteSession.PairingInfo)->Unit,done: (Boolean,String)->Unit) {
        val version=connections.incrementAndGet()
        val own=DesktopSuiteSession(); session=own
        jobs.submit {
            try {
                runBlocking { own.connect(pc,phone,workspace,identity) { info -> ui { if(version==connections.get()) pairing(info) } } }
                ui { if(version==connections.get()) done(true,"PC y Android conectados. Mantén el DNI disponible al firmar.") }
            } catch(e: Exception) { own.close(); ui { if(version==connections.get()) done(false,"No se pudo conectar (${e.javaClass.simpleName}). Revisa Android, IP y emparejamiento.") } }
        }
    }
    fun sign(request: DesktopSigningRequest,pin: CharArray,done: (DesktopSigningResult?,String)->Unit) {
        jobs.submit {
            try {
                val result=runBlocking { checkNotNull(session).sign(request,pin) }
                ui { done(result, "Completos: ${result.documents.count { it.signature!=null }} / ${result.documents.size}. No se repiten firmas automáticamente.") }
            } catch(e: SuiteCardUnavailableException) {
                ui { done(null,"DNI no disponible; no empezó la firma. Acércalo y envía una solicitud nueva.") }
            } catch(e: Exception) { session?.close(); ui { done(null,"Operación interrumpida (${e.javaClass.simpleName}). Revisa resultados antes de reconectar.") } }
            finally { pin.fill('\u0000') }
        }
    }
    fun preview(file: Path,page: Int,done: (DesktopPdfPreview.Page?,String)->Unit) {
        val version=generation.incrementAndGet()
        previews.submit {
            try { val result=DesktopPdfPreview().render(file,page); ui { if(version==generation.get()) done(result,"") } }
            catch(_: Exception) { ui { if(version==generation.get()) done(null,"No se puede previsualizar esta página; comprueba página, formato o cifrado.") } }
        }
    }
    fun invalidatePreview() { generation.incrementAndGet() }
    fun disconnect() { connections.incrementAndGet();session?.close(); session=null }
    override fun close() { closed=true;generation.incrementAndGet();disconnect();jobs.shutdownNow();previews.shutdownNow() }
}
