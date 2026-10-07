package pe.gob.reniec.dnie.desktop.remote

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer

/** Ephemeral transport identity, NOT a simulated DNI signing card. Never written to disk. */
class JobTlsIdentity {
    val context: SSLContext
    val fingerprint: ByteArray
    init {
        CryptoProviderInitializer.init()
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=DNIe Suite private job")
        val now = System.currentTimeMillis()
        val certificate = JcaX509CertificateConverter().setProvider(CryptoProviderInitializer.provider).getCertificate(
            JcaX509v3CertificateBuilder(name, BigInteger(128, SecureRandom()), Date(now - 60_000), Date(now + 86_400_000), name, keys.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(CryptoProviderInitializer.provider).build(keys.private)))
        fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        val password = java.util.UUID.randomUUID().toString().toCharArray()
        try {
            val store = KeyStore.getInstance("PKCS12").apply { load(null, null); setKeyEntry("suite", keys.private, password, arrayOf(certificate)) }
            val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password) }
            context = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, SecureRandom()) }
        } finally { password.fill('\u0000') }
    }
}
