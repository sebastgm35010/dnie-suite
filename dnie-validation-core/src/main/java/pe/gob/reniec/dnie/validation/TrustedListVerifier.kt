package pe.gob.reniec.dnie.validation

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Date
import javax.xml.parsers.DocumentBuilderFactory
import org.apache.xml.security.c14n.Canonicalizer
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

internal const val TSL_NS = "http://uri.etsi.org/02231/v2#"
internal const val DS_NS = "http://www.w3.org/2000/09/xmldsig#"
internal const val C14N = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"
private const val ENVELOPED = "$DS_NS" + "enveloped-signature"
private const val SHA256 = "http://www.w3.org/2001/04/xmlenc#sha256"

/** Narrow verifier for the published PE v3 profile. No URI/network/XPath resolution. */
class TrustedListVerifier(private val signingAnchors: Set<TrustAnchor>) {
    fun verify(xml: ByteArray, now: Date = Date(), minimumSequence: Int = 25): VerifiedTrustedList {
        var stage = TrustStage.TSL_XML
        try {
            require(xml.size in 1..(2 * 1024 * 1024))
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                // Android implementations do not support every JAXP feature. The input guard is mandatory too.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            }
            // Reject non UTF-8 input and declaration/entity syntax before any XML parser can resolve it.
            val text = xml.toString(Charsets.UTF_8)
            require(text.toByteArray(Charsets.UTF_8).contentEquals(xml))
            require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true) && !text.contains('\u0000'))
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> throw SecurityException("Entidades externas no admitidas") }
            val doc = builder.parse(InputSource(xml.inputStream()))
            val root = doc.documentElement
            require(root.namespaceURI == TSL_NS && root.localName == "TrustServiceStatusList")
            val ids = mutableMapOf<String, Element>()
            var elementCount = 0
            fun walk(element: Element, depth: Int) {
                require(depth <= 40 && ++elementCount <= 10000)
                for (i in 0 until element.attributes.length) {
                    val attribute = element.attributes.item(i)
                    if (attribute.localName.equals("id", true) || attribute.nodeName.equals("id", true)) {
                        require(attribute.nodeValue.isNotBlank() && ids.put(attribute.nodeValue, element) == null)
                    }
                }
                element.children().forEach { walk(it, depth + 1) }
            }
            walk(root, 0)
            val allSignatures = doc.getElementsByTagNameNS(DS_NS, "Signature")
            require(allSignatures.length == 1)
            val signature = allSignatures.item(0) as Element
            require(signature.parentNode === root)
            val info = signature.one(DS_NS, "SignedInfo")
            require(info.one(DS_NS, "CanonicalizationMethod").getAttribute("Algorithm") == C14N)
            require(info.one(DS_NS, "SignatureMethod").getAttribute("Algorithm") == "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256")
            val keyInfo = signature.one(DS_NS, "KeyInfo")
            stage = TrustStage.TSL_SIGNER_PATH
            val certificates = keyInfo.one(DS_NS, "X509Data").children(DS_NS, "X509Certificate")
            require(certificates.size == 1)
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(decode(certificates.single().textContent).inputStream()) as X509Certificate
            require(CertificateChecks.inspect(cert, now, true).acceptable)
            require(CertificatePathValidator.validate(cert, emptyList(), signingAnchors, now) == TrustStatus.TRUSTED)
            val references = info.children(DS_NS, "Reference")
            require(references.size in 1..3)
            val seen = mutableSetOf<Element>()
            for (reference in references) {
                val uri = reference.getAttribute("URI")
                require(uri.startsWith("#") && uri.length > 1)
                val target = ids[uri.substring(1)] ?: throw SecurityException("Referencia desconocida")
                require(seen.add(target))
                val algorithm = reference.one(DS_NS, "DigestMethod").getAttribute("Algorithm")
                val transforms = reference.children(DS_NS, "Transforms").also { require(it.size <= 1) }
                    .flatMap { it.children(DS_NS, "Transform") }.map {
                        require(it.children().isEmpty()); it.getAttribute("Algorithm")
                    }
                val bytes: ByteArray
                if (target === root) {
                    stage = TrustStage.TSL_ROOT_DIGEST
                    require(algorithm == SHA256 && transforms == listOf(ENVELOPED, C14N))
                    // Keep ancestor namespaces and the signature position intact for all other references.
                    val next = signature.nextSibling
                    root.removeChild(signature)
                    try { bytes = canonical(root) } finally { root.insertBefore(signature, next) }
                } else {
                    stage = if (target === keyInfo) TrustStage.TSL_KEYINFO_DIGEST else TrustStage.TSL_PROPERTIES_DIGEST
                    require(target === keyInfo || (target.namespaceURI == "http://uri.etsi.org/01903/v1.3.2#" &&
                        target.localName == "SignedProperties" && signature.isAncestorOf(target)))
                    require(transforms.isEmpty() || transforms == listOf(C14N))
                    // Legacy SHA1 permitted only on the signed KeyInfo reference, never the list or signature.
                    require(algorithm == SHA256 || (target === keyInfo && algorithm == "$DS_NS" + "sha1"))
                    bytes = canonical(target)
                }
                val hash = MessageDigest.getInstance(if (algorithm == SHA256) "SHA-256" else "SHA-1").digest(bytes)
                require(MessageDigest.isEqual(hash, decode(reference.one(DS_NS, "DigestValue").textContent)))
            }
            require(root in seen)
            stage = TrustStage.TSL_SIGNATURE
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(cert.publicKey); verifier.update(canonical(info))
            require(verifier.verify(decode(signature.one(DS_NS, "SignatureValue").textContent)))
            stage = TrustStage.TSL_METADATA
            val scheme = root.one(TSL_NS, "SchemeInformation")
            require(scheme.one(TSL_NS, "SchemeTerritory").textContent.trim() == "PE")
            require(scheme.one(TSL_NS, "TSLVersionIdentifier").textContent.trim() == "3")
            val sequence = scheme.one(TSL_NS, "TSLSequenceNumber").textContent.trim().toInt()
            require(sequence >= minimumSequence)
            val issued = parseDate(scheme.one(TSL_NS, "ListIssueDateTime").textContent)
            val expires = parseDate(scheme.one(TSL_NS, "NextUpdate").one(TSL_NS, "dateTime").textContent)
            require(!issued.after(now) && issued.before(expires) && now.before(expires))
            stage = TrustStage.TSL_SERVICES
            val entries = root.one(TSL_NS, "TrustServiceProviderList").children(TSL_NS, "TrustServiceProvider")
                .flatMap { it.one(TSL_NS, "TSPServices").children(TSL_NS, "TSPService") }.map { service ->
                    val current = service.one(TSL_NS, "ServiceInformation")
                    // Service history remains separate; this operation reports CURRENT trust only.
                    TrustedService(current.one(TSL_NS, "ServiceTypeIdentifier").textContent.trim(),
                        current.one(TSL_NS, "ServiceStatus").textContent.trim(),
                        parseDate(current.one(TSL_NS, "StatusStartingTime").textContent),
                        current.one(TSL_NS, "ServiceDigitalIdentity").children(TSL_NS, "DigitalId")
                            .flatMap { it.children(TSL_NS, "X509Certificate") }.map {
                                CertificateFactory.getInstance("X.509").generateCertificate(decode(it.textContent).inputStream()) as X509Certificate
                            }, current.children(TSL_NS, "ServiceInformationExtensions").isEmpty())
                }
            return VerifiedTrustedList(sequence, issued, expires, cert, entries, MessageDigest.getInstance("SHA-256").digest(xml))
        } catch (failure: TrustValidationFailure) { throw failure }
        catch (failure: Exception) { throw TrustValidationFailure(stage, failure) }
        catch (failure: LinkageError) { throw TrustValidationFailure(stage, failure) }
    }
}

data class TrustedService(val type: String, val status: String, val since: Date, val identities: List<X509Certificate>,
    val scopeSupported: Boolean = true)
data class VerifiedTrustedList internal constructor(val sequence: Int, val issued: Date, val expires: Date,
    val signer: X509Certificate, val services: List<TrustedService>, val digest: ByteArray,
    val authorityEvidenceUntil: Date? = null) {
    fun currentCertificateAuthorities(now: Date): List<X509Certificate> = services.filter {
        it.scopeSupported && it.type == "http://uri.etsi.org/TrstSvc/Svctype/CA/QC" &&
            it.status == "http://uri.etsi.org/TrstSvc/eSigDir-1999-93-EC-TrustedList/Svcstatus/undersupervision" && !it.since.after(now)
    }.flatMap { it.identities }.filter { it.basicConstraints >= 0 }
}

internal fun Element.children(ns: String? = null, local: String? = null): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
        .filter { (ns == null || it.namespaceURI == ns) && (local == null || it.localName == local) }
internal fun Element.one(ns: String, local: String): Element = children(ns, local).single()
private fun Element.isAncestorOf(node: Node): Boolean {
    var parent = node.parentNode
    while (parent != null) { if (parent === this) return true; parent = parent.parentNode }
    return false
}
internal fun canonical(node: Node): ByteArray {
    Canonicalizer.registerDefaultAlgorithms()
    return ByteArrayOutputStream().also { Canonicalizer.getInstance(C14N).canonicalizeSubtree(node, it) }.toByteArray()
}
internal fun decode(text: String): ByteArray = org.bouncycastle.util.encoders.Base64.decode(text.filterNot { it.isWhitespace() })
