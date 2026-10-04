package pe.gob.reniec.dnie.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.crypto.CmsSignatureBuilder
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer
import pe.gob.reniec.dnie.crypto.X509CertificateParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.TimeZone

/**
 * Contrato de motor de firma digital de documentos PDF según el estándar PAdES.
 */
interface PadesSignerEngineInterface {
    suspend fun signPdf(
        pdfInputStream: InputStream,
        pdfOutputStream: OutputStream,
        signingCertificateDer: ByteArray,
        cardSigner: suspend (sha256Hash: ByteArray) -> Result<ByteArray>,
        preset: SignaturePreset,
        reason: String,
        location: String
    ): Result<PdfSignResult>
}

/**
 * Motor de firma digital PAdES (PDF Advanced Electronic Signatures) para DNIe v2/v3.
 *
 * Características técnicas:
 * 1. Implementa [SignatureInterface] conectando PDFBox Android con [CmsSignatureBuilder].
 * 2. Soporta PAdES Baseline (`/SubFilter /ETSI.CAdES.detached`) y legado (`/adbe.pkcs7.detached`).
 * 3. Aplica guardado estrictamente incremental ([PDDocument.saveIncremental]) preservando firmas anteriores y revisiones.
 * 4. Reserva 32 KB ([PREFERRED_SIGNATURE_SIZE]) para evitar desbordamiento con cadenas de certificación completas de RENIEC.
 * 5. Gestiona archivos temporales seguros para evitar colisiones de E/S en Android.
 */
class PadesSignerEngine : PadesSignerEngineInterface {

    companion object {
        const val PREFERRED_SIGNATURE_SIZE = 32768 // 32 KB

        const val SUBFILTER_ETSI_CADES_DETACHED = "ETSI.CAdES.detached"
        const val SUBFILTER_ADBE_PKCS7_DETACHED = "adbe.pkcs7.detached"

        val defaultInstance: PadesSignerEngine by lazy { PadesSignerEngine() }
    }

    init {
        CryptoProviderInitializer.init()
    }

    override suspend fun signPdf(
        pdfInputStream: InputStream,
        pdfOutputStream: OutputStream,
        signingCertificateDer: ByteArray,
        cardSigner: suspend (sha256Hash: ByteArray) -> Result<ByteArray>,
        preset: SignaturePreset,
        reason: String,
        location: String
    ): Result<PdfSignResult> = signPdf(
        pdfInputStream = pdfInputStream,
        pdfOutputStream = pdfOutputStream,
        signingCertificateDer = signingCertificateDer,
        cardSigner = cardSigner,
        preset = preset,
        reason = reason,
        location = location,
        pageNumber = 1,
        caCertificates = emptyList()
    )

    /**
     * Firma digital completa de un documento PDF con soporte para control de página y cadena CA.
     */
    suspend fun signPdf(
        pdfInputStream: InputStream,
        pdfOutputStream: OutputStream,
        signingCertificateDer: ByteArray,
        cardSigner: suspend (sha256Hash: ByteArray) -> Result<ByteArray>,
        preset: SignaturePreset,
        reason: String,
        location: String,
        pageNumber: Int = 1,
        caCertificates: List<X509Certificate> = emptyList(),
        subFilter: String = SUBFILTER_ETSI_CADES_DETACHED,
        customX: Float = 0f,
        customY: Float = 0f,
        customWidth: Float = VisualStampRenderer.DEFAULT_STAMP_WIDTH,
        customHeight: Float = VisualStampRenderer.DEFAULT_STAMP_HEIGHT
    ): Result<PdfSignResult> = withContext(Dispatchers.IO) {
        runCatching {
            val signerCert = X509CertificateParser.toX509Certificate(signingCertificateDer)
            val citizenData = X509CertificateParser.extractCitizenData(signerCert)

            // Usar archivos de trabajo temporales para aislamiento de lectura/escritura en saveIncremental
            val tempSourceFile = File.createTempFile("pades_src_", ".pdf")
            val tempSignedFile = File.createTempFile("pades_signed_", ".pdf")

            var signatureSize = 0
            val signingTimestamp = System.currentTimeMillis()

            try {
                // Copiar el stream de entrada al archivo temporal
                FileOutputStream(tempSourceFile).use { out ->
                    pdfInputStream.copyTo(out)
                }

                val document = PDDocument.load(tempSourceFile)

                // 1. Configurar diccionario PDSignature
                val pdSignature = PDSignature().apply {
                    setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                    setSubFilter(COSName.getPDFName(subFilter))
                    name = citizenData.fullName
                    signDate = Calendar.getInstance(TimeZone.getTimeZone("America/Lima")).apply {
                        timeInMillis = signingTimestamp
                    }
                    this.reason = reason
                    this.location = location
                }

                // 2. Configurar opciones de firma y buffer de 32 KB
                val signatureOptions = SignatureOptions().apply {
                    preferredSignatureSize = PREFERRED_SIGNATURE_SIZE
                }

                // 3. Estampado visual o firma invisible
                val actualPageNumber = if (preset != SignaturePreset.INVISIBLE) {
                    val clampedPage = pageNumber.coerceIn(1, maxOf(1, document.numberOfPages))
                    val targetPage = document.getPage(clampedPage - 1)
                    val rect = VisualStampRenderer.calculateBoundingBox(
                        page = targetPage,
                        preset = preset,
                        customX = customX,
                        customY = customY,
                        stampWidth = customWidth,
                        stampHeight = customHeight
                    )

                    val visualTemplate = VisualStampRenderer.createVisualSignatureTemplate(
                        targetDoc = document,
                        pageNumber = clampedPage,
                        rect = rect,
                        signerName = citizenData.fullName,
                        signerDni = citizenData.dniNumber,
                        reason = reason,
                        location = location
                    )
                    signatureOptions.setVisualSignature(visualTemplate)
                    signatureOptions.page = clampedPage
                    clampedPage
                } else {
                    0 // 0 indica firma invisible
                }

                // 4. Implementación de SignatureInterface que delega al CmsSignatureBuilder
                val signatureInterface = SignatureInterface { contentStream ->
                    val cmsBytes = CmsSignatureBuilder.buildDetachedSignatureFromStream(
                        inputStream = contentStream,
                        signerCertificate = signerCert,
                        caCertificates = caCertificates,
                        cardSigner = { hash: ByteArray ->
                            runBlocking {
                                cardSigner(hash).getOrThrow()
                            }
                        }
                    )
                    signatureSize = cmsBytes.size
                    cmsBytes
                }

                // 5. Agregar firma al documento y ejecutar guardado incremental
                document.addSignature(pdSignature, signatureInterface, signatureOptions)

                FileOutputStream(tempSignedFile).use { fos ->
                    document.saveIncremental(fos)
                }
                document.close()

                // Copiar el resultado al flujo de salida final
                tempSignedFile.inputStream().use { fis ->
                    fis.copyTo(pdfOutputStream)
                }
                pdfOutputStream.flush()

                PdfSignResult(
                    outputFile = null,
                    outputBytes = null,
                    signerName = citizenData.fullName,
                    signerDni = citizenData.dniNumber,
                    timestamp = signingTimestamp,
                    signatureSize = signatureSize,
                    pageNumber = actualPageNumber
                )
            } finally {
                tempSourceFile.delete()
                tempSignedFile.delete()
            }
        }
    }

    /**
     * Firma un archivo PDF en disco produciendo un nuevo archivo firmado con guardado incremental.
     */
    suspend fun signPdfFile(
        inputFile: File,
        outputFile: File,
        signingCertificateDer: ByteArray,
        cardSigner: suspend (sha256Hash: ByteArray) -> Result<ByteArray>,
        preset: SignaturePreset = SignaturePreset.BOTTOM_RIGHT,
        reason: String = "Soy el autor del documento",
        location: String = "Lima, Perú",
        pageNumber: Int = 1,
        caCertificates: List<X509Certificate> = emptyList()
    ): Result<PdfSignResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputFile.exists()) { "El archivo de entrada no existe: ${inputFile.absolutePath}" }
            outputFile.parentFile?.mkdirs()

            val result = inputFile.inputStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    signPdf(
                        pdfInputStream = input,
                        pdfOutputStream = output,
                        signingCertificateDer = signingCertificateDer,
                        cardSigner = cardSigner,
                        preset = preset,
                        reason = reason,
                        location = location,
                        pageNumber = pageNumber,
                        caCertificates = caCertificates
                    ).getOrThrow()
                }
            }

            result.copy(outputFile = outputFile)
        }
    }

    /**
     * Firma un arreglo de bytes PDF en memoria.
     */
    suspend fun signPdfBytes(
        inputPdfBytes: ByteArray,
        signingCertificateDer: ByteArray,
        cardSigner: suspend (sha256Hash: ByteArray) -> Result<ByteArray>,
        preset: SignaturePreset = SignaturePreset.BOTTOM_RIGHT,
        reason: String = "Soy el autor del documento",
        location: String = "Lima, Perú",
        pageNumber: Int = 1,
        caCertificates: List<X509Certificate> = emptyList()
    ): Result<PdfSignResult> = withContext(Dispatchers.IO) {
        runCatching {
            val outputStream = ByteArrayOutputStream()
            val result = inputPdfBytes.inputStream().use { input ->
                signPdf(
                    pdfInputStream = input,
                    pdfOutputStream = outputStream,
                    signingCertificateDer = signingCertificateDer,
                    cardSigner = cardSigner,
                    preset = preset,
                    reason = reason,
                    location = location,
                    pageNumber = pageNumber,
                    caCertificates = caCertificates
                ).getOrThrow()
            }

            result.copy(outputBytes = outputStream.toByteArray())
        }
    }
}
