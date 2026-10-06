package pe.gob.reniec.dnie.passkeys

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.common.net.InternetDomainName
import java.io.StringReader
import java.net.IDN
import java.net.URI
import java.util.Base64

object PasskeyEncoding {
    fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun decode(value: String, maximum: Int): ByteArray {
        option(value.length <= maximum * 2 && value.matches(Regex("[A-Za-z0-9_-]+")), "BASE64URL_FORMAT")
        return Base64.getUrlDecoder().decode(value).also {
            option(it.size in 1..maximum && encode(it) == value, "BASE64URL_CANONICAL")
        }
    }
}

object PasskeyJson {
    // Local resource bound, not a WebAuthn challenge-size requirement. Some RPs use long opaque challenges.
    const val MAX_CHALLENGE_BYTES = 8192
    fun challenge(obj: JsonObject): String {
        val value = text(obj, "challenge", (MAX_CHALLENGE_BYTES * 4 + 2) / 3)
        option(PasskeyEncoding.decode(value, MAX_CHALLENGE_BYTES).size >= 16, "CHALLENGE_LENGTH")
        return value
    }
    val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    fun parse(source: String): JsonObject {
        require(source.length in 2..65536)
        return document(source).also { require(it.isJsonObject) }.asJsonObject
    }
    fun statements(source: String): JsonArray {
        require(source.length in 2..524288)
        return document(source).also { require(it.isJsonArray) }.asJsonArray
    }
    private fun document(source: String): JsonElement {
        JsonReader(StringReader(source)).use { reader ->
            reader.strictness = Strictness.STRICT
            val value = read(reader, 0)
            require(reader.peek() == JsonToken.END_DOCUMENT)
            return value
        }
    }
    private fun read(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= 16)
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().also { obj ->
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName(); require(!obj.has(name)) { "Campo JSON duplicado" }
                    obj.add(name, read(reader, depth + 1))
                }; reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().also { array ->
                reader.beginArray()
                while (reader.hasNext()) { require(array.size() < 1024); array.add(read(reader, depth + 1)) }
                reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> JsonPrimitive(reader.nextString().toBigDecimal())
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> error("JSON inválido")
        }
    }
    fun text(obj: JsonObject, field: String, max: Int): String {
        val value = obj.get(field)
        option(value?.isJsonPrimitive == true && value.asJsonPrimitive.isString, "FIELD_${field}_TYPE")
        return value.asString.also { option(it.toByteArray().size in 1..max && it.none(Char::isISOControl), "FIELD_${field}_LENGTH") }
    }
    fun rpId(value: String): String {
        require(value.length <= 253 && value == IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).lowercase(java.util.Locale.ROOT))
        require(!value.endsWith('.') && InternetDomainName.from(value).isUnderPublicSuffix) { "RP ID inválido" }
        return value
    }
    fun ids(obj: JsonObject, name: String): List<String> {
        if (!obj.has(name)) return emptyList()
        val array = obj.getAsJsonArray(name)
        require(array.size() <= 128)
        return array.map { item ->
            val descriptor = item.asJsonObject
            require(text(descriptor, "type", 32) == "public-key")
            PasskeyEncoding.encode(PasskeyEncoding.decode(text(descriptor, "id", 2048), 1024))
        }
    }
    fun extensions(obj: JsonObject) {
        if (!obj.has("extensions")) return
        val extensions = obj.getAsJsonObject("extensions")
        // WebAuthn extensions are optional. Ignore unsupported inputs without fabricating outputs.
        // Explicit requirements must still fail rather than silently weakening the requested policy.
        val largeBlob = extensions.get("largeBlob")?.takeIf { it.isJsonObject }?.asJsonObject
        option(largeBlob?.get("support")?.asString != "required", "LARGE_BLOB_REQUIRED")
        option(extensions.get("enforceCredentialProtectionPolicy")?.asBoolean != true, "CRED_PROTECT_REQUIRED")
        val payment = extensions.get("payment")?.takeIf { it.isJsonObject }?.asJsonObject
        option(payment?.get("isPayment")?.asBoolean != true, "PAYMENT_UNSUPPORTED")
    }
}

data class PasskeyCreateOptions(val rpId: String, val userId: ByteArray, val username: String,
    val displayName: String, val challenge: String, val excluded: List<String>, val credProps: Boolean) {
    companion object {
        fun parse(json: String): PasskeyCreateOptions {
            val obj = PasskeyJson.parse(json)
            PasskeyJson.extensions(obj)
            val rp = obj.getAsJsonObject("rp")
            val user = obj.getAsJsonObject("user")
            val parameters = obj.getAsJsonArray("pubKeyCredParams")
            option(parameters.any { it.asJsonObject["type"].asString == "public-key" &&
                it.asJsonObject["alg"].isJsonPrimitive && it.asJsonObject["alg"].asJsonPrimitive.isNumber &&
                it.asJsonObject["alg"].asBigDecimal.compareTo((-7).toBigDecimal()) == 0 }, "ES256_NOT_OFFERED")
            val selection = obj.getAsJsonObject("authenticatorSelection")
            option(selection?.get("authenticatorAttachment")?.asString.let { it == null || it == "platform" }, "ATTACHMENT_UNSUPPORTED")
            selection?.get("residentKey")?.let { require(it.asString in listOf("required", "preferred", "discouraged")) }
            selection?.get("userVerification")?.let { require(it.asString in listOf("required", "preferred", "discouraged")) }
            val attestation = obj.get("attestation")?.asString ?: "none"
            option(attestation == "none" || attestation == "indirect" || attestation == "direct", "ATTESTATION_UNSUPPORTED")
            val challenge = PasskeyJson.challenge(obj)
            return PasskeyCreateOptions(PasskeyJson.rpId(PasskeyJson.text(rp, "id", 253)),
                PasskeyEncoding.decode(PasskeyJson.text(user, "id", 128), 64),
                PasskeyJson.text(user, "name", 256), PasskeyJson.text(user, "displayName", 256), challenge,
                PasskeyJson.ids(obj, "excludeCredentials"), obj.getAsJsonObject("extensions")?.get("credProps")?.let {
                    it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean
                } == true)
        }
    }
}

data class PasskeyGetOptions(val rpId: String, val allowed: List<String>, val challenge: String?) {
    companion object {
        fun parse(json: String, requireChallenge: Boolean = true): PasskeyGetOptions {
            val obj = PasskeyJson.parse(json); PasskeyJson.extensions(obj)
            obj.get("userVerification")?.let { require(it.asString in listOf("required", "preferred", "discouraged")) }
            val challenge = obj.get("challenge")?.let { PasskeyJson.challenge(obj) }
            require(!requireChallenge || challenge != null)
            return PasskeyGetOptions(PasskeyJson.rpId(PasskeyJson.text(obj, "rpId", 253)), PasskeyJson.ids(obj, "allowCredentials"), challenge)
        }
    }
}

/** The platform adapter must establish native DAL or a privileged browser before constructing this. */
data class PasskeyCaller(val origin: String, val clientDataHash: ByteArray? = null) {
    init {
        if (origin.startsWith("android:apk-key-hash:")) {
            require(clientDataHash == null)
            require(PasskeyEncoding.decode(origin.removePrefix("android:apk-key-hash:"), 32).size == 32)
        } else {
            val uri = URI(origin)
            require(uri.scheme == "https" && uri.host != null && uri.userInfo == null &&
                (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") && uri.rawQuery == null && uri.rawFragment == null)
            require(clientDataHash?.size == 32) { "El navegador debe aportar clientDataHash" }
        }
    }
}
