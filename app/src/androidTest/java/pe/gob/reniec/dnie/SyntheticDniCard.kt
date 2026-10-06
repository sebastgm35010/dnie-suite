package pe.gob.reniec.dnie

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import pe.gob.reniec.dnie.core.nfc.VaultProbeCard
import pe.gob.reniec.dnie.domain.model.*
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Date
import javax.crypto.Cipher

/** Test APK only. Simulates card crypto and PIN, not PACE/radio or a RENIEC-issued certificate. */
internal class SyntheticDniCard(val pair: KeyPair = newPair(), expired: Boolean = false) : VaultProbeCard {
    private val certificate = run {
        val name = X500Name("CN=Synthetic Emulator Card")
        val now = System.currentTimeMillis()
        JcaX509v3CertificateBuilder(name, BigInteger.valueOf(if (expired) 2 else 1),
            Date(now - 172800000), Date(now + if (expired) -86400000 else 86400000), name, pair.public)
            .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyEncipherment))
            .build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)).encoded
    }
    var pinAttempts = 0
    var privateOperations = 0
    var closed = false
    var cancelProof = false
    var wrongProof = false
    var lastSecret: ByteArray? = null
    override suspend fun selectPkiApplet() = Result.success(Unit)
    override fun requireVaultProbeSupport() = Result.success(Unit)
    override suspend fun readCertificate(type: CertificateType): Result<ByteArray> {
        check(type == CertificateType.ENCRYPTION); return Result.success(certificate.copyOf())
    }
    override suspend fun verifyPin(pin: String): Result<PinVerificationResult> {
        pinAttempts++
        return Result.success(if (pin == TEST_PIN) PinVerificationResult.Success else PinVerificationResult.InvalidPin(2))
    }
    private fun decrypt(bytes: ByteArray): ByteArray {
        check(!closed); privateOperations++
        if (cancelProof) throw kotlinx.coroutines.CancellationException("Synthetic cancellation")
        return (if (wrongProof) ByteArray(32) { 8 } else Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
            init(Cipher.DECRYPT_MODE, pair.private); doFinal(bytes)
        }).also { lastSecret = it }
    }
    override suspend fun unwrapVaultKey(ciphertext: ByteArray) = Result.success(decrypt(ciphertext))
    override suspend fun decipherTestSecret(ciphertext: ByteArray) = Result.success(decrypt(ciphertext))
    override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> = error("Signing the document is outside this simulation")
    override fun close() { closed = true }
    companion object {
        const val TEST_PIN = "123456"
        fun newPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }
}
