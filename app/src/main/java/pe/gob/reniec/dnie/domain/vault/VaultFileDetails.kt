package pe.gob.reniec.dnie.domain.vault

import pe.gob.reniec.dnie.vault.VaultCatalogItem

/** Read-only result: presentation does not depend on the repository implementation. */
data class VaultFileDetails(val item: VaultCatalogItem, val recipient: String)
