package pe.gob.reniec.dnie.domain.signing

/** Pure filename policy shared by imported originals and generated artifacts. */
object SigningFileNames {
    private val unsafe = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    fun safeName(name: String): String = name.replace(unsafe, "_").trim().take(160)
        .takeUnless { it.isBlank() || it == "." || it == ".." } ?: "documento"
}
