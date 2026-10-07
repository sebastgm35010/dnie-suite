package pe.gob.reniec.dnie.timestamp

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.cms.*
import org.bouncycastle.tsp.*

fun interface CmsTimestampService { fun stamp(cms: ByteArray): ByteArray }

/** Adds a verified unsigned RFC3161 attribute. Never requests a new document signature. */
class ReniecCmsTimestampService(private val client: TimestampClient = ReniecTimestampClient(),
    private val validator: TimestampTokenValidator = TimestampTokenValidator()) : CmsTimestampService {
    override fun stamp(cms: ByteArray): ByteArray {
        try {
            val signed = CMSSignedData(cms)
            val signer = signed.signerInfos.signers.single()
            val oid = ASN1ObjectIdentifier("1.2.840.113549.1.9.16.2.14")
            require(signer.unsignedAttributes?.get(oid) == null) { "La firma ya tiene sello TSA" }
            val query = TimeStampRequestGenerator().apply { setCertReq(true); setReqPolicy(TimestampTokenValidator.RENIEC_POLICY) }
                .generate(TSPAlgorithms.SHA256, MessageDigest.getInstance("SHA-256").digest(signer.signature),
                    BigInteger(128, SecureRandom()).setBit(127))
            val token = validator.validate(query, client.request(query.encoded))
            val attributes = signer.unsignedAttributes?.toASN1EncodableVector() ?: ASN1EncodableVector()
            attributes.add(Attribute(oid, DERSet(ASN1Primitive.fromByteArray(token.encoded))))
            val stamped = SignerInformation.replaceUnsignedAttributes(signer, AttributeTable(attributes))
            return CMSSignedData.replaceSigners(signed, SignerInformationStore(listOf(stamped)))
                .toASN1Structure().getEncoded(ASN1Encoding.DER)
        } catch (failure: Exception) {
            throw TimestampFailure("No se pudo obtener o verificar el sello RENIEC; la firma del DNI no se repitió", failure)
        }
    }
}
class TimestampFailure(message: String, cause: Throwable) : Exception(message, cause)
