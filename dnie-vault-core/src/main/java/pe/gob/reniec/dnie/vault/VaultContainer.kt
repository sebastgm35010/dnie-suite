package pe.gob.reniec.dnie.vault

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.proto.AesGcmHkdfStreamingKey
import com.google.crypto.tink.proto.HashType
import com.google.crypto.tink.proto.Keyset
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class VaultMetadata(val name: String, val mime: String)

/** Header is untrusted until its wrapped keyset and the entire stream authenticate. */
class VaultHeader internal constructor(
    val id: UUID,
    val recipient: ByteArray,
    val envelope: ByteArray,
    internal val nonce: ByteArray,
    internal val encryptedKeyset: ByteArray
) {
    internal fun contentAad(): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).apply {
            write(MAGIC); writeInt(1); writeInt(1); writeLong(id.mostSignificantBits); writeLong(id.leastSignificantBits)
        }
    }.toByteArray()
    internal fun envelopeAad() = contentAad() + recipient + envelope + nonce
    internal fun writeTo(output: OutputStream) {
        DataOutputStream(output).apply {
            write(contentAad()); write(recipient); write(envelope); write(nonce)
            writeInt(encryptedKeyset.size); write(encryptedKeyset)
        }
    }
    override fun toString() = "VaultHeader(version=1, secrets=redacted)"
    companion object {
        internal val MAGIC = "DNIEVLT1".toByteArray(Charsets.US_ASCII)
    }
}

/** Version 1 preview container: RSA-v1.5 envelope + AEAD keyset + Tink Streaming AEAD.
 * Callers must stage authenticated plaintext privately and publish only after EOF succeeds.
 */
class VaultContainer(private val random: SecureRandom = SecureRandom()) {
    init { StreamingAeadConfig.register() }

    fun readHeader(input: InputStream): VaultHeader {
        val data = DataInputStream(input)
        val magic = ByteArray(8).also(data::readFully)
        require(magic.contentEquals(VaultHeader.MAGIC) && data.readInt() == 1 && data.readInt() == 1) {
            "Formato o versión de bóveda no compatible"
        }
        val id = UUID(data.readLong(), data.readLong())
        val recipient = ByteArray(32).also(data::readFully)
        val envelope = ByteArray(256).also(data::readFully)
        val nonce = ByteArray(12).also(data::readFully)
        val size = data.readInt()
        require(size in 17..8192) { "Cabecera de bóveda inválida" }
        return VaultHeader(id, recipient, envelope, nonce, ByteArray(size).also(data::readFully))
    }

    fun seal(input: InputStream, output: OutputStream, key: RSAPublicKey, metadata: VaultMetadata,
             checkCancelled: () -> Unit = {}): Long {
        require(key.modulus.bitLength() == 2048) { "Se requiere la pública de cifrado RSA-2048" }
        validateMetadata(metadata)
        val secret = ByteArray(32).also(random::nextBytes)
        var serialized: ByteArray? = null
        try {
            checkCancelled()
            val handle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
            val keyset = TinkProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get())
                .also { serialized = it }
            val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsa.init(Cipher.ENCRYPT_MODE, key, random)
            val header = VaultHeader(UUID.randomUUID(), MessageDigest.getInstance("SHA-256").digest(key.encoded),
                rsa.doFinal(secret), ByteArray(12).also(random::nextBytes), byteArrayOf())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, header.nonce))
            cipher.updateAAD(header.envelopeAad())
            val encrypted = cipher.doFinal(keyset)
            val complete = VaultHeader(header.id, header.recipient, header.envelope, header.nonce, encrypted)
            complete.writeTo(output)
            val stream = handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
                .newEncryptingStream(NonClosingOutput(output), complete.contentAad())
            return stream.use {
                val prefix = DataOutputStream(it)
                writeString(prefix, metadata.name); writeString(prefix, metadata.mime)
                copyBounded(input, it, checkCancelled)
            }
        } finally {
            secret.fill(0); serialized?.fill(0)
        }
    }

    /** Input must remain positioned immediately after this header. Does not own the secret. */
    fun openPayload(input: InputStream, output: OutputStream, header: VaultHeader, secret: ByteArray,
                    checkCancelled: () -> Unit = {}): VaultMetadata {
        require(secret.size == 32) { "No se pudo abrir el archivo cifrado" }
        var serialized: ByteArray? = null
        try {
            checkCancelled()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, header.nonce))
            cipher.updateAAD(header.envelopeAad())
            val keyset = cipher.doFinal(header.encryptedKeyset).also { serialized = it }
            validateKeyset(keyset)
            val handle = TinkProtoKeysetFormat.parseKeyset(keyset, InsecureSecretKeyAccess.get())
            return handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
                .newDecryptingStream(input, header.contentAad()).use { stream ->
                    val data = DataInputStream(stream)
                    val metadata = VaultMetadata(readString(data, 1024), readString(data, 128))
                    validateMetadata(metadata)
                    copyBounded(data, output, checkCancelled)
                    metadata
                }
        } catch (e: java.security.GeneralSecurityException) {
            throw IOException("No se pudo autenticar el archivo cifrado", e)
        } finally { serialized?.fill(0) }
    }

    private fun validateKeyset(bytes: ByteArray) {
        val keyset = Keyset.parseFrom(bytes)
        require(keyset.keyCount == 1) { "Suite de cifrado no compatible" }
        val key = keyset.getKey(0)
        require(key.keyId == keyset.primaryKeyId && key.status.name == "ENABLED" && key.outputPrefixType.name == "RAW" &&
            key.keyData.typeUrl == "type.googleapis.com/google.crypto.tink.AesGcmHkdfStreamingKey") { "Suite de cifrado no compatible" }
        val streaming = AesGcmHkdfStreamingKey.parseFrom(key.keyData.value)
        require(streaming.version == 0 && streaming.keyValue.size() == 32 && streaming.params.derivedKeySize == 32 &&
            streaming.params.ciphertextSegmentSize == 1048576 && streaming.params.hkdfHashType == HashType.SHA256) {
            "Parámetros de cifrado no compatibles"
        }
    }

    private fun validateMetadata(metadata: VaultMetadata) {
        require(metadata.name.isNotBlank() && metadata.name.toByteArray(Charsets.UTF_8).size <= 1024 &&
            metadata.name.none { it == '/' || it == '\\' || it.isISOControl() }) { "Nombre de archivo inválido" }
        require(metadata.mime.length <= 128 && metadata.mime.matches(Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+"))) {
            "Tipo de archivo inválido"
        }
    }

    private fun writeString(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8); output.writeInt(bytes.size); output.write(bytes)
    }
    private fun readString(input: DataInputStream, max: Int): String {
        val size = input.readInt(); require(size in 1..max) { "Metadatos inválidos" }
        val bytes = ByteArray(size).also(input::readFully)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }
    private class NonClosingOutput(output: OutputStream) : FilterOutputStream(output) {
        override fun write(bytes: ByteArray, offset: Int, length: Int) { out.write(bytes, offset, length) }
        override fun close() { flush() }
    }

    companion object {
        const val MAX_FILE_BYTES = 1024L * 1024 * 1024
        /** Bounded memory, cancellation per chunk; callers also control disk quota. */
        fun copyBounded(input: InputStream, output: OutputStream, checkCancelled: () -> Unit = {},
                        limit: Long = MAX_FILE_BYTES): Long {
            val buffer = ByteArray(65536)
            var total = 0L
            var stalled = 0
            try {
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) return total
                    if (count == 0) {
                        if (++stalled > 8) throw IOException("El proveedor no devuelve datos")
                        continue
                    }
                    stalled = 0; total += count
                    require(total <= limit) { "El archivo supera el límite de esta versión (1 GiB)" }
                    output.write(buffer, 0, count)
                }
            } finally { buffer.fill(0) }
        }
    }
}
