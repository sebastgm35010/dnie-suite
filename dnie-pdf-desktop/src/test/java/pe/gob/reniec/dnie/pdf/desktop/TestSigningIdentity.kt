package pe.gob.reniec.dnie.pdf.desktop

import java.security.KeyPairGenerator
import java.math.BigInteger
import java.util.Date
import javax.crypto.Cipher
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.*
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer
import pe.gob.reniec.dnie.core.apdu.ApduConstants

/** Synthetic certificate/key used only by tests; never a DNI backend. */
internal class TestSigningIdentity {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val certificate = run {
        CryptoProviderInitializer.init()
        val subject = X500Name("CN=PRUEBA PDF NO ES DNI, SERIALNUMBER=88888888, C=PE")
        val now = System.currentTimeMillis()
        JcaX509CertificateConverter().setProvider(CryptoProviderInitializer.provider).getCertificate(
            JcaX509v3CertificateBuilder(subject, BigInteger.ONE, Date(now-60000), Date(now+86400000), subject, keys.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(CryptoProviderInitializer.provider).build(keys.private)))
    }
    var operations = 0
    fun sign(hash: ByteArray): ByteArray {
        operations++
        return Cipher.getInstance("RSA/ECB/PKCS1Padding", CryptoProviderInitializer.provider).run {
            init(Cipher.ENCRYPT_MODE, keys.private); doFinal(ApduConstants.SHA256_DIGEST_INFO_PREFIX + hash)
        }
    }
}
