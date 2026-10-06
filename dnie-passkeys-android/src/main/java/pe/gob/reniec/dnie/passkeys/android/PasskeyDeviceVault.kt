package pe.gob.reniec.dnie.passkeys.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import pe.gob.reniec.dnie.passkeys.PasskeyCatalog
import java.io.*
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-only alternate protection, gated by a fresh Class 3 biometric CryptoObject. */
class PasskeyDeviceVault(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "passkeys-biometric.bin"))
    fun enabled() = file.baseFile.exists()
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    fun disable() { synchronized(lock) { file.delete(); store().deleteEntry(ALIAS) } }
    fun enrollmentCipher(): Cipher = synchronized(lock) {
        disable()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true).setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true).build())
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, generator.generateKey()) }
    }
    fun enroll(cipher: Cipher, bytes: ByteArray, revision: ByteArray) = synchronized(lock) {
        require(revision.size == 32 && bytes.size <= PasskeyCatalog.MAX_BYTES)
        cipher.updateAAD(revision)
        val sealed = cipher.doFinal(bytes)
        val output = file.startWrite()
        try {
            DataOutputStream(output).apply { writeInt(1); write(revision); write(cipher.iv); writeInt(sealed.size); write(sealed) }
            file.finishWrite(output)
        } catch (e: Throwable) { file.failWrite(output); throw e }
    }
    class Pending(val cipher: Cipher, internal val ciphertext: ByteArray, val revision: ByteArray)
    fun prepare(revision: ByteArray): Pending = synchronized(lock) {
        file.openRead().use { stream ->
            val input = DataInputStream(stream)
            require(input.readInt() == 1)
            val expected = ByteArray(32).also(input::readFully)
            require(MessageDigest.isEqual(expected, revision)) { "Device enrollment outdated" }
            val nonce = ByteArray(12).also(input::readFully)
            val size = input.readInt(); require(size in 16..PasskeyCatalog.MAX_BYTES + 16)
            val payload = ByteArray(size).also(input::readFully); require(input.read() == -1)
            val key = store().getKey(ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce)); updateAAD(expected)
            }
            Pending(cipher, payload, expected)
        }
    }
    fun open(pending: Pending, cipher: Cipher, currentRevision: ByteArray): PasskeyCatalog {
        require(cipher === pending.cipher && MessageDigest.isEqual(pending.revision, currentRevision))
        val bytes = cipher.doFinal(pending.ciphertext)
        return try { PasskeyCatalog.decode(bytes) } finally { bytes.fill(0) }
    }
    companion object { private const val ALIAS = "dnie-passkeys-biometric-v1"; private val lock = Any() }
}
