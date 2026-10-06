package pe.gob.reniec.dnie.domain.vault

import pe.gob.reniec.dnie.vault.VaultMetadata

object VaultNoteDraft {
    fun metadata(title: String, body: String): VaultMetadata {
        val name = title.trim()
        require(name.isNotBlank() && name.length <= 180 && name.none { it == '/' || it == '\\' || it.isISOControl() }) { "Nombre de nota inválido" }
        require(body.isNotBlank() && body.toByteArray(Charsets.UTF_8).size <= 1048576) { "Nota vacía o demasiado grande (máximo 1 MiB)" }
        return VaultMetadata(if (name.endsWith(".txt", true)) name else "$name.txt", "text/plain")
    }
}
