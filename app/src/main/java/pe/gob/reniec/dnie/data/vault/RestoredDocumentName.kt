package pe.gob.reniec.dnie.data.vault

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Rename only the user's completed copy; failure never invalidates restored content. */
object RestoredDocumentName {
    fun apply(context: Context, destination: Uri, originalName: String): Boolean = try {
        if (!DocumentsContract.isDocumentUri(context, destination)) false
        else {
            val resolver = context.contentResolver
            val supportsRename = resolver.query(destination,
                arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
                cursor.moveToFirst() &&
                    (cursor.getLong(0) and DocumentsContract.Document.FLAG_SUPPORTS_RENAME.toLong()) != 0L
            } ?: false
            supportsRename && DocumentsContract.renameDocument(resolver, destination, originalName) != null
        }
    } catch (_: Exception) { false }
}
