package pe.gob.reniec.dnie.passkeys

import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.*

class PasskeyEngine(private val random: SecureRandom = SecureRandom(), private val backupEligible: Boolean = true) {
    fun create(options: PasskeyCreateOptions, caller: PasskeyCaller, catalog: PasskeyCatalog): Pair<PasskeyRecord, String> {
        require(catalog.records.size < PasskeyCatalog.MAX_RECORDS)
        require(catalog.records.none { it.rpId == options.rpId && it.encodedId in options.excluded }) { "La credencial ya está registrada" }
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()
        val id = ByteArray(32).also(random::nextBytes)
        val record = PasskeyRecord(id, options.rpId, options.userId.copyOf(), options.username, options.displayName, pair.private.encoded, pair.public.encoded, backupEligible = backupEligible)
        try {
            val auth = authData(options.rpId, 0x45 or (if (backupEligible) 0x08 else 0)) + ByteArray(16) + byteArrayOf(0, 32) + id + cose(pair.public as ECPublicKey)
            val attestation = ByteArrayOutputStream().also { out ->
                out.write(0xa3); cborText(out, "fmt"); cborText(out, "none")
                cborText(out, "attStmt"); out.write(0xa0)
                cborText(out, "authData"); cborBytes(out, auth)
            }.toByteArray()
            val response = JsonObject().apply {
                addProperty("clientDataJSON", PasskeyEncoding.encode(clientData("webauthn.create", options.challenge, caller)))
                addProperty("attestationObject", PasskeyEncoding.encode(attestation))
                add("transports", PasskeyJson.gson.toJsonTree(listOf("internal")))
                addProperty("publicKeyAlgorithm", -7); addProperty("publicKey", PasskeyEncoding.encode(pair.public.encoded))
                addProperty("authenticatorData", PasskeyEncoding.encode(auth))
            }
            val extensions = JsonObject()
            if (options.credProps) extensions.add("credProps", JsonObject().apply { addProperty("rk", true) })
            return record to credential(id, response, extensions)
        } catch (failure: Throwable) { record.destroy(); throw failure }
    }
    fun authenticate(options: PasskeyGetOptions, caller: PasskeyCaller, record: PasskeyRecord): String {
        require(record.rpId == options.rpId && (options.allowed.isEmpty() || record.encodedId in options.allowed)) { "Credencial fuera de alcance" }
        val client = clientData("webauthn.get", requireNotNull(options.challenge), caller)
        val auth = authData(options.rpId, 0x05 or (if (record.backupEligible) 0x08 else 0) or (if (record.backedUp) 0x10 else 0))
        val hash = caller.clientDataHash ?: sha(client)
        val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(record.privateKey))
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey, random); update(auth); update(hash)
        }.sign()
        val response = JsonObject().apply {
            addProperty("clientDataJSON", PasskeyEncoding.encode(client)); addProperty("authenticatorData", PasskeyEncoding.encode(auth))
            addProperty("signature", PasskeyEncoding.encode(signature)); addProperty("userHandle", PasskeyEncoding.encode(record.userId))
        }
        return credential(record.id, response, JsonObject())
    }
    private fun clientData(type: String, challenge: String, caller: PasskeyCaller): ByteArray =
        if (caller.clientDataHash != null) "{}".toByteArray() else JsonObject().apply {
            addProperty("type", type); addProperty("challenge", challenge); addProperty("origin", caller.origin); addProperty("crossOrigin", false)
        }.toString().toByteArray(Charsets.UTF_8)
    private fun credential(id: ByteArray, response: JsonObject, extensions: JsonObject): String = JsonObject().apply {
        addProperty("id", PasskeyEncoding.encode(id)); addProperty("rawId", PasskeyEncoding.encode(id))
        addProperty("type", "public-key"); addProperty("authenticatorAttachment", "platform")
        add("response", response); add("clientExtensionResults", extensions)
    }.toString()
    private fun authData(rp: String, flags: Int): ByteArray = sha(rp.toByteArray(Charsets.UTF_8)) + byteArrayOf(flags.toByte(), 0, 0, 0, 0)
    private fun cose(key: ECPublicKey): ByteArray = ByteArrayOutputStream().also { out ->
        out.write(byteArrayOf(0xa5.toByte(), 1, 2, 3, 0x26, 0x20, 1, 0x21))
        cborBytes(out, coordinate(key.w.affineX.toByteArray()))
        out.write(0x22); cborBytes(out, coordinate(key.w.affineY.toByteArray()))
    }.toByteArray()
    private fun coordinate(bytes: ByteArray): ByteArray = bytes.takeLast(32).toByteArray().let { ByteArray(32 - it.size) + it }
    private fun cborText(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(); length(out, 3, bytes.size); out.write(bytes)
    }
    private fun cborBytes(out: ByteArrayOutputStream, bytes: ByteArray) { length(out, 2, bytes.size); out.write(bytes) }
    private fun length(out: ByteArrayOutputStream, type: Int, size: Int) {
        when { size < 24 -> out.write(type * 32 + size)
            size < 256 -> { out.write(type * 32 + 24); out.write(size) }
            else -> { out.write(type * 32 + 25); out.write(size shr 8); out.write(size and 255) }
        }
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
}
