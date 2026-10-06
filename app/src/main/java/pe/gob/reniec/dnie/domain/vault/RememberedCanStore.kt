package pe.gob.reniec.dnie.domain.vault

/** Shared credential preference boundary. PIN is never part of this contract. */
interface RememberedCanStore {
    suspend fun read(): String
    suspend fun save(can: String)
    suspend fun clear()
}
