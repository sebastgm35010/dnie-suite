package pe.gob.reniec.dnie.passkeys

/** Exact package/current signer and the credential delegation relation are all mandatory. */
object PasskeyDigitalAssetLinks {
    fun authorizes(json: String, packageName: String, fingerprint: String): Boolean {
        val expected = fingerprint.uppercase(java.util.Locale.ROOT)
        require(expected.matches(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){31}")))
        return PasskeyJson.statements(json).any { entry ->
            if (!entry.isJsonObject) return@any false
            val statement = entry.asJsonObject
            val target = statement.get("target")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@any false
            val relations = statement.get("relation")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@any false
            val fingerprints = target.get("sha256_cert_fingerprints")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@any false
            relations.any { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString == "delegate_permission/common.get_login_creds" } &&
                target.get("namespace")?.asString == "android_app" && target.get("package_name")?.asString == packageName &&
                fingerprints.any { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.uppercase(java.util.Locale.ROOT) == expected }
        }
    }
}
