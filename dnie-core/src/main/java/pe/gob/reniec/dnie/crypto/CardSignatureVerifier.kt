package pe.gob.reniec.dnie.crypto

import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import pe.gob.reniec.dnie.core.apdu.ApduConstants

class SignatureIntegrityException(cause: Throwable? = null) : Exception("La firma del chip no corresponde al certificado del titular", cause)

/** Verify RSA PKCS#1 v1.5 over the exact SHA-256 digest submitted to PSO. */
object CardSignatureVerifier {
    fun verify(certificate: X509Certificate, digest: ByteArray, signature: ByteArray): ByteArray {
        try {
            require(digest.size == 32)
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", CryptoProviderInitializer.provider)
            cipher.init(Cipher.DECRYPT_MODE, certificate.publicKey)
            val expected = ApduConstants.SHA256_DIGEST_INFO_PREFIX + digest
            require(MessageDigest.isEqual(expected, cipher.doFinal(signature)))
            return signature
        } catch (e: Exception) { throw SignatureIntegrityException(e) }
    }
}
