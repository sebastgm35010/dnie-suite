package pe.gob.reniec.dnie.passkeys.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import pe.gob.reniec.dnie.passkeys.PasskeyCatalog
import pe.gob.reniec.dnie.passkeys.PasskeyLocalCipher
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Independent primary store. Its presence prevents falling back to a stale DNI catalog. */
class IndependentPasskeyVault(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "passkeys/independent-v1.bin"))
    fun exists(): Boolean = synchronized(lock) { present() }
    private fun present() = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
    private fun keys() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    fun enrollmentCipher(): Cipher = synchronized(lock) {
        check(!present()) { "El almacén independiente ya existe" }
        val keystore = keys()
        val key = if (keystore.containsAlias(ALIAS)) keystore.getKey(ALIAS, null) as SecretKey else {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true).setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true).build())
            generator.generateKey()
        }
        try { Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) } }
        catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
            // Only an orphan from an uncommitted enrollment can be replaced here.
            check(!present()); keystore.deleteEntry(ALIAS); enrollmentCipher()
        }
    }
    fun initialize(cipher: Cipher, catalog: PasskeyCatalog) = synchronized(lock) {
        check(!present())
        val binding = PasskeyLocalCipher.secret()
        val secret = PasskeyLocalCipher.secret()
        try {
            cipher.updateAAD(MAGIC); cipher.updateAAD(binding)
            val wrapped = cipher.doFinal(secret)
            require(cipher.iv.size == 12 && wrapped.size == 48)
            val payload = PasskeyLocalCipher.seal(catalog, secret, binding)
            // Verify the complete catalog before the sole atomic activation point.
            PasskeyLocalCipher.open(payload, secret, binding).close()
            write(MAGIC + binding + cipher.iv + wrapped + payload)
        } finally { secret.fill(0) }
    }
    class Pending internal constructor(val cipher: Cipher, internal val bytes: ByteArray, val revision: ByteArray)
    fun prepare(): Pending = synchronized(lock) {
        val bytes = read()
        val key = keys().getKey(ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 40, 12))
        cipher.updateAAD(MAGIC); cipher.updateAAD(bytes, 8, 32)
        Pending(cipher, bytes, digest(bytes))
    }
    class Session internal constructor(val catalog: PasskeyCatalog, internal val secret: ByteArray,
        internal val header: ByteArray, internal val revision: ByteArray) : AutoCloseable {
        private var closed = false
        internal fun requireOpen() { check(!closed) }
        override fun close() { if (!closed) { closed = true; catalog.close(); secret.fill(0) } }
    }
    fun open(pending: Pending, cipher: Cipher): Session = synchronized(lock) {
        require(cipher === pending.cipher && MessageDigest.isEqual(pending.revision, digest(read())))
        val secret = cipher.doFinal(pending.bytes, 52, 48)
        try {
            require(secret.size == 32)
            Session(PasskeyLocalCipher.open(pending.bytes.copyOfRange(100, pending.bytes.size), secret,
                pending.bytes.copyOfRange(8, 40)), secret, pending.bytes.copyOfRange(0, 100), pending.revision)
        } catch (e: Throwable) { secret.fill(0); throw e }
    }
    fun save(session: Session) = synchronized(lock) {
        session.requireOpen()
        require(MessageDigest.isEqual(session.revision, digest(read()))) { "El catálogo cambió" }
        val sealed = PasskeyLocalCipher.seal(session.catalog, session.secret, session.header.copyOfRange(8, 40))
        write(session.header + sealed)
        PasskeyAuthorizations.continuation.cancel()
    }
    private fun read(): ByteArray = file.openRead().use {
        val bytes = it.readNBytes(100 + PasskeyLocalCipher.MAX_SEALED_BYTES + 1)
        require(bytes.size in 128..100 + PasskeyLocalCipher.MAX_SEALED_BYTES)
        require(MessageDigest.isEqual(bytes.copyOfRange(0, 8), MAGIC))
        bytes
    }
    private fun write(bytes: ByteArray) {
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Throwable) { file.failWrite(stream); throw e }
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    companion object {
        private val MAGIC = "DNIEPKI1".toByteArray(Charsets.US_ASCII)
        private const val ALIAS = "dnie-passkeys-independent-v1"
        private val lock = Any()
    }
}
