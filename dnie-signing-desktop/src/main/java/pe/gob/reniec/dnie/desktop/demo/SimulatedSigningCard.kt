package pe.gob.reniec.dnie.desktop.demo

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.util.Date
import javax.crypto.Cipher
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.PinVerificationResult

/** Explicit laboratory backend. Never imported from the user's Windows certificate store.
 * A new ephemeral key/certificate is created per instance, and no private key is exported.
 */
class SimulatedSigningCard(private val failAtSignature: Int = 0) : DnieTransceiverInterface {
    companion object { const val DEMO_PIN = "0000" }
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val certificate = run {
        CryptoProviderInitializer.init()
        val name = X500Name("CN=SIMULACION SUITE NO ES DNI, SERIALNUMBER=12345678, C=PE")
        val now = System.currentTimeMillis()
        JcaX509CertificateConverter().setProvider(CryptoProviderInitializer.provider).getCertificate(
            JcaX509v3CertificateBuilder(name, BigInteger(128, java.security.SecureRandom()), Date(now - 60000), Date(now + 86400000), name, keys.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(CryptoProviderInitializer.provider).build(keys.private)))
    }
    var selections = 0; private set
    var verifications = 0; private set
    var certificateReads = 0; private set
    var signatures = 0; private set
    var closes = 0; private set
    private var selected = false
    private var authorized = false
    private var closed = false
    override suspend fun selectPkiApplet(): Result<Unit> = runCatching {
        check(!closed); selections++; selected = true
    }
    override suspend fun verifyPin(pin: String): Result<PinVerificationResult> = runCatching {
        check(selected && !closed)
        verifications++
        authorized = pin == DEMO_PIN
        if (authorized) PinVerificationResult.Success else PinVerificationResult.InvalidPin(2)
    }
    override suspend fun readCertificate(type: CertificateType): Result<ByteArray> = runCatching {
        check(selected && !closed); require(type == CertificateType.SIGNATURE)
        certificateReads++; certificate.encoded
    }
    override suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray> = runCatching {
        check(selected && authorized && !closed) { "Autorización de simulación consumida" }
        require(hash32Bytes.size == 32)
        authorized = false
        signatures++
        check(signatures != failAtSignature) { "Interrupción simulada de tarjeta" }
        Cipher.getInstance("RSA/ECB/PKCS1Padding", CryptoProviderInitializer.provider).run {
            init(Cipher.ENCRYPT_MODE, keys.private)
            doFinal(ApduConstants.SHA256_DIGEST_INFO_PREFIX + hash32Bytes)
        }
    }
    override fun close() { closes++; closed = true; authorized = false }
}
