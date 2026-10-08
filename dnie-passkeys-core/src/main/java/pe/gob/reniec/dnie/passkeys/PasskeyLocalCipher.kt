package pe.gob.reniec.dnie.passkeys

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Versioned local catalog protection. Keys are random, never derived from citizen data. */
object PasskeyLocalCipher {
    private val domain = "DNIE-PASSKEYS-LOCAL-1".toByteArray(Charsets.US_ASCII)
    const val MAX_SEALED_BYTES = PasskeyCatalog.MAX_BYTES + 28
    fun secret(): ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)
    fun seal(catalog: PasskeyCatalog, secret: ByteArray, binding: ByteArray): ByteArray {
        require(secret.size == 32 && binding.size == 32)
        val bytes = catalog.encode()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(domain); cipher.updateAAD(binding)
            return nonce + cipher.doFinal(bytes)
        } finally { bytes.fill(0) }
    }
    fun open(sealed: ByteArray, secret: ByteArray, binding: ByteArray): PasskeyCatalog {
        require(secret.size == 32 && binding.size == 32 && sealed.size in 28..MAX_SEALED_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, sealed, 0, 12))
        cipher.updateAAD(domain); cipher.updateAAD(binding)
        val bytes = cipher.doFinal(sealed, 12, sealed.size - 12)
        return try { PasskeyCatalog.decode(bytes) } finally { bytes.fill(0) }
    }
}
