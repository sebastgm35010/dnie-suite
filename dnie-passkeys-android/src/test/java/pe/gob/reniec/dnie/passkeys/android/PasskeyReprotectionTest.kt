package pe.gob.reniec.dnie.passkeys.android

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.passkeys.*
import pe.gob.reniec.dnie.vault.*
import java.io.*
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

class PasskeyReprotectionTest {
    private fun pair() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private fun seal(bytes: ByteArray, key: KeyPair): ByteArray = ByteArrayOutputStream().also {
        VaultContainer().seal(ByteArrayInputStream(bytes), it, key.public as RSAPublicKey,
            VaultMetadata("passkeys.bin", "application/x-dnie-passkeys"))
    }.toByteArray()
    private fun open(encrypted: ByteArray, key: KeyPair): ByteArray {
        val input = ByteArrayInputStream(encrypted); val engine = VaultContainer(); val header = engine.readHeader(input)
        val secret = Cipher.getInstance("RSA/ECB/PKCS1Padding").run { init(Cipher.DECRYPT_MODE, key.private); doFinal(header.envelope) }
        return try { ByteArrayOutputStream().also { engine.openPayload(input, it, header, secret) }.toByteArray() }
        finally { secret.fill(0) }
    }
    @Test fun newDniProtectionPreservesCredentialAndStillAuthenticatesOriginalPublicKey() {
        val oldDni = pair(); val newDni = pair()
        val challenge = PasskeyEncoding.encode(ByteArray(32) { 7 })
        val caller = PasskeyCaller("android:apk-key-hash:${PasskeyEncoding.encode(ByteArray(32) { 3 })}")
        PasskeyCatalog().use { catalog ->
            val options = PasskeyCreateOptions.parse("""{"rp":{"id":"example.com"},"user":{"id":"AQID","name":"test","displayName":"Test"},"challenge":"$challenge","pubKeyCredParams":[{"type":"public-key","alg":-7}]}""")
            val record = PasskeyEngine().create(options, caller, catalog).first; catalog.records.add(record)
            val original = catalog.encode()
            val oldBackup = seal(original, oldDni)
            val recovered = open(oldBackup, oldDni)
            val newBackup = seal(recovered, newDni)
            val migrated = open(newBackup, newDni)
            try {
                assertArrayEquals(original, migrated)
                PasskeyCatalog.decode(migrated).use { restored ->
                    val selected = restored.records.single()
                    assertArrayEquals(record.id, selected.id); assertArrayEquals(record.publicKey, selected.publicKey)
                    val response = PasskeyJson.parse(PasskeyEngine().authenticate(PasskeyGetOptions("example.com", listOf(record.encodedId), challenge), caller, selected)).getAsJsonObject("response")
                    val verifier = Signature.getInstance("SHA256withECDSA").apply {
                        initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(record.publicKey)))
                        update(PasskeyEncoding.decode(response["authenticatorData"].asString, 128))
                        update(MessageDigest.getInstance("SHA-256").digest(PasskeyEncoding.decode(response["clientDataJSON"].asString, 2048)))
                    }
                    assertTrue(verifier.verify(PasskeyEncoding.decode(response["signature"].asString, 256)))
                }
                try { open(newBackup, oldDni); fail("Old private key accepted new envelope") } catch (_: javax.crypto.BadPaddingException) {}
                newBackup[newBackup.lastIndex] = (newBackup.last().toInt() xor 1).toByte()
                try { open(newBackup, newDni); fail("Corrupt backup accepted") } catch (_: Exception) {}
            } finally { original.fill(0); recovered.fill(0); migrated.fill(0) }
        }
    }
}
