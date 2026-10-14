package pe.gob.reniec.dnie.data.qr

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.crypto.X509CertificateParser
import pe.gob.reniec.dnie.domain.model.CertificateInfo
import pe.gob.reniec.dnie.pdf.StampFileDigest
import pe.gob.reniec.dnie.pdf.StampQrCodec
import java.io.ByteArrayOutputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

interface StampQrRepository {
    suspend fun original(uri: String): StampFileDigest
    suspend fun certificate(uri: String): CertificateInfo
}

/** SAF only, streaming on IO; no retained files, QR logs, URL fetches or network from claims. */
class AndroidStampQrRepository(private val resolver: ContentResolver): StampQrRepository {
    override suspend fun original(uri: String) = withContext(Dispatchers.IO) {
        val context=currentCoroutineContext()
        requireNotNull(resolver.openInputStream(Uri.parse(uri))).use { input ->
            StampQrCodec.digest(input) { context.ensureActive() }
        }
    }
    override suspend fun certificate(uri: String): CertificateInfo = withContext(Dispatchers.IO) {
        val context=currentCoroutineContext()
        val bytes=requireNotNull(resolver.openInputStream(Uri.parse(uri))).use { input ->
            val out=ByteArrayOutputStream();val buffer=ByteArray(4096)
            while(true) { context.ensureActive();val count=input.read(buffer);if(count<0) break
                require(out.size()+count<=128*1024) { "Certificado demasiado grande" };out.write(buffer,0,count) }
            out.toByteArray()
        }
        val cert=CertificateFactory.getInstance("X.509").generateCertificate(bytes.inputStream()) as X509Certificate
        X509CertificateParser.parse(cert)
    }
}
