package pe.gob.reniec.dnie.passkeys

class PasskeyOptionFailure(val rule: String) : IllegalArgumentException(rule)
internal fun option(condition: Boolean, rule: String) {
    if (!condition) {
        pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "option_rejected", "rule" to rule)
        throw PasskeyOptionFailure(rule)
    }
}

/** Only shape and whitelisted option values; never a serialized WebAuthn request. */
object PasskeyRequestDiagnostics {
    fun capture(source: String) {
        val log = pe.gob.reniec.dnie.diagnostics.DiagnosticLog
        try {
            val obj = PasskeyJson.parse(source)
            fun field(parent: com.google.gson.JsonObject?, name: String, allowed: Set<String>): String {
                val value = parent?.get(name) ?: return "absent"
                return if (value.isJsonPrimitive && value.asString in allowed) value.asString else "invalid"
            }
            fun bytes(parent: com.google.gson.JsonObject?, name: String) = parent?.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.toByteArray()?.size ?: -1
            fun objectAt(name: String) = obj.get(name)?.takeIf { it.isJsonObject }?.asJsonObject
            val selection = objectAt("authenticatorSelection")
            val algorithms = obj.get("pubKeyCredParams")?.takeIf { it.isJsonArray }?.asJsonArray?.take(16)?.map { entry ->
                runCatching { entry.asJsonObject["alg"].asBigDecimal.intValueExact().toString() }.getOrDefault("invalid")
            }?.joinToString(",") ?: "absent"
            val extensions = objectAt("extensions")?.keySet().orEmpty()
            val known = setOf("credProps", "prf", "largeBlob", "hmacCreateSecret", "uvm", "credProtect", "credentialProtectionPolicy", "enforceCredentialProtectionPolicy", "payment", "appid", "appidExclude", "minPinLength")
            log.event("passkeys", "request_shape", "algorithm" to algorithms,
                "attachment" to field(selection, "authenticatorAttachment", setOf("platform", "cross-platform")),
                "residentKey" to field(selection, "residentKey", setOf("required", "preferred", "discouraged")),
                "verification" to field(selection ?: obj, "userVerification", setOf("required", "preferred", "discouraged")),
                "attestation" to field(obj, "attestation", setOf("none", "direct", "indirect", "enterprise")),
                "challengeBytes" to bytes(obj, "challenge"), "nameBytes" to bytes(objectAt("user"), "name"),
                "displayNameBytes" to bytes(objectAt("user"), "displayName"), "userIdBytes" to bytes(objectAt("user"), "id"),
                "extensions" to extensions.filter { it in known }.sorted().joinToString(","),
                "unknownExtensions" to extensions.count { it !in known })
        } catch (failure: Exception) { log.failure("passkeys", "request_shape", failure) }
    }
}
