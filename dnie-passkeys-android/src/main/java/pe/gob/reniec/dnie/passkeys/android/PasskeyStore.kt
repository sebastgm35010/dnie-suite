package pe.gob.reniec.dnie.passkeys.android

import pe.gob.reniec.dnie.passkeys.PasskeyCatalog
import pe.gob.reniec.dnie.vault.VaultHeader
import java.security.interfaces.RSAPublicKey

/** Storage boundary lets the card authorization use case be verified without Android or UI. */
interface PasskeyStore {
    suspend fun header(): VaultHeader?
    suspend fun open(secret: ByteArray, expectedRecipient: ByteArray): PasskeyCatalog
    suspend fun save(catalog: PasskeyCatalog, key: RSAPublicKey)
}
