package pe.gob.reniec.dnie.icao

import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.icao.LDSSecurityObject
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer
import java.security.MessageDigest

object SodVerifier {
    fun parse(cmsBytes: ByteArray): IcaoContent.Security {
        val cms = CMSSignedData(cmsBytes)
        require(cms.signedContentTypeOID == "2.23.136.1.1.1") { "EF.SOD no contiene un LDS Security Object" }
        val content = cms.signedContent?.content as? ByteArray ?: error("EF.SOD sin contenido firmado")
        val lds = LDSSecurityObject.getInstance(ASN1Primitive.fromByteArray(content))
        val signer = cms.signerInfos.signers.single()
        val cert = cms.certificates.getMatches(null).filter { signer.sid.match(it) }.single()
        val signatureValid = signer.verify(JcaSimpleSignerInfoVerifierBuilder()
            .setProvider(CryptoProviderInitializer.provider).build(cert))
        val algorithm = when (lds.digestAlgorithmIdentifier.algorithm.id) {
            "1.3.14.3.2.26" -> "SHA-1"
            "2.16.840.1.101.3.4.2.1" -> "SHA-256"
            "2.16.840.1.101.3.4.2.2" -> "SHA-384"
            "2.16.840.1.101.3.4.2.3" -> "SHA-512"
            else -> error("Algoritmo de hash LDS no soportado")
        }
        val hashes = linkedMapOf<Int, ByteArray>()
        lds.datagroupHash.forEach { entry ->
            val group = entry.dataGroupNumber
            require(group in 1..16 && group !in hashes) { "Grupos duplicados o inválidos en EF.SOD" }
            hashes[group] = entry.dataGroupHashValue.octets
        }
        return IcaoContent.Security(algorithm, hashes, signatureValid, cert.subject.toString(), cert.issuer.toString())
    }

    fun apply(document: IcaoDocument): IcaoDocument {
        val sod = document.blocks[IcaoFile.SOD]?.content as? IcaoContent.Security ?: return document
        return document.copy(blocks = document.blocks.mapValues { (_, block) ->
            val group = block.file.group
            val bytes = block.rawBytes
            if (group == null || bytes == null) block else {
                val expected = sod.hashes[group]
                val state = if (expected == null) HashStatus.NOT_LISTED else {
                    val actual = MessageDigest.getInstance(sod.digestAlgorithm).digest(bytes)
                    if (MessageDigest.isEqual(expected, actual)) HashStatus.MATCH else HashStatus.MISMATCH
                }
                block.copy(hashStatus = state)
            }
        })
    }
}
