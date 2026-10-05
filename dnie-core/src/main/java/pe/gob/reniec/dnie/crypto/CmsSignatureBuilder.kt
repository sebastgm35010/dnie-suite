package pe.gob.reniec.dnie.crypto

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.asn1.cms.Time
import org.bouncycastle.asn1.ess.ESSCertIDv2
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cms.CMSAttributeTableGenerator
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator
import org.bouncycastle.cms.SignerInfoGeneratorBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.DigestCalculator
import org.bouncycastle.operator.DigestCalculatorProvider
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.util.CollectionStore
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Hashtable

/**
 * Constructor de contenedores criptográficos CMS / PKCS#7 (RFC 5652) en modo detached
 * conforme al estándar PAdES Baseline (ETSI EN 319 142-1) y la Ley de Firmas Digitales del Perú.
 *
 * Características:
 * 1. Incorpora SignedAttributes: `id-contentType`, `id-signingTime`, `id-messageDigest` y RFC 5035 `id-aa-signingCertificateV2`.
 * 2. Embebe el certificado del titular (DNIe) y la cadena de confianza intermedia / raíz de RENIEC en el `CertificateSet`.
 * 3. Delega el cómputo de la firma RSA al [DnieContentSigner].
 */
object CmsSignatureBuilder {

    init {
        CryptoProviderInitializer.init()
    }

    /**
     * Construye un contenedor CMS SignedData detached a partir del resumen SHA-256 precalculado.
     *
     * @param documentHash Resumen SHA-256 de 32 bytes del contenido o rango de bytes del documento.
     * @param signerCertificate Certificado X.509 del ciudadano extraído del chip DNIe (EF 00 1D).
     * @param caCertificates Cadena opcional de certificados de Autoridades Certificadoras (Sub-CA y Root CA).
     * @param contentSigner Instancia de [ContentSigner] (habitualmente [DnieContentSigner]).
     * @return Arreglo binario DER con la estructura ASN.1 del contenedor CMS SignedData.
     */
    fun buildDetachedSignature(
        documentHash: ByteArray,
        signerCertificate: X509Certificate,
        caCertificates: List<X509Certificate> = emptyList(),
        contentSigner: ContentSigner
    ): ByteArray {
        require(documentHash.size == 32) {
            "El hash del documento debe tener exactamente 32 bytes (recibido ${documentHash.size})"
        }

        val cmsGenerator = CMSSignedDataGenerator()

        // Generador de SignedAttributes con atributos obligatorios de PAdES / CAdES
        val signedAttrGen = CMSAttributeTableGenerator { parameters ->
            val stdTable = DefaultSignedAttributeTableGenerator().getAttributes(parameters)
            val tableMap = Hashtable<ASN1ObjectIdentifier, Attribute>()
            if (stdTable != null) {
                val rawTable = stdTable.toHashtable()
                for (key in rawTable.keys()) {
                    val attr = rawTable[key] as? Attribute
                    if (key is ASN1ObjectIdentifier && attr != null) {
                        tableMap[key] = attr
                    }
                }
            }

            // 1. id-contentType: id-data
            tableMap[CMSAttributes.contentType] = Attribute(
                CMSAttributes.contentType,
                DERSet(CMSObjectIdentifiers.data)
            )

            // 2. id-signingTime
            if (!tableMap.containsKey(CMSAttributes.signingTime)) {
                tableMap[CMSAttributes.signingTime] = Attribute(
                    CMSAttributes.signingTime,
                    DERSet(Time(Date()))
                )
            }

            // 3. id-messageDigest: Hash de 32 bytes del documento
            tableMap[CMSAttributes.messageDigest] = Attribute(
                CMSAttributes.messageDigest,
                DERSet(DEROctetString(documentHash))
            )

            // 4. id-aa-signingCertificateV2 (RFC 5035 / PAdES Baseline)
            val certHash = MessageDigest.getInstance("SHA-256").digest(signerCertificate.encoded)
            val essCertIdV2 = ESSCertIDv2(
                AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256),
                certHash
            )
            val signingCertV2 = SigningCertificateV2(arrayOf(essCertIdV2))
            tableMap[PKCSObjectIdentifiers.id_aa_signingCertificateV2] = Attribute(
                PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                DERSet(signingCertV2)
            )

            AttributeTable(tableMap)
        }

        val digestCalculatorProvider = DigestCalculatorProvider { algId ->
            object : DigestCalculator {
                private val os = ByteArrayOutputStream()
                override fun getAlgorithmIdentifier(): AlgorithmIdentifier = algId
                override fun getOutputStream(): OutputStream = os
                override fun getDigest(): ByteArray = documentHash
            }
        }

        val signerInfoGenBuilder = SignerInfoGeneratorBuilder(digestCalculatorProvider)
            .setSignedAttributeGenerator(signedAttrGen)

        val signerInfoGenerator = signerInfoGenBuilder.build(
            contentSigner,
            JcaX509CertificateHolder(signerCertificate)
        )
        cmsGenerator.addSignerInfoGenerator(signerInfoGenerator)

        // Incorporar certificado del firmante y certificados de la cadena CA
        val certList = mutableListOf<X509CertificateHolder>()
        certList.add(JcaX509CertificateHolder(signerCertificate))
        caCertificates.forEach { caCert ->
            certList.add(JcaX509CertificateHolder(caCert))
        }
        cmsGenerator.addCertificates(CollectionStore(certList))

        // Generar en modo detached (encapsulate = false)
        val msg = CMSProcessableByteArray(documentHash)
        val signedData: CMSSignedData = cmsGenerator.generate(msg, false)
        return signedData.encoded
    }

    /**
     * Construye un contenedor CMS SignedData detached leyendo un flujo [InputStream].
     */
    fun buildDetachedSignatureFromStream(
        inputStream: InputStream,
        signerCertificate: X509Certificate,
        caCertificates: List<X509Certificate> = emptyList(),
        contentSigner: ContentSigner
    ): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        val documentHash = digest.digest()
        return buildDetachedSignature(documentHash, signerCertificate, caCertificates, contentSigner)
    }

    /**
     * Sobrecarga de stream que acepta un callback de firma en tarjeta en lugar de un [ContentSigner].
     */
    fun buildDetachedSignatureFromStream(
        inputStream: InputStream,
        signerCertificate: X509Certificate,
        caCertificates: List<X509Certificate> = emptyList(),
        cardSigner: (sha256Digest: ByteArray) -> ByteArray
    ): ByteArray {
        val contentSigner = DnieContentSigner(cardSigner)
        return buildDetachedSignatureFromStream(inputStream, signerCertificate, caCertificates, contentSigner)
    }

    /**
     * Sobrecarga que acepta un callback de firma en tarjeta en lugar de un [ContentSigner] preconstruido.
     */
    fun buildDetachedSignature(
        documentHash: ByteArray,
        signerCertificate: X509Certificate,
        caCertificates: List<X509Certificate> = emptyList(),
        cardSigner: (sha256Digest: ByteArray) -> ByteArray
    ): ByteArray {
        val contentSigner = DnieContentSigner(cardSigner)
        return buildDetachedSignature(documentHash, signerCertificate, caCertificates, contentSigner)
    }
}
