package pe.gob.reniec.dnie.validation

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.ocsp.*
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder

fun interface OcspResponseSource { fun query(url: String, request: ByteArray): ByteArray }
object OcspValidator {
    fun id(cert: X509Certificate, issuer: X509Certificate): CertificateID = CertificateID(
        JcaDigestCalculatorProviderBuilder().build().get(CertificateID.HASH_SHA1),
        JcaX509CertificateHolder(issuer),cert.serialNumber)
    fun request(cert: X509Certificate, issuer: X509Certificate): ByteArray = OCSPReqBuilder().addRequest(id(cert,issuer)).build().encoded
    fun evaluate(cert: X509Certificate, issuer: X509Certificate, bytes: ByteArray, now: Date = Date()): RevocationAssessment {
        fun unknown(message: String) = RevocationAssessment(RevocationStatus.UNKNOWN,message)
        try {
            if (!CertificateChecks.inspect(issuer,now,false).acceptable) return unknown("Emisor OCSP no apto")
            issuer.checkValidity(now)
            if(cert.issuerX500Principal != issuer.subjectX500Principal || issuer.basicConstraints < 0)
                return unknown("Emisor OCSP no válido")
            cert.verify(issuer.publicKey)
            val response = OCSPResp(bytes)
            if(response.status != OCSPResp.SUCCESSFUL) return unknown("Servidor OCSP no confirmó estado")
            val basic = response.responseObject as? BasicOCSPResp ?: return unknown("Respuesta OCSP no admitida")
            if(basic.criticalExtensionOIDs.isNotEmpty()) return unknown("Extensión crítica OCSP no soportada")
            if(basic.signatureAlgorithmID.algorithm.id !in setOf("1.2.840.113549.1.1.11","1.2.840.113549.1.1.12","1.2.840.113549.1.1.13",
                "1.2.840.10045.4.3.2","1.2.840.10045.4.3.3","1.2.840.10045.4.3.4")) return unknown("Algoritmo OCSP no admitido")
            val responder = basic.responderId.toASN1Primitive()
            val candidates = listOf(issuer) + basic.certs.take(8).map { JcaX509CertificateConverter().getCertificate(it) }
            val authenticated = candidates.any { candidate -> runCatching {
                candidate.checkValidity(now)
                val holder = JcaX509CertificateHolder(candidate)
                val identityMatches = if(responder.name != null) responder.name == holder.subject
                    else MessageDigest.getInstance("SHA-1").digest(SubjectPublicKeyInfo.getInstance(candidate.publicKey.encoded).publicKeyData.bytes)
                        .contentEquals(responder.keyHash)
                if(!identityMatches) return@runCatching false
                if(!candidate.encoded.contentEquals(issuer.encoded)) {
                    if(!CertificateChecks.inspect(candidate,now).acceptable ||
                        candidate.sigAlgOID !in setOf("1.2.840.113549.1.1.11","1.2.840.113549.1.1.12","1.2.840.113549.1.1.13",
                            "1.2.840.10045.4.3.2","1.2.840.10045.4.3.3","1.2.840.10045.4.3.4") ||
                        candidate.issuerX500Principal != issuer.subjectX500Principal ||
                        candidate.extendedKeyUsage?.contains("1.3.6.1.5.5.7.3.9") != true ||
                        candidate.keyUsage?.getOrElse(0) { false } == false ||
                        candidate.getExtensionValue("1.3.6.1.5.5.7.48.1.5") == null) return@runCatching false
                    candidate.verify(issuer.publicKey)
                }
                basic.isSignatureValid(JcaContentVerifierProviderBuilder().build(candidate.publicKey))
            }.getOrDefault(false) }
            if(!authenticated) return unknown("Firma o autorización del respondedor OCSP no verificada")
            val matches = basic.responses.filter { it.certID == id(cert,issuer) }
            if(matches.size != 1) return unknown("Respuesta OCSP no corresponde inequívocamente al certificado")
            val single = matches.single()
            if(single.criticalExtensionOIDs.isNotEmpty()) return unknown("Extensión crítica de estado OCSP no soportada")
            val future = Date(now.time + 300000)
            if(basic.producedAt.after(future) || single.thisUpdate.after(future) ||
                basic.producedAt.before(Date(single.thisUpdate.time - 300000))) return unknown("Tiempo OCSP incoherente")
            val expires = single.nextUpdate ?: Date(single.thisUpdate.time + 900000)
            if(!now.before(expires) || !single.thisUpdate.before(expires) ||
                now.time - single.thisUpdate.time > 86400000) return unknown("Respuesta OCSP caducada o demasiado antigua")
            return when(val status = single.certStatus) {
                null -> RevocationAssessment(RevocationStatus.GOOD,"Sin revocación en respuesta OCSP firmada y vigente",single.thisUpdate,expires)
                is RevokedStatus -> if(status.revocationTime.after(future)) unknown("Fecha de revocación futura") else
                    RevocationAssessment(RevocationStatus.REVOKED,"Certificado revocado por OCSP",single.thisUpdate,expires)
                else -> unknown("El respondedor OCSP desconoce el certificado")
            }
        } catch (_: Exception) { return unknown("Respuesta OCSP no interpretable o no verificable") }
    }
}
