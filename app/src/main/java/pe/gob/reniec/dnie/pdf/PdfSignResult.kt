package pe.gob.reniec.dnie.pdf

import java.io.File

/**
 * Resultado exitoso de un proceso de firma digital PAdES sobre un documento PDF.
 *
 * @property outputBytes Contenido binario del documento firmado (si se generó en memoria).
 * @property outputFile Archivo resultante con la firma incremental aplicada.
 * @property signerName Nombre completo del ciudadano firmante.
 * @property signerDni Número de DNI del firmante.
 * @property timestamp Marca de tiempo en milisegundos en la que se aplicó la firma.
 * @property signatureSize Tamaño en bytes del contenedor CMS/PKCS#7 incrustado.
 * @property pageNumber Página en la que se aplicó el estampado visual (0 para invisible).
 */
data class PdfSignResult(
    val outputBytes: ByteArray? = null,
    val outputFile: File? = null,
    val signerName: String,
    val signerDni: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val signatureSize: Int,
    val pageNumber: Int = 1
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PdfSignResult

        if (outputBytes != null) {
            if (other.outputBytes == null) return false
            if (!outputBytes.contentEquals(other.outputBytes)) return false
        } else if (other.outputBytes != null) return false
        if (outputFile != other.outputFile) return false
        if (signerName != other.signerName) return false
        if (signerDni != other.signerDni) return false
        if (timestamp != other.timestamp) return false
        if (signatureSize != other.signatureSize) return false
        if (pageNumber != other.pageNumber) return false

        return true
    }

    override fun hashCode(): Int {
        var result = outputBytes?.contentHashCode() ?: 0
        result = 31 * result + (outputFile?.hashCode() ?: 0)
        result = 31 * result + signerName.hashCode()
        result = 31 * result + (signerDni?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + signatureSize
        result = 31 * result + pageNumber
        return result
    }
}
