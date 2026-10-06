package pe.gob.reniec.dnie.core.nfc

/** Experimental, local capability probe. Not a general-purpose decryption service. */
interface VaultProbeCard : DnieTransceiverInterface {
    fun requireVaultProbeSupport(): Result<Unit> = Result.failure(
        UnsupportedOperationException("El transporte no declaró soporte para la prueba de bóveda"))
    suspend fun decipherTestSecret(ciphertext: ByteArray): Result<ByteArray>
    /** One local user-approved envelope, after same-session PIN; never exported as a service. */
    suspend fun unwrapVaultKey(ciphertext: ByteArray): Result<ByteArray> = Result.failure(
        UnsupportedOperationException("Descifrado de archivos no disponible en este adaptador"))
}
