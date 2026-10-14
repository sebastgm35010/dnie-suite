package pe.gob.reniec.dnie.desktop.ui

import java.nio.file.Path
import pe.gob.reniec.dnie.desktop.orchestration.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import kotlinx.coroutines.runBlocking
import pe.gob.reniec.dnie.desktop.application.DesktopSuiteSession
import pe.gob.reniec.dnie.desktop.application.DesktopInputUnavailableException
import pe.gob.reniec.dnie.desktop.model.*
import pe.gob.reniec.dnie.pdf.desktop.DesktopPdfPreview
import pe.gob.reniec.dnie.signing.link.SuiteCardUnavailableException

/** Schedules use cases off EDT. No PDF commands, APDU, TLS or signing logic in Swing views. */
class SuiteController(private val product: DesktopProductCoordinator = DesktopIntegration.coordinator()) : AutoCloseable {
    private val jobs=Executors.newSingleThreadExecutor { Thread(it,"suite-ui-jobs") }
    private val previews=Executors.newSingleThreadExecutor { Thread(it,"suite-ui-preview") }
    private val stamps=Executors.newSingleThreadExecutor { Thread(it,"suite-ui-stamp") }
    private val stampGeneration=AtomicLong()
    private val generation=AtomicLong()
    private val connections=AtomicLong()
    @Volatile private var permit: AutoCloseable?=null
    @Volatile private var session: DesktopSuiteSession?=null
    fun connectedPhone()=session?.connectedPhone.orEmpty()
    fun connectedPc()=session?.connectedPc.orEmpty()
    @Volatile private var closed=false
    private fun ui(action: ()->Unit) { SwingUtilities.invokeLater { if(!closed) action() } }
    fun connect(pc: String,phone: String,workspace: Path,identity: Path,
                pairing: (DesktopSuiteSession.PairingInfo)->Unit,done: (Boolean,String)->Unit) {
        val version=connections.incrementAndGet()
        val own=DesktopSuiteSession(); session=own
        jobs.submit {
            var acquired: AutoCloseable?=null
            try {
                acquired=product.acquirePrivateSuite()
                if(closed || version!=connections.get()) { acquired.close();return@submit }
                permit=acquired
                runBlocking { own.connect(pc,phone,workspace,identity) { info -> ui { if(version==connections.get()) pairing(info) } } }
                ui { if(version==connections.get()) done(true,"PC y Android conectados. Mantén el DNI disponible al firmar.") }
            } catch(e: Exception) { own.close();acquired?.close();if(permit===acquired)permit=null
                ui { if(version==connections.get()) done(false,if(e is ProductModeConflict) e.message ?: "Modo ocupado." else "No se pudo conectar (${e.javaClass.simpleName}). Revisa Android, IP y emparejamiento.") } }
        }
    }
    fun prepareFiles(selected: List<java.io.File>, view: javax.swing.filechooser.FileSystemView, done: (List<Path>,Int)->Unit) {
        previews.submit {
            val resolved=selected.map { runCatching { DesktopFileSelection.resolve(it,view) }.getOrNull() }
            ui { done(resolved.filterNotNull().distinct(),resolved.count { it==null }) }
        }
    }
    fun checkInputs(inputs: List<Path>, done: (Boolean)->Unit) {
        jobs.submit {
            val valid=runCatching { pe.gob.reniec.dnie.desktop.storage.DesktopWorkspace.validate(DesktopSigningRequest(inputs,Path.of("."))) }.isSuccess
            ui { done(valid) }
        }
    }
    fun fileSizes(inputs: List<Path>,done: (Map<Path,Long?>)->Unit) {
        previews.submit {
            val sizes=inputs.associateWith { runCatching { java.nio.file.Files.size(it) }.getOrNull() }
            ui { done(sizes) }
        }
    }
    fun stamp(width: Float,height: Float,name: String,options: pe.gob.reniec.dnie.pdf.PdfSigningOptions,timestamp: Long,dni: String="00000000",
        done: (java.awt.image.BufferedImage?,String?)->Unit) {
        val version=stampGeneration.incrementAndGet()
        stamps.submit {
            if(version!=stampGeneration.get() || closed) return@submit
            val rendered=runCatching { pe.gob.reniec.dnie.pdf.desktop.DesktopStampRenderer.renderPreviewImage(
                (width*2).toInt().coerceIn(1,1600),(height*2).toInt().coerceIn(1,1600),name,dni,options,width,height,timestamp) }
            SwingUtilities.invokeLater {
                if(closed || version!=stampGeneration.get()) rendered.getOrNull()?.flush()
                else done(rendered.getOrNull(),rendered.exceptionOrNull()?.message)
            }
        }
    }
    fun sign(request: DesktopSigningRequest,pin: CharArray,done: (DesktopSigningResult?,String,Boolean)->Unit) {
        jobs.submit {
            try {
                val result=runBlocking { checkNotNull(session).sign(request,pin) }
                ui { done(result, "Completos: ${result.documents.count { it.signature!=null }} / ${result.documents.size}. No se repiten firmas automáticamente.",true) }
            } catch(e: SuiteCardUnavailableException) {
                ui { done(null,"DNI no disponible; no empezó la firma. Acércalo y envía una solicitud nueva.",true) }
            } catch(e: DesktopInputUnavailableException) {
                ui { done(null,"El archivo ya no está disponible. Vuelve a seleccionarlo; la conexión se conserva.",true) }
            } catch(e: Exception) { session?.close();permit?.close();permit=null; ui { done(null,"Operación interrumpida (${e.javaClass.simpleName}). Revisa resultados antes de reconectar.",false) } }
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
    fun completedCitizen(result: DesktopSigningResult,done: (pe.gob.reniec.dnie.domain.model.CitizenData?)->Unit) {
        previews.submit { val citizen=CompletedBatchPresentation.citizen(result);ui { done(citizen) } }
    }
    fun openOutput(path: Path,reveal: Boolean,actions: SuiteDesktopActions,done: (Boolean)->Unit) {
        previews.submit { val ok=runCatching { if(reveal)actions.reveal(path) else actions.open(path) }.isSuccess;ui { done(ok) } }
    }
    fun invalidatePreview() { generation.incrementAndGet() }
    fun disconnect() { connections.incrementAndGet();session?.close(); session=null;permit?.close();permit=null }
    override fun close() { closed=true;generation.incrementAndGet();stampGeneration.incrementAndGet();disconnect();jobs.shutdownNow();previews.shutdownNow();stamps.shutdownNow() }
}
