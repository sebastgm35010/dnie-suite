package pe.gob.reniec.dnie.crypto

import org.bouncycastle.util.encoders.Base64
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.cert.X509Certificate

/**
 * Exportador de certificados digitales X.509 a formatos estándar de interoperabilidad (.cer y .pem).
 */
object CertificateExporter {

    private const val PEM_HEADER = "-----BEGIN CERTIFICATE-----"
    private const val PEM_FOOTER = "-----END CERTIFICATE-----"
    private const val PEM_LINE_LENGTH = 64

    /**
     * Convierte los bytes DER de un certificado en una cadena con formato Base64 PEM estándar (RFC 7468).
     *
     * @param derBytes Bytes codificados en formato ASN.1 DER.
     * @return Cadena PEM con encabezado "-----BEGIN CERTIFICATE-----", líneas de 64 caracteres y pie.
     */
    fun toPem(derBytes: ByteArray): String {
        require(derBytes.isNotEmpty()) { "Los bytes del certificado no pueden estar vacíos" }
        val base64 = Base64.toBase64String(derBytes)
        val formattedBody = base64.chunked(PEM_LINE_LENGTH).joinToString("\n")
        return "$PEM_HEADER\n$formattedBody\n$PEM_FOOTER\n"
    }

    /**
     * Convierte un [X509Certificate] a una cadena PEM estándar.
     */
    fun toPem(certificate: X509Certificate): String {
        return toPem(certificate.encoded)
    }

    /**
     * Exporta los bytes DER directamente para ser guardados en formato binario .cer.
     */
    fun toCer(derBytes: ByteArray): ByteArray {
        require(derBytes.isNotEmpty()) { "Los bytes del certificado no pueden estar vacíos" }
        return derBytes.copyOf()
    }

    /**
     * Exporta los bytes DER de un [X509Certificate] para formato binario .cer.
     */
    fun toCer(certificate: X509Certificate): ByteArray {
        return toCer(certificate.encoded)
    }

    /**
     * Guarda el certificado en disco en formato binario DER (.cer).
     */
    fun exportToCerFile(derBytes: ByteArray, destinationFile: File): File {
        destinationFile.parentFile?.mkdirs()
        FileOutputStream(destinationFile).use { it.write(derBytes) }
        return destinationFile
    }

    /**
     * Guarda el certificado en disco en formato PEM (.pem).
     */
    fun exportToPemFile(derBytes: ByteArray, destinationFile: File): File {
        destinationFile.parentFile?.mkdirs()
        val pemContent = toPem(derBytes)
        destinationFile.writeText(pemContent, Charsets.US_ASCII)
        return destinationFile
    }

    /**
     * Escribe el certificado en un flujo de salida [OutputStream] en formato DER binario.
     */
    fun writeCerToStream(derBytes: ByteArray, outputStream: OutputStream) {
        outputStream.write(derBytes)
        outputStream.flush()
    }

    /**
     * Escribe el certificado en un flujo de salida [OutputStream] en formato PEM.
     */
    fun writePemToStream(derBytes: ByteArray, outputStream: OutputStream) {
        val pemContent = toPem(derBytes)
        outputStream.write(pemContent.toByteArray(Charsets.US_ASCII))
        outputStream.flush()
    }
}
