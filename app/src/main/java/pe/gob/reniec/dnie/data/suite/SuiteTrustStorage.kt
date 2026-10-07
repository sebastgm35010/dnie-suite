package pe.gob.reniec.dnie.data.suite

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import pe.gob.reniec.dnie.signing.link.SuitePairing
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate encrypted trust from the compatible bridge. Never stores PIN or CAN. */
class SuiteTrustStorage(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "suite-trusted-pc-v2.bin"))
    private val alias="dnie-suite-trusted-pc-v2"
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized fun save(pair: SuitePairing) {
        val raw = pair.encode().toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(alias.toByteArray()) }
            val encrypted = cipher.doFinal(raw)
            val output = file.startWrite()
            try { output.write(cipher.iv); output.write(encrypted); file.finishWrite(output) }
            catch (e: Exception) { file.failWrite(output); throw e }
        } finally { raw.fill(0) }
    }
    @Synchronized fun load(): SuitePairing? {
        if (!file.baseFile.exists()) return null
        val bytes = file.openRead().use { it.readBytes() }
        var raw: ByteArray? = null
        try {
            require(bytes.size in 29..2048)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12))); updateAAD(alias.toByteArray())
            }
            raw = cipher.doFinal(bytes,12,bytes.size-12)
            return SuitePairing.parse(raw.toString(Charsets.UTF_8))
        } finally { raw?.fill(0); bytes.fill(0) }
    }
    @Synchronized fun forget() { file.delete() }
}
