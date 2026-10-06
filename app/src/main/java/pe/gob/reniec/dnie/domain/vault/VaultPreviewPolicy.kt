package pe.gob.reniec.dnie.domain.vault

import java.util.Locale

/** Viewer capabilities, shared by library actions and authenticated decoding. No Android or Compose. */
object VaultPreviewPolicy {
    const val MAX_BYTES = 32L * 1024 * 1024
    private val extensionMime = mapOf(
        "pdf" to "application/pdf", "png" to "image/png", "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg", "webp" to "image/webp"
    ) + listOf("txt", "csv", "log", "md", "json", "xml", "html", "htm", "css", "js", "yaml", "yml")
        .associateWith { "text/plain" }

    fun resolve(name: String?, mime: String?): String? {
        val declared = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val effective = if (declared.isNullOrBlank() || declared == "application/octet-stream")
            extensionMime[name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)] else declared
        return effective?.takeIf { it == "application/pdf" || it.startsWith("text/") ||
            it in setOf("image/png", "image/jpeg", "image/webp") }
    }
    /** Public hints only decide whether to offer an action. Authenticated metadata is checked again. */
    fun canOffer(name: String?, mime: String?, encryptedBytes: Long): Boolean =
        encryptedBytes in 1..(MAX_BYTES + 1048576) && resolve(name, mime) != null
}
