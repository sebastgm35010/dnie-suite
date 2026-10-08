package pe.gob.reniec.dnie.data.vault

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import pe.gob.reniec.dnie.vault.*
import pe.gob.reniec.dnie.vault.recovery.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** Runs under the repository IO mutex; owns no UI, card session or saved password. */
internal class AndroidVaultRecoveryAdapter(private val app: Context) {
    private val engine = VaultRecoveryService()
    private val codec = RecoveryPackageCodec()
    private val staging get() = File(app.cacheDir, "vault-private")

    suspend fun prepare(store: VaultFileStore, id: String, header: VaultHeader,
                        secret: ByteArray, password: CharArray, destination: Uri) {
        val active = currentCoroutineContext()
        var entry: RecoveryEntry? = null
        try {
            store.exportEncrypted(id) { stream ->
                entry = engine.prepare(stream, { actual ->
                    require(actual.id == header.id && MessageDigest.isEqual(actual.recipient, header.recipient) &&
                        MessageDigest.isEqual(actual.envelope, header.envelope)) { "El archivo cambió; vuelva a autorizarlo" }
                    active.ensureActive(); secret.copyOf()
                }, { active.ensureActive() })
            }
            // Historical public fingerprint is context, not proof of identity or a secret.
            val encoded = codec.seal(listOf(requireNotNull(entry)), password, header.recipient) { active.ensureActive() }
            try {
                codec.open(encoded, password) { active.ensureActive() }.use { require(it.ids() == listOf(header.id)) }
                active.ensureActive()
                app.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                    output.write(encoded); output.flush()
                } ?: error("No se pudo guardar el paquete")
                active.ensureActive()
            } finally { encoded.fill(0) }
        } finally { entry?.close() }
    }

    suspend fun restore(store: VaultFileStore, id: String, packageUri: Uri,
                        password: CharArray, destination: Uri): AndroidVaultRepository.Restoration {
        val active = currentCoroutineContext()
        val encoded = ByteArrayOutputStream().also { output ->
            app.contentResolver.openInputStream(packageUri)?.use { input ->
                VaultContainer.copyBounded(input, output, { active.ensureActive() }, RecoveryPackageCodec.MAX_BYTES.toLong())
            } ?: error("No se pudo leer el paquete")
        }.toByteArray()
        try {
            return codec.open(encoded, password) { active.ensureActive() }.use { pkg ->
                val entry = pkg.entry(store.readHeader(id).id)
                var result: AndroidVaultRepository.Restoration? = null
                store.exportEncrypted(id) { encrypted ->
                    engine.restore(encrypted, entry, staging, { verified, metadata ->
                        active.ensureActive()
                        app.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                            VaultContainer.copyBounded(verified, output, { active.ensureActive() }); output.flush()
                        } ?: error("No se pudo abrir el destino")
                        active.ensureActive()
                        result = AndroidVaultRepository.Restoration(metadata,
                            RestoredDocumentName.apply(app, destination, metadata.name))
                    }, { active.ensureActive() })
                }
                requireNotNull(result)
            }
        } finally { encoded.fill(0) }
    }
}
