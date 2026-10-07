package pe.gob.reniec.dnie.pdf.desktop

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.util.Date
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.*
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.*
import org.bouncycastle.tsp.*
import org.bouncycastle.util.CollectionStore
import pe.gob.reniec.dnie.timestamp.*

/** Test-only RFC3161 issuer. Production always uses authenticated TSL and verified CRLs. */
internal object TestTimestampAuthority {
    fun service(): CmsTimestampService {
        val keys=KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject=X500Name("CN=Synthetic test TSA")
        val certificate=JcaX509CertificateConverter().getCertificate(JcaX509v3CertificateBuilder(subject,
            BigInteger.TEN,Date(System.currentTimeMillis()-60000),Date(System.currentTimeMillis()+3600000),subject,keys.public)
            .apply { addExtension(Extension.extendedKeyUsage,true,ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping)) }
            .build(JcaContentSignerBuilder("SHA256withRSA").build(keys.private)))
        return ReniecCmsTimestampService(TimestampClient { bytes ->
            val digests=JcaDigestCalculatorProviderBuilder().build()
            val generator=TimeStampTokenGenerator(JcaSignerInfoGeneratorBuilder(digests).build(
                JcaContentSignerBuilder("SHA256withRSA").build(keys.private),certificate),
                digests.get(AlgorithmIdentifier(TSPAlgorithms.SHA256)),ASN1ObjectIdentifier(TimestampTokenValidator.RENIEC_POLICY))
            generator.addCertificates(CollectionStore(listOf(JcaX509CertificateHolder(certificate))))
            TimeStampResponseGenerator(generator,setOf(TSPAlgorithms.SHA256))
                .generateGrantedResponse(TimeStampRequest(bytes),BigInteger.ONE,Date()).encoded
        },TimestampTokenValidator(TimestampCertificateTrust { _, _ -> }))
    }
}
