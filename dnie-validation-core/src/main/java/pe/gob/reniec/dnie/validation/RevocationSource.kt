package pe.gob.reniec.dnie.validation

import java.net.URI
import java.net.HttpURLConnection
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.cert.X509CRL
import java.util.Date

fun interface PublicCertificateSource { fun download(url: String): ByteArray }

/** Restricted CA/CRL and OCSP transport. OCSP sends only CertID, never PIN or documents. */
class ReniecPublicSource : PublicCertificateSource, OcspResponseSource {
    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        fun allowed(url: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme in setOf("https", "http") && uri.host in setOf("www.reniec.gob.pe", "crl.reniec.gob.pe", "crl2.reniec.gob.pe") &&
                uri.userInfo == null && uri.fragment == null && uri.query == null && uri.port == -1 &&
                !uri.rawPath.contains("%") && !uri.path.contains("..") &&
                listOf("/crt/sha2/", "/crl/sha2/", "/arl/sha2/").any { uri.path.startsWith(it) }
        }.getOrDefault(false)
    }
    override fun query(url: String, request: ByteArray): ByteArray {
        val uri = URI(url)
        require(uri.scheme in setOf("https","http") && uri.host == "ocsp.reniec.gob.pe" &&
            uri.userInfo == null && uri.query == null && uri.fragment == null && uri.port == -1 &&
            uri.path in setOf("", "/")) { "Dirección OCSP no permitida" }
        require(request.size <= 4096)
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout=5000; connection.readTimeout=5000; connection.instanceFollowRedirects=false
        connection.requestMethod="POST"; connection.doOutput=true
        connection.setRequestProperty("Content-Type","application/ocsp-request")
        connection.setRequestProperty("Accept","application/ocsp-response")
        connection.setFixedLengthStreamingMode(request.size)
        try {
            connection.outputStream.use { it.write(request) }
            require(connection.responseCode == 200)
            require(connection.contentLengthLong <= 1024*1024)
            val deadline = System.nanoTime()+10_000_000_000L
            return connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while(true) {
                    if(Thread.currentThread().isInterrupted || System.nanoTime()>deadline) throw java.io.InterruptedIOException()
                    val count=input.read(buffer); if(count<0) break
                    require(out.size()+count<=1024*1024); out.write(buffer,0,count)
                }
                out.toByteArray()
            }
        } finally { connection.disconnect() }
    }
    override fun download(url: String): ByteArray {
        require(allowed(url)) { "Dirección de autoridad no permitida" }
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5000; connection.readTimeout = 5000
        connection.instanceFollowRedirects = false
        val deadline = System.nanoTime() + 12_000_000_000L
        try {
            require(connection.responseCode == 200) { "Autoridad no disponible" }
            require(connection.contentLengthLong <= MAX_BYTES) { "Respuesta demasiado grande" }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(16384)
                while (true) {
                    if (Thread.currentThread().isInterrupted || System.nanoTime() > deadline)
                        throw java.io.InterruptedIOException("Consulta cancelada o fuera de plazo")
                    val count = input.read(buffer); if (count < 0) break
                    require(output.size() + count <= MAX_BYTES) { "Respuesta demasiado grande" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }
}

/** Issuer-bound revocation only; path trust is deliberately not inferred from AIA downloads. */
class OnlineRevocationInspector(private val source: PublicCertificateSource = ReniecPublicSource()) {
    fun inspect(der: ByteArray, forDocumentSignature: Boolean = true): CertificateValidationReport {
        val now = Date()
        val factory = CertificateFactory.getInstance("X.509")
        val certificate = factory.generateCertificate(der.inputStream()) as X509Certificate
        val local = CertificateChecks.inspect(certificate, now, forDocumentSignature)
        fun report(crl: RevocationAssessment): CertificateValidationReport {
            KnownRevocationPolicy.recordVerified(certificate,crl.status)
            return CertificateValidationReport(local,crl,TrustStatus.UNKNOWN,Date())
        }
        if (!local.acceptable) return report(RevocationAssessment(RevocationStatus.UNKNOWN, "Certificado no apto; no se consultó red"))
        var last = RevocationAssessment(RevocationStatus.UNKNOWN, "No hay evidencia CRL verificable")
        val deadline = System.nanoTime() + 25_000_000_000L
        for (issuerUrl in CertificateLocations.issuerUrls(certificate)) {
            if (Thread.currentThread().isInterrupted || System.nanoTime() > deadline) break
            val issuer = try { factory.generateCertificate(source.download(issuerUrl).inputStream()) as X509Certificate }
                catch (_: Exception) { continue }
            if(source is OcspResponseSource) for (url in CertificateLocations.ocspUrls(certificate)) {
                if(Thread.currentThread().isInterrupted || System.nanoTime()>deadline) break
                try {
                    last=OcspValidator.evaluate(certificate,issuer,source.query(url,OcspValidator.request(certificate,issuer)),Date())
                    if(last.status != RevocationStatus.UNKNOWN) return report(last)
                } catch (_: Exception) { last=RevocationAssessment(RevocationStatus.UNKNOWN,"OCSP no disponible/verificable") }
            }
            for (url in CertificateLocations.completeCrlUrls(certificate)) {
                if (Thread.currentThread().isInterrupted || System.nanoTime() > deadline) break
                try {
                    val crl = factory.generateCRL(source.download(url).inputStream()) as X509CRL
                    last = DirectCrlValidator.evaluate(certificate, issuer, crl, Date())
                    if (last.status != RevocationStatus.UNKNOWN) return report(last)
                } catch (_: Exception) { last = RevocationAssessment(RevocationStatus.UNKNOWN, "Red o respuesta CRL no disponible/verificable") }
            }
        }
        return report(last)
    }
}
