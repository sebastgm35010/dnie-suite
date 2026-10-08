package pe.gob.reniec.dnie.vault.recovery

import java.security.MessageDigest
import java.util.UUID

/** Owns copies of sensitive buffers; callers must close packages and entries. */
class RecoveryEntry(val id: UUID, recipient: ByteArray, fileHash: ByteArray, secret: ByteArray) : AutoCloseable {
    private val recipientBytes = recipient.copyOf()
    private val fileHashBytes = fileHash.copyOf()
    private val secretBytes = secret.copyOf()
    private var closed = false
    init { require(recipient.size == 32 && fileHash.size == 32 && secret.size == 32) }
    internal fun recipient(): ByteArray { check(!closed); return recipientBytes.copyOf() }
    internal fun fileHash(): ByteArray { check(!closed); return fileHashBytes.copyOf() }
    internal fun secret(): ByteArray { check(!closed); return secretBytes.copyOf() }
    internal fun matchesRecipient(bytes: ByteArray) = !closed && MessageDigest.isEqual(recipientBytes, bytes)
    internal fun matchesFile(bytes: ByteArray) = !closed && MessageDigest.isEqual(fileHashBytes, bytes)
    fun coversSameCiphertext(other: RecoveryEntry): Boolean = !closed && !other.closed && id == other.id &&
        MessageDigest.isEqual(recipientBytes, other.recipientBytes) && MessageDigest.isEqual(fileHashBytes, other.fileHashBytes)
    override fun close() { closed = true; recipientBytes.fill(0); fileHashBytes.fill(0); secretBytes.fill(0) }
    override fun toString() = "RecoveryEntry(secrets=redacted)"
}

class OpenRecoveryPackage internal constructor(private val entries: List<RecoveryEntry>) : AutoCloseable {
    fun ids(): List<UUID> = entries.map { it.id }
    fun entry(id: UUID): RecoveryEntry = entries.singleOrNull { it.id == id }
        ?: throw IllegalArgumentException("El paquete no cubre este archivo")
    override fun close() = entries.forEach { it.close() }
    override fun toString() = "OpenRecoveryPackage(entries=${entries.size}, secrets=redacted)"
}
