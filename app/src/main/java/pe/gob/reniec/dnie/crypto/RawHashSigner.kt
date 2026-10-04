package pe.gob.reniec.dnie.crypto

import org.bouncycastle.util.encoders.Base64
import java.io.File
import java.io.FileOutputStream
import java.security.cert.X509Certificate

/**
 * Servicio criptográfico para firma digital de resúmenes (hashes) SHA-256 arbitrarios.
 *
 * Permite dos modos de operación:
 * 1. Firma RSA-2048 en crudo (256 bytes directos del chip seguro).
 * 2. Empaquetado en contenedor estándar CMS / PKCS#7 detached (.p7s).
 */
object RawHashSigner {

    init {
        CryptoProviderInitializer.init()
    }

    /**
     * Firma un resumen SHA-256 de 32 bytes y retorna la firma RSA-2048 pura de 256 bytes.
     *
     * @param hash32Bytes Resumen criptográfico de exactamente 32 bytes.
     * @param cardSigner Delegado de comunicación con la tarjeta inteligente.
     * @return Firma digital RSA-2048 de 256 bytes.
     */
    fun signRawHash(
        hash32Bytes: ByteArray,
        cardSigner: (sha256Digest: ByteArray) -> ByteArray
    ): ByteArray {
        require(hash32Bytes.size == 32) {
            "El hash a firmar debe tener exactamente 32 bytes (recibido ${hash32Bytes.size})"
        }
        val signature = cardSigner(hash32Bytes)
        require(signature.size == 256) {
            "La firma producida por el chip debe tener exactamente 256 bytes (recibido ${signature.size})"
        }
        return signature
    }

    /**
     * Versión suspendida para firmar hashes en crudo con control de excepciones [Result].
     */
    suspend fun signRawHashSuspend(
        hash32Bytes: ByteArray,
        cardSigner: suspend (sha256Digest: ByteArray) -> Result<ByteArray>
    ): Result<ByteArray> = runCatching {
        require(hash32Bytes.size == 32) {
            "El hash a firmar debe tener exactamente 32 bytes (recibido ${hash32Bytes.size})"
        }
        val result = cardSigner(hash32Bytes).getOrThrow()
        require(result.size == 256) {
            "La firma producida por el chip debe tener exactamente 256 bytes (recibido ${result.size})"
        }
        result
    }

    /**
     * Firma un resumen SHA-256 y lo empaqueta dentro de un contenedor CMS / PKCS#7 (.p7s) detached.
     *
     * @param hash32Bytes Resumen SHA-256 del archivo externo.
     * @param signerCertificate Certificado del titular.
     * @param caCertificates Cadena de confianza opcional de RENIEC.
     * @param cardSigner Delegado de firma en tarjeta.
     * @return Bytes del archivo de firma .p7s.
     */
    fun signHashToCmsContainer(
        hash32Bytes: ByteArray,
        signerCertificate: X509Certificate,
        caCertificates: List<X509Certificate> = emptyList(),
        cardSigner: (sha256Digest: ByteArray) -> ByteArray
    ): ByteArray {
        require(hash32Bytes.size == 32) {
            "El hash a empaquetar debe tener exactamente 32 bytes (recibido ${hash32Bytes.size})"
        }
        return CmsSignatureBuilder.buildDetachedSignature(
            documentHash = hash32Bytes,
            signerCertificate = signerCertificate,
            caCertificates = caCertificates,
            cardSigner = cardSigner
        )
    }

    /**
     * Versión suspendida de empaquetado CMS .p7s.
     */
    suspend fun signHashToCmsContainerSuspend(
        hash32Bytes: ByteArray,
        signerCertificate: X509Certificate,
        caCertificates: List<X509Certificate> = emptyList(),
        cardSigner: suspend (sha256Digest: ByteArray) -> Result<ByteArray>
    ): Result<ByteArray> = runCatching {
        require(hash32Bytes.size == 32) {
            "El hash a empaquetar debe tener exactamente 32 bytes (recibido ${hash32Bytes.size})"
        }
        val contentSigner = DnieContentSigner(cardSigner)
        CmsSignatureBuilder.buildDetachedSignature(
            documentHash = hash32Bytes,
            signerCertificate = signerCertificate,
            caCertificates = caCertificates,
            contentSigner = contentSigner
        )
    }

    /**
     * Guarda el contenedor CMS binario en un archivo con extensión .p7s.
     */
    fun exportToP7sFile(cmsBytes: ByteArray, destinationFile: File): File {
        destinationFile.parentFile?.mkdirs()
        FileOutputStream(destinationFile).use { it.write(cmsBytes) }
        return destinationFile
    }

    /**
     * Convierte la firma a representación Base64.
     */
    fun toBase64(signatureBytes: ByteArray): String {
        return Base64.toBase64String(signatureBytes)
    }

    /**
     * Convierte la firma a representación hexadecimal continua.
     */
    fun toHex(signatureBytes: ByteArray): String {
        return signatureBytes.joinToString("") { "%02X".format(it) }
    }
}
