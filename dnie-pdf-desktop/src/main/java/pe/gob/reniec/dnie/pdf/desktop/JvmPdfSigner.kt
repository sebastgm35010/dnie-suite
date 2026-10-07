package pe.gob.reniec.dnie.pdf.desktop

import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.io.ScratchFile
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import pe.gob.reniec.dnie.crypto.*
import pe.gob.reniec.dnie.pdf.*

/** PDFBox JVM adapter. CMS and RSA verification stay in the shared, proven crypto engine. */
class JvmPdfSigner(private val temporaryRoot: Path) : PadesSignerEngineInterface {
    companion object {
        const val SIGNATURE_RESERVE = 32768
        const val MAX_INPUT_BYTES = 256L * 1024 * 1024
    }
    override suspend fun signPdf(pdfInputStream: InputStream, pdfOutputStream: OutputStream,
        signingCertificateDer: ByteArray, cardSigner: suspend (ByteArray) -> Result<ByteArray>,
        preset: SignaturePreset, reason: String, location: String): Result<PdfSignResult> {
        if (preset != SignaturePreset.INVISIBLE) return Result.failure(IllegalArgumentException("Estampado visual aun no disponible en escritorio"))
        return signConfiguredPdf(pdfInputStream, pdfOutputStream, signingCertificateDer, cardSigner,
            PdfSigningOptions(visible = false, reason = reason, location = location))
    }
    override suspend fun signConfiguredPdf(input: InputStream, output: OutputStream, certificate: ByteArray,
        cardSigner: suspend (ByteArray) -> Result<ByteArray>, options: PdfSigningOptions): Result<PdfSignResult> = withContext(Dispatchers.IO) {
        try {
            val signer = X509CertificateParser.toX509Certificate(certificate)
            val citizen = X509CertificateParser.extractCitizenData(signer)
            val timestamp = System.currentTimeMillis()
            PrivatePdfWorkspace(temporaryRoot).use { workspace ->
                val source = workspace.directory.resolve("source.pdf")
                var total = 0L
                Files.newOutputStream(source).use { out ->
                    val bytes = ByteArray(65536)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(bytes); if (count < 0) break
                        total += count; require(total <= MAX_INPUT_BYTES) { "PDF mayor de 256 MiB" }
                        out.write(bytes, 0, count)
                    }
                }
                val signed = workspace.directory.resolve("signed.pdf")
                val memory = MemoryUsageSetting.setupMixed(32L * 1024 * 1024).setTempDir(workspace.directory.toFile())
                Loader.loadPDF(source.toFile(), "", null, null, { ScratchFile(memory) }).use { document ->
                    PdfSigningPolicy.validate(document, options.visible)
                    val signature = PDSignature().apply {
                        setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                        setSubFilter(COSName.getPDFName("ETSI.CAdES.detached"))
                        name = citizen.fullName
                        signDate = Calendar.getInstance(TimeZone.getTimeZone("America/Lima")).apply { timeInMillis = timestamp }
                        reason = if(options.appearance.showReason) options.reason else ""; location = if(options.appearance.showLocation) options.location else ""
                    }
                    var size = 0
                    SignatureOptions().use { signingOptions ->
                        signingOptions.preferredSignatureSize = SIGNATURE_RESERVE
                        if(options.visible) signingOptions.page=options.pageNumber-1
                        document.addSignature(signature, null, signingOptions)
                        if(options.visible) DesktopStampRenderer.apply(document,signature,options,citizen.fullName,citizen.dniNumber,timestamp)
                        Files.newOutputStream(signed).use { stream ->
                            val signing = document.saveIncrementalForExternalSigning(stream)
                            val context = coroutineContext
                            val cms = signing.content.use { content -> CmsSignatureBuilder.buildDetachedSignatureFromStream(
                                inputStream = content, signerCertificate = signer,
                                cardSigner = { hash ->
                                    context.ensureActive()
                                    runBlocking(context) { CardSignatureVerifier.verify(signer, hash, cardSigner(hash).getOrThrow()) }
                                }) }
                            require(cms.size <= SIGNATURE_RESERVE) { "La firma excede el espacio PDF reservado" }
                            size = cms.size; signing.setSignature(cms)
                        }
                    }
                    coroutineContext.ensureActive()
                    // No partial PDF reaches caller if parsing/card/signature verification failed.
                    Files.newInputStream(signed).use { it.copyTo(output) }; output.flush()
                    Result.success(PdfSignResult(signerName = citizen.fullName, signerDni = citizen.dniNumber,
                        timestamp = timestamp, signatureSize = size, pageNumber = if(options.visible) options.pageNumber else 0))
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(failure) }
    }
}
