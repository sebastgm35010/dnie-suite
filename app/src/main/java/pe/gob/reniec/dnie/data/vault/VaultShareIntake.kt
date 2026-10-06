package pe.gob.reniec.dnie.data.vault

import android.content.Intent
import android.net.Uri
import android.os.Build

/** Parse transient Android grants. Untrusted sources require an explicit encryption action in the vault. */
object VaultShareIntake {
    fun read(intent: Intent): List<Uri> = runCatching {
        val sources = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else { @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) })
            Intent.ACTION_SEND_MULTIPLE -> if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                else { @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty() }
            else -> emptyList()
        }
        sources.filter { it.scheme == "content" }.distinct().take(1000)
    }.getOrDefault(emptyList())
}
