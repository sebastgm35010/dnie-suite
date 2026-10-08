package pe.gob.reniec.dnie.vault.recovery

import pe.gob.reniec.dnie.vault.VaultContainer
import pe.gob.reniec.dnie.vault.VaultHeader
import pe.gob.reniec.dnie.vault.VaultMetadata
import java.io.*
import java.security.DigestInputStream
import java.security.MessageDigest

/** No NFC/PCSC or UI dependencies. Secret supplier must be explicit: no simulated card fallback. */
class VaultRecoveryService(private val container: VaultContainer = VaultContainer()) {
    fun prepare(input: InputStream, unwrap: (VaultHeader) -> ByteArray,
                checkCancelled: () -> Unit = {}): RecoveryEntry {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(input, digest).use { stream ->
            val header = container.readHeader(stream)
            val secret = unwrap(header)
            try {
                container.openPayload(stream, discard(), header, secret, checkCancelled)
                checkCancelled()
                return RecoveryEntry(header.id, header.recipient, digest.digest(), secret)
            } finally { secret.fill(0) }
        }
    }

    /** Only invokes publish after every segment AND exact ciphertext digest have authenticated. */
    fun restore(input: InputStream, entry: RecoveryEntry, privateStaging: File,
                publish: (InputStream, VaultMetadata) -> Unit,
                checkCancelled: () -> Unit = {}): VaultMetadata {
        require(privateStaging.isDirectory)
        val temporary = File.createTempFile("recovery-", ".plain", privateStaging)
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val metadata = DigestInputStream(input, digest).use { stream ->
                val header = container.readHeader(stream)
                require(header.id == entry.id && entry.matchesRecipient(header.recipient)) { "Entrada de otro archivo" }
                val secret = entry.secret()
                try { FileOutputStream(temporary).use { output ->
                    container.openPayload(stream, output, header, secret, checkCancelled).also {
                        output.flush(); output.fd.sync()
                    }
                } } finally { secret.fill(0) }
            }
            require(entry.matchesFile(digest.digest())) { "El archivo no coincide con el paquete" }
            checkCancelled()
            FileInputStream(temporary).use { publish(it, metadata) }
            return metadata
        } finally { check(temporary.delete() || !temporary.exists()) { "No se pudo retirar staging privado" } }
    }

    private fun discard() = object : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(bytes: ByteArray, offset: Int, count: Int) = Unit
    }
}
