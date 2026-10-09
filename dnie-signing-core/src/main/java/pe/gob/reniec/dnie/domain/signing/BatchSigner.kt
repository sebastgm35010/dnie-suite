package pe.gob.reniec.dnie.domain.signing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import pe.gob.reniec.dnie.core.nfc.CardProtocolException
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.crypto.DetachedFileSigner
import pe.gob.reniec.dnie.crypto.CardSignatureVerifier
import pe.gob.reniec.dnie.crypto.X509CertificateParser
import pe.gob.reniec.dnie.pdf.PadesSignerEngineInterface
import pe.gob.reniec.dnie.diagnostics.DiagnosticLog
import java.io.File
import kotlin.coroutines.coroutineContext

/** One approval and PIN entry, sequential signatures in the same physical/PACE session. */
class BatchSigner(private val pdfEngine: PadesSignerEngineInterface) {
    suspend fun sign(documents: List<SigningDocument>, transceiver: DnieTransceiverInterface, pin: String,
                     outputDirectory: File, mark: HandwrittenMark? = null,
                     onProgress: (Int, String, List<SignedArtifact>, ByteArray?) -> Unit = { _, _, _, _ -> }): BatchSignResult {
        val results = mutableListOf<SignedArtifact>()
        val approved = documents.toList()
        val session = BatchCardSession(transceiver, pin)
        var cert: ByteArray? = null
        try {
            require(approved.isNotEmpty()) { "No hay archivos pendientes" }
            require(approved.map { it.id }.distinct().size == approved.size) { "El lote contiene identificadores duplicados" }
            coroutineContext.ensureActive()
            DiagnosticLog.event("signer", "batch_start", "count" to approved.size)
            cert = session.open()
            val certificate = X509CertificateParser.toX509Certificate(cert)
            val citizen = X509CertificateParser.extractCitizenData(certificate)
            if (approved.any { it.isPdf && it.options.visible && it.options.appearance.requiresHandwritten }) {
                require(mark != null) { "Lea la firma manuscrita en Identidad" }
                val number = mark.personalNumber?.replace(Regex("[^0-9]"), "")?.takeIf { it.isNotBlank() }
                require(number == null || number == citizen.dniNumber) { "La imagen manuscrita y el certificado pertenecen a titulares diferentes" }
            }
            for ((index, document) in approved.withIndex()) {
                coroutineContext.ensureActive()
                onProgress(index, document.name, results.toList(), cert)
                val folder = File(outputDirectory, document.id).canonicalFile
                require(folder.parentFile == outputDirectory.canonicalFile) { "Identificador de documento inválido" }
                val outputName = if (document.isPdf) document.name.removeSuffix(".pdf").removeSuffix(".PDF").take(140) + "_firmado.pdf" else document.name.take(150) + ".p7s"
                val output = File(folder, SigningFileNames.safeName(outputName))
                var staging: File? = null
                var cardError: Throwable? = null
                try {
                    check(folder.isDirectory || folder.mkdirs()) { "No se pudo crear la carpeta de resultados" }
                    check(!output.exists()) { "El resultado ya existe; consérvelo antes de repetir la firma" }
                    val partial = File.createTempFile("signing-", ".part", folder)
                    staging = partial
                    val currentContext = coroutineContext
                    val signer: suspend (ByteArray) -> Result<ByteArray> = { hash ->
                        currentContext.ensureActive()
                        try {
                            val raw = session.sign(hash).getOrThrow()
                            Result.success(CardSignatureVerifier.verify(certificate, hash, raw))
                        } catch (e: Exception) { cardError = e; Result.failure(e) }
                    }
                    val time: Long
                    val size: Int
                    if (document.isPdf) {
                        val config = document.options.copy(appearance = document.options.appearance.copy(
                            handwrittenPng = if (document.options.appearance.requiresHandwritten) mark?.png else null))
                        val signed = CancellableSigningInput(document.file.inputStream(), currentContext).use { input -> partial.outputStream().use { out ->
                            DiagnosticLog.measureSuspend("signer", "pdf_sign_and_timestamp", "engine") {
                                pdfEngine.signConfiguredPdf(input, out, cert, signer, config).getOrThrow()
                            }
                        } }
                        time = signed.timestamp; size = signed.signatureSize
                    } else {
                        time = System.currentTimeMillis()
                        size = CancellableSigningInput(document.file.inputStream(), currentContext).use { input -> partial.outputStream().use { out ->
                            DetachedFileSigner.sign(input, out, cert) { digest -> runBlocking { signer(digest).getOrThrow() } }
                        } }
                    }
                    coroutineContext.ensureActive()
                    check(partial.renameTo(output)) { "No se pudo publicar el documento firmado" }
                    results += SignedArtifact(document, DocumentSignStatus.SIGNED, output, cert,
                        citizen.fullName, citizen.dniNumber, time, size)
                    onProgress(index + 1, document.name, results.toList(), cert)
                } catch (e: Exception) {
                    DiagnosticLog.failure("signer", "batch_document", e)
                    if (e is CancellationException || cardError is CancellationException) throw CancellationException("Firma cancelada", e)
                    val fatal = cardError != null || generateSequence<Throwable>(e) { it.cause }.any { it is CardProtocolException }
                    results += SignedArtifact(document, if (fatal) DocumentSignStatus.INTERRUPTED else DocumentSignStatus.FAILED,
                        message = (cardError ?: e).message ?: "No se pudo firmar este archivo")
                    if (fatal) {
                        val message = (cardError ?: e).message ?: "Se interrumpió la sesión NFC"
                        approved.drop(index + 1).forEach { results += SignedArtifact(it, DocumentSignStatus.INTERRUPTED, message = "Pendiente: vuelva a acercar el DNI") }
                        return BatchSignResult(results, message)
                    }
                } finally {
                    staging?.delete()
                }
            }
            return BatchSignResult(results)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val completedIds = results.map { it.document.id }.toSet()
            DiagnosticLog.failure("signer", "batch_session", e)
            approved.filter { it.id !in completedIds }.forEach { results += SignedArtifact(it, DocumentSignStatus.INTERRUPTED, certificate = cert, message = e.message) }
            return BatchSignResult(results, e.message)
        } finally {
            session.close()
            DiagnosticLog.event("signer", "batch_end", "count" to results.count { it.status == DocumentSignStatus.SIGNED })
        }
    }
}
