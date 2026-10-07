package pe.gob.reniec.dnie.domain.signing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class SigningImportResult(val documents: List<SigningDocument>, val errors: List<String>)

/** Batch policy, independent of SAF/Context and UI. Callers supply storage operations. */
class ImportSigningDocuments(private val maxDocuments: Int = 50,
                             private val maxBytes: Long = 512L * 1024 * 1024) {
    suspend fun <T> run(sources: List<T>, existing: List<SigningDocument>, singlePdf: Boolean,
                       import: suspend (T) -> SigningDocument,
                       discard: suspend (SigningDocument) -> Unit,
                       failed: (Exception) -> Unit = {}): SigningImportResult {
        val imported = mutableListOf<SigningDocument>()
        val errors = mutableListOf<String>()
        // Replacing the PDF does not consume the old workspace's count/byte allowance.
        val retained = if (singlePdf) emptyList() else existing
        var bytes = retained.sumOf { it.size }
        try {
            for (source in if (singlePdf) sources.take(1) else sources) {
                currentCoroutineContext().ensureActive()
                if (retained.size + imported.size >= maxDocuments) { errors += "Máximo $maxDocuments archivos por lote"; break }
                val doc = try { import(source) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { failed(e); errors += e.message ?: "No se pudo añadir un archivo"; continue }
                // Own it immediately so cancellation or policy failures cannot leak the imported file.
                imported += doc
                currentCoroutineContext().ensureActive()
                if (singlePdf && !doc.isPdf) {
                    discard(doc); imported.removeAt(imported.lastIndex)
                    errors += "Seleccione un PDF sin contraseña. Para firmar un archivo protegido use Firma por lotes (.p7s)."
                    continue
                }
                if (doc.size < 0 || doc.size > maxBytes - bytes) {
                    discard(doc); imported.removeAt(imported.lastIndex)
                    errors += "El lote supera ${maxBytes / (1024 * 1024)} MiB"; break
                }
                bytes += doc.size
            }
            return SigningImportResult(imported.toList(), errors)
        } catch (failure: Throwable) {
            withContext(NonCancellable) { imported.forEach { discard(it) } }
            throw failure
        }
    }
}
