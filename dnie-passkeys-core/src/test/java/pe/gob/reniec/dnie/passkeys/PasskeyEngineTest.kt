package pe.gob.reniec.dnie.passkeys

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.*
import java.security.spec.X509EncodedKeySpec

class PasskeyEngineTest {
    private val challenge = PasskeyEncoding.encode(ByteArray(32) { it.toByte() })
    private val caller = PasskeyCaller("android:apk-key-hash:${PasskeyEncoding.encode(ByteArray(32) { 2 })}")
    private fun options(rp: String = "example.com") = PasskeyCreateOptions.parse("""{"rp":{"id":"$rp","name":"Example"},"user":{"id":"AQID","name":"alice","displayName":"Alice"},"challenge":"$challenge","pubKeyCredParams":[{"type":"public-key","alg":-7}],"authenticatorSelection":{"residentKey":"required","userVerification":"required","authenticatorAttachment":"platform"},"extensions":{"credProps":true}}""")
    private fun get(rp: String = "example.com", ids: List<String> = emptyList()) = PasskeyGetOptions(rp, ids, challenge)
    private fun rejects(block: () -> Unit) { try { block(); fail("Unexpected success") } catch (_: Exception) {} }

    @Test fun signatureVerifiedIndependentlyUsingStoredPublicKey() {
        PasskeyCatalog().use { catalog ->
            val (record, _) = PasskeyEngine().create(options(), caller, catalog); catalog.records.add(record)
            val response = PasskeyJson.parse(PasskeyEngine().authenticate(get(), caller, record)).getAsJsonObject("response")
            val auth = PasskeyEncoding.decode(response["authenticatorData"].asString, 128)
            val client = PasskeyEncoding.decode(response["clientDataJSON"].asString, 2048)
            assertArrayEquals(MessageDigest.getInstance("SHA-256").digest("example.com".toByteArray()), auth.copyOfRange(0, 32))
            assertEquals(13, auth[32].toInt()); assertArrayEquals(ByteArray(4), auth.copyOfRange(33, 37))
            val signature = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(record.publicKey)))
                update(auth); update(MessageDigest.getInstance("SHA-256").digest(client))
            }
            assertTrue(signature.verify(PasskeyEncoding.decode(response["signature"].asString, 256)))
            assertEquals("AQID", response["userHandle"].asString)
        }
    }
    @Test fun browserDelegatedHashIsSignedExactly() {
        PasskeyCatalog().use { catalog ->
            val hash = ByteArray(32) { 7 }
            val browser = PasskeyCaller("https://related.example.org", hash)
            val (record, _) = PasskeyEngine().create(options(), browser, catalog); catalog.records.add(record)
            val response = PasskeyJson.parse(PasskeyEngine().authenticate(get(), browser, record)).getAsJsonObject("response")
            assertEquals("{}", String(PasskeyEncoding.decode(response["clientDataJSON"].asString, 1024)))
            val verifier = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(record.publicKey)))
                update(PasskeyEncoding.decode(response["authenticatorData"].asString, 128)); update(hash)
            }
            assertTrue(verifier.verify(PasskeyEncoding.decode(response["signature"].asString, 256)))
        }
    }
    @Test fun rpAndAllowlistPreventCredentialReuse() {
        PasskeyCatalog().use { catalog ->
            val (record, _) = PasskeyEngine().create(options(), caller, catalog); catalog.records.add(record)
            rejects { PasskeyEngine().authenticate(get("other.example.com"), caller, record) }
            rejects { PasskeyEngine().authenticate(get(ids = listOf("unrelated")), caller, record) }
            assertTrue(catalog.matching(get("other.example.com")).isEmpty())
            assertEquals(1, catalog.matching(get(ids = listOf(record.encodedId))).size)
        }
    }
    @Test fun excludedCredentialRejectedOnlyForItsRp() {
        PasskeyCatalog().use { catalog ->
            val (record, _) = PasskeyEngine().create(options(), caller, catalog); catalog.records.add(record)
            rejects { PasskeyEngine().create(options().copy(excluded = listOf(record.encodedId)), caller, catalog) }
            val (other, _) = PasskeyEngine().create(options("other.example.com").copy(excluded = listOf(record.encodedId)), caller, catalog)
            other.destroy()
        }
    }
    @Test fun everyCredentialHasIndependentKeyAndOpaqueIdentifier() {
        PasskeyCatalog().use { catalog ->
            val first = PasskeyEngine().create(options(), caller, catalog).first
            val second = PasskeyEngine().create(options(), caller, catalog).first
            catalog.records.addAll(listOf(first, second))
            assertFalse(first.publicKey.contentEquals(second.publicKey)); assertFalse(first.id.contentEquals(second.id))
        }
    }
    @Test fun catalogRoundTripAndZeroizesPrivateKeysOnClose() {
        val catalog = PasskeyCatalog()
        val record = PasskeyEngine().create(options(), caller, catalog).first; catalog.records.add(record)
        val serialized = catalog.encode()
        PasskeyCatalog.decode(serialized).use { restored -> assertArrayEquals(record.privateKey, restored.records.single().privateKey) }
        serialized.fill(0); catalog.close()
        assertTrue(record.privateKey.all { it == 0.toByte() }); assertTrue(catalog.records.isEmpty())
    }
    @Test fun catalogRejectsTrailingAndDuplicateRecords() {
        PasskeyCatalog().use { catalog ->
            val record = PasskeyEngine().create(options(), caller, catalog).first; catalog.records.add(record)
            rejects { PasskeyCatalog.decode(catalog.encode() + 0) }
            catalog.records.add(record)
            rejects { PasskeyCatalog.decode(catalog.encode()) }
        }
    }
    @Test fun catalogRejectsUnboundedRecordCount() {
        rejects { PasskeyCatalog.decode(byteArrayOf(0,0,0,1,0,0,4,1)) }
        rejects { PasskeyCatalog.decode(ByteArray(PasskeyCatalog.MAX_BYTES + 1)) }
    }
    @Test fun strictRequestsRejectDuplicateFieldsAndTrailingInput() {
        rejects { PasskeyJson.parse("""{"rpId":"example.com","rpId":"evil.com"}""") }
        rejects { PasskeyJson.parse("{} {}") }
    }
    @Test fun strictRequestRejectsWrongAlgorithm() {
        rejects { PasskeyCreateOptions.parse(optionsJson().replace("-7", "-7.1")) }
    }
    @Test fun optionalExtensionsDoNotPreventRegistrationAndDoNotFabricateOutputs() {
        val json = PasskeyJson.parse(optionsJson()).apply {
            add("extensions", PasskeyJson.parse("""{"prf":{},"largeBlob":{"support":"preferred"},"hmacCreateSecret":true,"credProps":true}"""))
        }.toString()
        val options = PasskeyCreateOptions.parse(json)
        PasskeyCatalog().use { catalog ->
            val (record, response) = PasskeyEngine().create(options, caller, catalog); catalog.records.add(record)
            val outputs = PasskeyJson.parse(response).getAsJsonObject("clientExtensionResults")
            assertEquals(setOf("credProps"), outputs.keySet())
            assertTrue(outputs.getAsJsonObject("credProps")["rk"].asBoolean)
        }
    }
    @Test fun unsupportedRequiredPoliciesStillFail() {
        for (extension in listOf("""{"largeBlob":{"support":"required"}}""",
            """{"enforceCredentialProtectionPolicy":true,"credentialProtectionPolicy":"userVerificationRequired"}""",
            """{"payment":{"isPayment":true}}""")) {
            rejects { PasskeyCreateOptions.parse(PasskeyJson.parse(optionsJson()).apply { add("extensions", PasskeyJson.parse(extension)) }.toString()) }
        }
    }
    @Test fun unknownAuthenticationExtensionsAreIgnoredAndRpScopeStillEnforced() {
        val options = PasskeyGetOptions.parse("""{"rpId":"example.com","challenge":"$challenge","extensions":{"prf":{},"uvm":true}}""")
        PasskeyCatalog().use { catalog ->
            val record = PasskeyEngine().create(options(), caller, catalog).first; catalog.records.add(record)
            val response = PasskeyJson.parse(PasskeyEngine().authenticate(options, caller, record))
            assertTrue(response.getAsJsonObject("clientExtensionResults").keySet().isEmpty())
            rejects { PasskeyEngine().authenticate(options.copy(rpId = "evil.example.com"), caller, record) }
        }
    }
    @Test fun invalidCredPropsDoesNotReportItAsSupported() {
        val options = PasskeyCreateOptions.parse(PasskeyJson.parse(optionsJson()).apply {
            add("extensions", PasskeyJson.parse("""{"credProps":"true"}"""))
        }.toString())
        assertFalse(options.credProps)
    }
    private fun optionsJson() = """{"rp":{"id":"example.com"},"user":{"id":"AQID","name":"alice","displayName":"Alice"},"challenge":"$challenge","pubKeyCredParams":[{"type":"public-key","alg":-7}]}"""
    @Test fun rejectsPublicSuffixIpAndNoncanonicalRp() {
        listOf("com", "co.uk", "127.0.0.1", "Example.com", "example.com.").forEach { rejects { PasskeyJson.rpId(it) } }
    }
    @Test fun rejectsInvalidChallengesAndNoncanonicalBase64() {
        rejects { PasskeyEncoding.decode("AQ==", 32) }; rejects { PasskeyEncoding.decode("AR", 32) }
        rejects { PasskeyCreateOptions.parse(optionsJson().replace(challenge, "AQID")) }
        rejects { PasskeyGetOptions.parse("""{"rpId":"example.com"}""") }
    }
    @Test fun callerRejectsUnverifiedWebHashesAndNativeDelegation() {
        rejects { PasskeyCaller("https://example.com") }
        rejects { PasskeyCaller("https://example.com/path", ByteArray(32)) }
        rejects { PasskeyCaller(caller.origin, ByteArray(32)) }
    }
    @Test fun browserRootSlashPreservesDelegatedHashAndDoesNotAllowPaths() {
        val hash = ByteArray(32) { 4 }
        val caller = PasskeyCaller("https://example.com/", hash)
        assertArrayEquals(hash, caller.clientDataHash)
        rejects { PasskeyCaller("https://example.com/path", hash) }
        rejects { PasskeyCaller("https://example.com/?query", hash) }
    }
    @Test fun unsupportedAttachmentRejected() {
        rejects { PasskeyCreateOptions.parse(optionsJson().dropLast(1) + """, "authenticatorSelection":{"authenticatorAttachment":"cross-platform"}}""") }
    }
    @Test fun generatesPublicFixturesForIndependentWebauthnVerifier() {
        val fixtures = com.google.gson.JsonArray()
        for (web in listOf(false, true)) for ((eligible, backedUp) in listOf(false to false, true to false, true to true)) {
            PasskeyCatalog().use { catalog ->
                val origin = if (web) "https://example.com" else caller.origin
                fun client(type: String) = """{"type":"$type","challenge":"$challenge","origin":"$origin","crossOrigin":false}""".toByteArray()
                val createCaller = if (web) PasskeyCaller(origin, MessageDigest.getInstance("SHA-256").digest(client("webauthn.create"))) else caller
                val getCaller = if (web) PasskeyCaller(origin, MessageDigest.getInstance("SHA-256").digest(client("webauthn.get"))) else caller
                val (record, registration) = PasskeyEngine(backupEligible = eligible).create(options(), createCaller, catalog); catalog.records.add(record)
                record.backedUp = backedUp
                val authentication = PasskeyEngine().authenticate(get(), getCaller, record)
                val reg = PasskeyJson.parse(registration); val auth = PasskeyJson.parse(authentication)
                if (web) {
                    // Browser replaces the placeholder with its own clientDataJSON; provider signs its exact hash.
                    reg.getAsJsonObject("response").addProperty("clientDataJSON", PasskeyEncoding.encode(client("webauthn.create")))
                    auth.getAsJsonObject("response").addProperty("clientDataJSON", PasskeyEncoding.encode(client("webauthn.get")))
                }
                fixtures.add(com.google.gson.JsonObject().apply {
                    addProperty("origin", origin); addProperty("challenge", challenge); addProperty("rpId", "example.com")
                    addProperty("backupEligible", eligible)
                    addProperty("backedUp", backedUp)
                    add("registration", reg); add("authentication", auth)
                })
            }
        }
        File("build/webauthn-fixtures.json").apply { parentFile.mkdirs(); writeText(fixtures.toString()) }
    }
}
