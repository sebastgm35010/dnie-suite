package pe.gob.reniec.dnie.data.signing

import pe.gob.reniec.dnie.domain.signing.SigningDocument
import pe.gob.reniec.dnie.domain.signing.SigningFileNames

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

object DocumentImportService {
    const val MAX_FILE_BYTES = 256L * 1024 * 1024

    suspend fun import(context: Context, uri: Uri): SigningDocument {
        var owned: SigningDocument? = null
        try {
            return withContext(Dispatchers.IO) { readLocal(context, uri).also { owned = it } }
        } catch (failure: Throwable) {
            withContext(NonCancellable) { owned?.let { discard(it) } }
            throw failure
        }
    }

    private suspend fun readLocal(context: Context, uri: Uri): SigningDocument {
        var name = "documento"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
        }
        val safeName = SigningFileNames.safeName(name)
        val id = UUID.randomUUID().toString()
        val directory = File(context.cacheDir, "signing_inputs/$id").apply { mkdirs() }
        val target = File(directory, safeName)
        try {
            var total = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            requireNotNull(context.contentResolver.openInputStream(uri)) { "No se pudo abrir $safeName" }.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_FILE_BYTES) { "El archivo supera 256 MiB" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val sha256 = digest.digest()
            val prefix = target.inputStream().use { input ->
                val bytes = ByteArray(1024)
                bytes.copyOf(input.read(bytes).coerceAtLeast(0)).toString(Charsets.ISO_8859_1)
            }
            val pdf = prefix.contains("%PDF-") || safeName.endsWith(".pdf", true) || context.contentResolver.getType(uri) == "application/pdf"
            val pages = if (pdf) PdfDocumentMetadata.pages(target) else emptyList()
            return SigningDocument(id, safeName, target, total, pages, sha256 = sha256)
        } catch (e: Throwable) {
            target.delete(); directory.delete()
            throw e
        }
    }

    suspend fun discard(document: SigningDocument) = withContext(Dispatchers.IO) {
        document.file.delete(); document.file.parentFile?.delete()
        Unit
    }
}
