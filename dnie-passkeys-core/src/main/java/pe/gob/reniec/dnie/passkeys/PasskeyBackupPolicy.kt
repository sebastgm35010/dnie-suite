package pe.gob.reniec.dnie.passkeys

/** BE is immutable, including for legacy version-1 credentials. Never turn export on retroactively. */
object PasskeyBackupPolicy {
    fun portableCopy(source: PasskeyCatalog): PasskeyCatalog {
        val selected = source.records.filter { it.backupEligible }
        require(selected.isNotEmpty()) { "No hay passkeys exportables. Las anteriores a 1.7 deben registrarse de nuevo en el sitio." }
        val result = PasskeyCatalog()
        try {
            selected.forEach { r -> result.records += PasskeyRecord(r.id.copyOf(), r.rpId, r.userId.copyOf(), r.username,
                r.displayName, r.privateKey.copyOf(), r.publicKey.copyOf(), backupEligible = true, backedUp = true) }
            return result
        } catch (e: Throwable) { result.close(); throw e }
    }
    fun validateRestore(catalog: PasskeyCatalog) {
        require(catalog.records.isNotEmpty() && catalog.records.all { it.backupEligible }) { "El respaldo contiene credenciales no exportables" }
        catalog.records.forEach { it.backedUp = true }
    }
}
