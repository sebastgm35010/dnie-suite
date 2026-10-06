package pe.gob.reniec.dnie.passkeys

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class PasskeyLongChallengeTest {
    private fun create(value: String) = PasskeyCreateOptions.parse("""{"rp":{"id":"example.com"},"user":{"id":"AQID","name":"test","displayName":"Test"},"challenge":"$value","pubKeyCredParams":[{"type":"public-key","alg":-7}],"authenticatorSelection":{"authenticatorAttachment":"platform","residentKey":"required","userVerification":"required"},"attestation":"none","extensions":{"prf":{}}}""")
    private fun get(value: String) = PasskeyGetOptions.parse("""{"rpId":"example.com","challenge":"$value"}""")
    private fun challenge(size: Int) = PasskeyEncoding.encode(ByteArray(size) { (it * 31).toByte() })
    private fun rejectBoth(value: String) {
        listOf<() -> Any>({ create(value) }, { get(value) }).forEach { parse ->
            try { parse(); fail("Invalid challenge accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun acceptsObservedEncodedLengthInBothCeremonies() {
        val value = challenge(1461)
        assertEquals(1948, value.length)
        assertEquals(value, create(value).challenge)
        assertEquals(value, get(value).challenge)
    }
    @Test fun retainsBoundedMaximumForBothCeremonies() {
        val value = challenge(PasskeyJson.MAX_CHALLENGE_BYTES)
        assertEquals(value, create(value).challenge)
        assertEquals(value, get(value).challenge)
        rejectBoth(challenge(PasskeyJson.MAX_CHALLENGE_BYTES + 1))
    }
    @Test fun rejectsShortChallenges() { rejectBoth(challenge(15)) }
    @Test fun rejectsMalformedAndNoncanonicalChallenges() {
        val value = challenge(1462)
        rejectBoth(value + "=")
        rejectBoth("!" + value.drop(1))
        // Last base64url symbol must have zero unused bits.
        rejectBoth(value.dropLast(1) + "B")
    }
    @Test fun longChallengeSurvivesClientDataAndSignatureVerification() {
        val value = challenge(1461)
        val caller = PasskeyCaller("android:apk-key-hash:${PasskeyEncoding.encode(ByteArray(32) { 2 })}")
        PasskeyCatalog().use { catalog ->
            val (record, creation) = PasskeyEngine().create(create(value), caller, catalog)
            catalog.records.add(record)
            fun response(json: String) = PasskeyJson.parse(json).getAsJsonObject("response")
            fun client(json: String) = PasskeyEncoding.decode(response(json)["clientDataJSON"].asString, 16384)
            assertEquals(value, PasskeyJson.parse(String(client(creation)))["challenge"].asString)
            val assertion = PasskeyEngine().authenticate(get(value), caller, record)
            val bytes = client(assertion)
            assertEquals(value, PasskeyJson.parse(String(bytes))["challenge"].asString)
            val verifier = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(record.publicKey)))
                update(PasskeyEncoding.decode(response(assertion)["authenticatorData"].asString, 128))
                update(MessageDigest.getInstance("SHA-256").digest(bytes))
            }
            assertTrue(verifier.verify(PasskeyEncoding.decode(response(assertion)["signature"].asString, 256)))
        }
    }
}
