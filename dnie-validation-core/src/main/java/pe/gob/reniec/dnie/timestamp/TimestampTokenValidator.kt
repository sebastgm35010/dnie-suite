package pe.gob.reniec.dnie.timestamp

import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.tsp.*
import pe.gob.reniec.dnie.validation.CertificateChecks

class TimestampTokenValidator(private val trust: TimestampCertificateTrust = ReniecTimestampTrust(),
    private val policies: Set<String> = setOf(RENIEC_POLICY)) {
    fun validate(query: TimeStampRequest, bytes: ByteArray, now: Date = Date()): TimeStampToken {
        require(bytes.size in 1..ReniecTimestampClient.MAX_RESPONSE)
        val response = TimeStampResponse(bytes)
        response.validate(query)
        val token = requireNotNull(response.timeStampToken) { "TSA no emitió un sello" }
        val info = token.timeStampInfo
        require(info.policy.id in policies) { "Política TSA no admitida" }
        require(kotlin.math.abs(now.time - info.genTime.time) <= 5 * 60 * 1000L) { "Hora TSA fuera del intervalo de la solicitud" }
        require(info.hashAlgorithm.algorithm == TSPAlgorithms.SHA256)
        val holders = token.certificates.getMatches(null).filter { token.sid.match(it) }
        require(holders.size == 1)
        val certificate: X509Certificate = JcaX509CertificateConverter().getCertificate(holders.single() as X509CertificateHolder)
        require(CertificateChecks.inspect(certificate, now, false).acceptable)
        require(certificate.basicConstraints < 0 && certificate.extendedKeyUsage == listOf("1.3.6.1.5.5.7.3.8") &&
            "2.5.29.37" in certificate.criticalExtensionOIDs.orEmpty()) { "Certificado sin uso exclusivo TSA" }
        require(certificate.keyUsage?.getOrElse(0) { false } != false)
        val signer = token.toCMSSignedData().signerInfos.signers.single()
        require(signer.encryptionAlgOID in setOf("1.2.840.113549.1.1.1", "1.2.840.113549.1.1.11",
            "1.2.840.10045.4.3.2", "1.2.840.10045.4.3.3", "1.2.840.10045.4.3.4"))
        require(signer.digestAlgOID in setOf("2.16.840.1.101.3.4.2.1", "2.16.840.1.101.3.4.2.2", "2.16.840.1.101.3.4.2.3"))
        token.validate(JcaSimpleSignerInfoVerifierBuilder().build(certificate))
        trust.verify(certificate, info.genTime)
        return token
    }
    companion object { const val RENIEC_POLICY = "1.3.6.1.4.1.35300.2.1.3.2.0.105.1000.0" }
}
