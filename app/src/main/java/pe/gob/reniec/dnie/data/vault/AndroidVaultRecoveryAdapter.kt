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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream

/** Runs under the repository IO mutex; owns no UI, card session or saved password. */
internal class AndroidVaultRecoveryAdapter(private val app: Context) {
    private val engine = VaultRecoveryService()
    private val codec = RecoveryPackageCodec()
    private val staging get() = File(app.cacheDir, "vault-private")

    suspend fun prepareAll(store: VaultFileStore, batch: pe.gob.reniec.dnie.domain.vault.UnlockedRecoveryBatch,
                           password: CharArray, destination: Uri, progress: (Int, Int) -> Unit): Int {
        val active = currentCoroutineContext()
        require(store.list().map { it.id }.toSet() == batch.headers.keys) { "La bóveda cambió; vuelve a iniciar la protección completa." }
        val entries = linkedMapOf<java.util.UUID, RecoveryEntry>()
        try {
            batch.headers.entries.forEachIndexed { index, (id, expected) ->
                active.ensureActive()
                store.exportEncrypted(id) { input ->
                    val entry = engine.prepare(input, { actual ->
                        require(actual.id == expected.id && MessageDigest.isEqual(actual.envelope, expected.envelope) &&
                            MessageDigest.isEqual(actual.recipient, expected.recipient)) { "El archivo cambió." }
                        batch.secrets.getValue(id).copyOf()
                    }, { active.ensureActive() })
                    val previous = entries[entry.id]
                    if (previous == null) entries[entry.id] = entry
                    else try { require(previous.coversSameCiphertext(entry)) { "Identificador duplicado con contenido distinto." } }
                    finally { entry.close() }
                }
                progress(index + 1, batch.headers.size)
            }
            val encoded = codec.seal(entries.values.toList(), password, batch.headers.values.first().recipient) { active.ensureActive() }
            try {
                codec.open(encoded, password) { active.ensureActive() }.use { require(it.ids().toSet() == entries.keys) }
                active.ensureActive()
                app.contentResolver.openOutputStream(destination, "wt")?.use { it.write(encoded); it.flush() }
                    ?: error("No se pudo guardar la protección completa.")
                active.ensureActive()
            } finally { encoded.fill(0) }
            return batch.headers.size
        } finally { entries.values.forEach { it.close() } }
    }

    suspend fun restoreAll(store: VaultFileStore, packageUri: Uri, password: CharArray,
                           destination: Uri, progress: (Int, Int) -> Unit): Int {
        val active = currentCoroutineContext()
        val items = store.list()
        require(items.size in 1..1000) { "Importa primero el respaldo de tu bóveda. Se admiten hasta 1000 archivos." }
        require(items.sumOf { it.encryptedBytes } <= VaultBackup.MAX_TOTAL_BYTES)
        val encoded = ByteArrayOutputStream().also { output ->
            app.contentResolver.openInputStream(packageUri)?.use {
                VaultContainer.copyBounded(it, output, { active.ensureActive() }, RecoveryPackageCodec.MAX_BYTES.toLong())
            } ?: error("No se pudo leer la protección guardada.")
        }.toByteArray()
        val zipFile = File.createTempFile("recovery-whole-", ".plain", staging)
        try {
            codec.open(encoded, password) { active.ensureActive() }.use { pkg ->
                // All current objects, including trash, must be covered; never report a partial vault as recovered.
                items.forEach { pkg.entry(store.readHeader(it.id).id) }
                val names = mutableSetOf<String>()
                FileOutputStream(zipFile).use { file ->
                    ZipOutputStream(file).use { zip ->
                        zip.setLevel(0)
                        items.forEachIndexed { index, item ->
                            active.ensureActive()
                            val entry = pkg.entry(store.readHeader(item.id).id)
                            store.exportEncrypted(item.id) { input ->
                                engine.restore(input, entry, staging, { verified, metadata ->
                                    require(metadata.name != "." && metadata.name != "..") { "Nombre de archivo no válido para exportar." }
                                    var name = metadata.name; var suffix = 1
                                    while (!names.add(name)) { name = "${suffix++}_${metadata.name}" }
                                    zip.putNextEntry(ZipEntry(name))
                                    VaultContainer.copyBounded(verified, zip, { active.ensureActive() })
                                    zip.closeEntry()
                                }, { active.ensureActive() })
                            }
                            progress(index + 1, items.size)
                        }
                    }
                }
                // Nothing is delivered externally until the complete ZIP has authenticated sources.
                active.ensureActive()
                app.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                    FileInputStream(zipFile).use { input ->
                        VaultContainer.copyBounded(input, output, { active.ensureActive() }, VaultBackup.MAX_TOTAL_BYTES + 10485760)
                    }; output.flush()
                } ?: error("No se pudo guardar el ZIP recuperado.")
                active.ensureActive()
            }
            return items.size
        } finally { encoded.fill(0); check(zipFile.delete() || !zipFile.exists()) }
    }

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
