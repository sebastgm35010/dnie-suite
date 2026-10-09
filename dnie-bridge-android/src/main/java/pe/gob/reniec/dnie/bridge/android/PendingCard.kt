package pe.gob.reniec.dnie.bridge.android

import java.util.concurrent.CompletableFuture

/** Single physical discovery, independently cancellable while the PC is authenticated. */
class PendingCard<T> : AutoCloseable {
    private val value = CompletableFuture<T>()
    fun offer(card: T): Boolean = value.complete(card)
    fun await(): T = value.get()
    override fun close() { value.cancel(false) }
}
