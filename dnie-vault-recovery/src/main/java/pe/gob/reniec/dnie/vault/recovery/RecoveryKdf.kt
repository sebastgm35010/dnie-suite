package pe.gob.reniec.dnie.vault.recovery

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** Experimental fixed profile: benchmark on Android before shipping. Never accepts arbitrary costs. */
internal object RecoveryKdf {
    const val MEMORY_KIB = 65536
    const val PASSES = 3
    const val LANES = 4
    fun derive(password: CharArray, salt: ByteArray, identityContext: ByteArray,
               domain: String = "dnie:recovery:v1"): ByteArray {
        require(password.size in 16..1024) { "La contraseña de recuperación requiere al menos 16 caracteres" }
        require(password.none { it == '\u0000' })
        require(salt.size == 16 && identityContext.size == 32)
        val associated = domain.toByteArray(Charsets.US_ASCII) + identityContext
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13).withMemoryAsKB(MEMORY_KIB)
            .withIterations(PASSES).withParallelism(LANES).withSalt(salt).withAdditional(associated).build()
        val key = ByteArray(32)
        try {
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password, key)
            return key
        } catch (failure: Throwable) { key.fill(0); throw failure }
        finally { parameters.clear(); associated.fill(0) }
    }
}
