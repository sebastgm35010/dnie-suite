package pe.gob.reniec.dnie.vault

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Narrow capability gate. No file API, persistent key or production container. */
fun interface CardKeyUnwrapper {
    suspend fun unwrapSyntheticSecret(ciphertext: ByteArray): ByteArray
}

data class VaultProbeResult(val certificateFingerprint: String, val verified: Boolean)

class VaultCapabilityProbe(private val random: SecureRandom = SecureRandom()) {
    fun readEncryptionCertificate(der: ByteArray): X509Certificate {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        require(certificate.basicConstraints < 0) { "El certificado debe ser del titular, no de una CA" }
        certificate.keyUsage?.let { usage ->
            require(usage.size > 2 && usage[2]) { "El certificado no permite cifrado de claves" }
        }
        requireRsa2048(certificate.publicKey as? RSAPublicKey)
        return certificate
    }

    fun fingerprint(key: RSAPublicKey): String = MessageDigest.getInstance("SHA-256")
        .digest(key.encoded).joinToString("") { "%02X".format(it) }

    /** Only self-generated data with the documented legacy envelope.
     * AES-GCM validates a small synthetic payload, not a streaming file format.
     */
    suspend fun verify(key: RSAPublicKey, card: CardKeyUnwrapper): VaultProbeResult {
        requireRsa2048(key)
        val secret = ByteArray(32).also(random::nextBytes)
        val payload = ByteArray(64).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val aad = "DNIe vault capability probe v1".toByteArray(Charsets.US_ASCII)
        var recovered: ByteArray? = null
        var plaintext: ByteArray? = null
        var envelope: ByteArray? = null
        try {
            val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsa.init(Cipher.ENCRYPT_MODE, key, random)
            val wrapped = rsa.doFinal(secret).also { envelope = it }
            val aes = Cipher.getInstance("AES/GCM/NoPadding")
            aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, nonce))
            aes.updateAAD(aad)
            val ciphertext = aes.doFinal(payload)
            val unwrapped = card.unwrapSyntheticSecret(wrapped).also { recovered = it }
            check(unwrapped.size == 32 && MessageDigest.isEqual(secret, unwrapped)) {
                "La tarjeta no recuperó el secreto de prueba"
            }
            aes.init(Cipher.DECRYPT_MODE, SecretKeySpec(unwrapped, "AES"), GCMParameterSpec(128, nonce))
            aes.updateAAD(aad)
            val opened = aes.doFinal(ciphertext).also { plaintext = it }
            check(MessageDigest.isEqual(payload, opened)) { "No se pudo verificar el contenido de prueba" }
            return VaultProbeResult(fingerprint(key), verified = true)
        } finally {
            secret.fill(0)
            payload.fill(0)
            recovered?.fill(0)
            plaintext?.fill(0)
            envelope?.fill(0)
        }
    }

    private fun requireRsa2048(key: RSAPublicKey?) {
        require(key != null && key.modulus.bitLength() == 2048) {
            "El perfil de prueba requiere certificado de cifrado RSA-2048"
        }
    }
}
