package pe.gob.reniec.dnie.domain.vault

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class VaultBatchResult(val completed: Int, val failed: Int)

/** Independent files commit independently. Cancellation preserves successes and stops subsequent files. */
class VaultBatchImportUseCase {
    suspend fun <T> run(sources: List<T>, encrypt: suspend (T) -> Unit,
                       progress: (done: Int, total: Int, failed: Int) -> Unit): VaultBatchResult {
        require(sources.size <= 1000) { "Selecciona hasta 1000 archivos por lote" }
        var successes = 0
        var failures = 0
        sources.forEachIndexed { index, source ->
            currentCoroutineContext().ensureActive()
            try { encrypt(source); successes++ }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failures++ }
            progress(index + 1, sources.size, failures)
        }
        return VaultBatchResult(successes, failures)
    }
}
