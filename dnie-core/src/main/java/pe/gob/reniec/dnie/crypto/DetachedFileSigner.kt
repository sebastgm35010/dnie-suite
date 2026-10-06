package pe.gob.reniec.dnie.crypto

import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Detached CMS: supports arbitrary binary contents without modifying or converting them. */
object DetachedFileSigner {
    fun sign(input: InputStream, output: OutputStream, certificateDer: ByteArray,
             cardSigner: (ByteArray) -> ByteArray): Int {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Firma cancelada")
            digest.update(buffer, 0, count)
        }
        val hash = digest.digest()
        val cert = X509CertificateParser.toX509Certificate(certificateDer)
        val cms = CmsSignatureBuilder.buildDetachedSignature(hash, cert, cardSigner = cardSigner)
        val signed = CMSSignedData(mapOf("2.16.840.1.101.3.4.2.1" to hash), cms)
        require(signed.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder()
            .setProvider(CryptoProviderInitializer.provider).build(cert))) { "La firma no corresponde al certificado del titular" }
        output.write(cms); output.flush()
        return cms.size
    }
}
