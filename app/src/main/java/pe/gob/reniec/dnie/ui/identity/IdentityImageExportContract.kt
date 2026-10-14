package pe.gob.reniec.dnie.ui.identity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

internal data class ImageExportRequest(val name: String, val mime: String)
internal class IdentityImageExportContract : ActivityResultContract<ImageExportRequest, Uri?>() {
    override fun createIntent(context: Context, input: ImageExportRequest): Intent {
        val extension = when (input.mime) { "image/jp2" -> "jp2"; "image/png" -> "png"; "image/jpeg" -> "jpg"; else -> "bin" }
        return Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(input.mime)
            .putExtra(Intent.EXTRA_TITLE, input.name + "." + extension)
    }
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == Activity.RESULT_OK) intent?.data else null
}
