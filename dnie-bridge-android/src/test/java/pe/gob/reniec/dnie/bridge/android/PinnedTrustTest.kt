package pe.gob.reniec.dnie.bridge.android

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.util.Date

class PinnedTrustTest {
    private fun certificate(expired: Boolean = false): java.security.cert.X509Certificate {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val name = X500Name("CN=Synthetic bridge test")
        return JcaX509CertificateConverter().getCertificate(
            JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date(now - 120_000),
                Date(now + if (expired) -60_000 else 60_000), name, key.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").build(key.private)))
    }
    @Test fun onlyPairedValidLeafAccepted() {
        val cert = certificate()
        val pin = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        PinnedTrust(pin).checkServerTrusted(arrayOf(cert), "RSA")
        assertThrows(CertificateException::class.java) {
            PinnedTrust(ByteArray(32)).checkServerTrusted(arrayOf(cert), "RSA")
        }
        assertThrows(CertificateException::class.java) {
            PinnedTrust(pin).checkServerTrusted(emptyArray(), "RSA")
        }
    }
    @Test fun evenPairedExpiredLeafRejected() {
        val cert = certificate(expired = true)
        val pin = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        assertThrows(CertificateException::class.java) {
            PinnedTrust(pin).checkServerTrusted(arrayOf(cert), "RSA")
        }
    }
}
