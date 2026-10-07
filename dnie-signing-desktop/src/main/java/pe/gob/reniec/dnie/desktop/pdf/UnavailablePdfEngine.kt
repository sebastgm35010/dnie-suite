package pe.gob.reniec.dnie.desktop.pdf

import java.io.InputStream
import java.io.OutputStream
import pe.gob.reniec.dnie.pdf.*

/** CMS-only delivery. No PDF callback or private operation is executed here. */
class UnavailablePdfEngine : PadesSignerEngineInterface {
    override suspend fun signConfiguredPdf(input: InputStream, output: OutputStream, certificate: ByteArray,
        cardSigner: suspend (ByteArray) -> Result<ByteArray>, options: PdfSigningOptions): Result<PdfSignResult> = unsupported()
    override suspend fun signPdf(pdfInputStream: InputStream, pdfOutputStream: OutputStream, signingCertificateDer: ByteArray,
        cardSigner: suspend (ByteArray) -> Result<ByteArray>, preset: SignaturePreset, reason: String, location: String): Result<PdfSignResult> = unsupported()
    private fun unsupported(): Result<PdfSignResult> = Result.failure(UnsupportedOperationException("PDF integrado todavía no disponible; use CMS separado"))
}
