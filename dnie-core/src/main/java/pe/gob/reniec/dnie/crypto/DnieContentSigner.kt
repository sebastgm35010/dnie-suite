package pe.gob.reniec.dnie.crypto

import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Adaptador de [ContentSigner] de BouncyCastle para delegar la firma digital
 * al chip seguro del DNIe peruano v2/v3 vía comandos APDU ISO 7816-4.
 *
 * Flujo criptográfico:
 * 1. BouncyCastle serializa los atributos firmados (`signedAttrs`) DER en el [outputStream].
 * 2. [getSignature] acumula los bytes, calcula el resumen criptográfico SHA-256 (32 bytes).
 * 3. Delega el hash de 32 bytes a la tarjeta inteligente mediante el callback [cardSigner].
 * 4. El adaptador ejecuta MSE + PSO: Compute Digital Signature. V3 recibe el digest
 *    de 32 bytes; el formato APDU de cada tarjeta corresponde al módulo de protocolo.
 * 5. Retorna la firma RSA-2048 en crudo (256 bytes).
 */
class DnieContentSigner(
    private val cardSigner: (sha256Digest: ByteArray) -> ByteArray
) : ContentSigner {

    /**
     * Constructor secundario que acepta una función suspendida con [Result].
     */
    constructor(
        suspendSigner: suspend (sha256Digest: ByteArray) -> Result<ByteArray>
    ) : this(cardSigner = { digest: ByteArray ->
        runBlocking {
            suspendSigner(digest).getOrThrow()
        }
    })

    private val outputStream = ByteArrayOutputStream()
    private val algorithmIdentifier = AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption)

    override fun getAlgorithmIdentifier(): AlgorithmIdentifier = algorithmIdentifier

    override fun getOutputStream(): OutputStream = outputStream

    override fun getSignature(): ByteArray {
        val bytesToSign = outputStream.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytesToSign)

        val signature = cardSigner(digest)
        require(signature.size == 256) {
            "La firma digital RSA-2048 del DNIe debe tener exactamente 256 bytes (recibido ${signature.size})"
        }
        return signature
    }
}
