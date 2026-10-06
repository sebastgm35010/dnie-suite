package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult

/**
 * Contrato de alto nivel del transceptor APDU para DNIe peruano v2/v3.
 *
 * Conecta las capas de presentación / casos de uso con el protocolo criptográfico del chip.
 */
interface DnieTransceiverInterface : AutoCloseable {
    override fun close() {}
    /**
     * Selecciona el Applet PKI de RENIEC (AID: E8 28 BD 08 0F D2 50 47 65 6E 65 72 69 63).
     */
    suspend fun selectPkiApplet(): Result<Unit>

    /**
     * Lee y reconstruye un certificado digital X.509 público a partir de su archivo elemental (EF).
     * @param type Tipo de certificado ([CertificateType.SIGNATURE], [CertificateType.AUTHENTICATION], [CertificateType.ENCRYPTION]).
     * @return Arreglo de bytes con la estructura binaria DER limpia y sin relleno de tarjeta.
     */
    suspend fun readCertificate(type: CertificateType): Result<ByteArray>

    /**
     * Verifica el PIN en el chip seguro.
     * @param pin Cadena numérica del PIN (4 a 8 dígitos).
     * @return [PinVerificationResult.Success], [PinVerificationResult.InvalidPin] o [PinVerificationResult.CardBlocked].
     */
    suspend fun verifyPin(pin: String): Result<PinVerificationResult>

    /**
     * Computa una firma digital RSA-2048 con SHA-256 PKCS#1 v1.5 dentro del chip seguro de la tarjeta.
     * @param hash32Bytes Resumen criptográfico SHA-256 de 32 bytes del documento o datos a firmar.
     * @return Firma digital RSA-2048 en crudo (exactamente 256 bytes).
     */
    suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray>
}
