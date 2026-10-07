package pe.gob.reniec.dnie.pdf

import java.io.InputStream
import java.io.OutputStream

/**
 * Contrato de motor de firma digital de documentos PDF según el estándar PAdES.
 */
interface PadesSignerEngineInterface {
    suspend fun signConfiguredPdf(
        input: InputStream, output: OutputStream, certificate: ByteArray,
        cardSigner: suspend (ByteArray) -> Result<ByteArray>, options: PdfSigningOptions
    ): Result<PdfSignResult>
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
