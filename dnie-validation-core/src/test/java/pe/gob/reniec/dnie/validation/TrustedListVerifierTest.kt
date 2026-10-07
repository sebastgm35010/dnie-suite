package pe.gob.reniec.dnie.validation

import java.io.ByteArrayOutputStream
import java.security.cert.TrustAnchor
import java.util.Date
import javax.xml.crypto.dsig.*
import javax.xml.crypto.dsig.dom.DOMSignContext
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
import javax.xml.crypto.dsig.spec.TransformParameterSpec
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.junit.Assert.*
import org.junit.Test

class TrustedListVerifierTest {
    private val fixture = CertificateValidationTest()
    private val root = fixture.cert(true)
    private val signer = fixture.cert()
    private val now = Date()
    private val authorities = setOf(TrustAnchor(root, null))
    private val xmlFactory = XMLSignatureFactory.getInstance("DOM")
    private fun list(sequence: Int = 25, issued: Long = now.time - 10000, expires: Long = now.time + 500000,
        status: String = "undersupervision", territory: String = "PE", serviceType: String = "CA/QC",
        extension: Boolean = false): ByteArray {
        fun date(value: Long) = java.time.Instant.ofEpochMilli(value).toString()
        val der = org.bouncycastle.util.encoders.Base64.toBase64String(root.encoded)
        val contents = """<TrustServiceStatusList xmlns="$TSL_NS" Id="list">
          <SchemeInformation><TSLVersionIdentifier>3</TSLVersionIdentifier><TSLSequenceNumber>$sequence</TSLSequenceNumber>
          <SchemeTerritory>$territory</SchemeTerritory><ListIssueDateTime>${date(issued)}</ListIssueDateTime>
          <NextUpdate><dateTime>${date(expires)}</dateTime></NextUpdate></SchemeInformation>
          <TrustServiceProviderList><TrustServiceProvider><TSPServices><TSPService><ServiceInformation>
          <ServiceTypeIdentifier>http://uri.etsi.org/TrstSvc/Svctype/$serviceType</ServiceTypeIdentifier>
          <ServiceStatus>http://uri.etsi.org/TrstSvc/eSigDir-1999-93-EC-TrustedList/Svcstatus/$status</ServiceStatus>
          <StatusStartingTime>${date(now.time - 20000)}</StatusStartingTime>
          <ServiceDigitalIdentity><DigitalId><X509Certificate>$der</X509Certificate></DigitalId></ServiceDigitalIdentity>
          ${if (extension) "<ServiceInformationExtensions><Unsupported/></ServiceInformationExtensions>" else ""}
          </ServiceInformation></TSPService></TSPServices></TrustServiceProvider></TrustServiceProviderList>
          </TrustServiceStatusList>"""
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(contents.toByteArray().inputStream())
        doc.documentElement.setIdAttribute("Id", true)
        val reference = xmlFactory.newReference("#list", xmlFactory.newDigestMethod(DigestMethod.SHA256, null),
            listOf(xmlFactory.newTransform(Transform.ENVELOPED, null as TransformParameterSpec?),
                xmlFactory.newTransform(C14N, null as TransformParameterSpec?)), null, null)
        val info = xmlFactory.newSignedInfo(xmlFactory.newCanonicalizationMethod(C14N, null as C14NMethodParameterSpec?),
            xmlFactory.newSignatureMethod(SignatureMethod.RSA_SHA256, null), listOf(reference))
        val keyInfo = xmlFactory.keyInfoFactory.newKeyInfo(listOf(xmlFactory.keyInfoFactory.newX509Data(listOf(signer))))
        xmlFactory.newXMLSignature(info, keyInfo).sign(DOMSignContext(CertificateValidationTest.keys.private, doc.documentElement))
        return ByteArrayOutputStream().also {
            TransformerFactory.newInstance().newTransformer().transform(DOMSource(doc), StreamResult(it))
        }.toByteArray()
    }
    private fun rejected(xml: ByteArray, anchors: Set<TrustAnchor> = authorities, floor: Int = 25) {
        assertTrue(runCatching { TrustedListVerifier(anchors).verify(xml, now, floor) }.isFailure)
    }
    @Test fun signedCurrentListWithExplicitAnchorProvidesCurrentAuthorities() {
        val verified = TrustedListVerifier(authorities).verify(list(), now)
        assertEquals(25, verified.sequence)
        assertEquals(1, verified.currentCertificateAuthorities(now).size)
    }
    @Test fun signerInXmlDoesNotMakeItsOwnTrust() { rejected(list(), emptySet()) }
    @Test fun changedSignedContentOrSignatureIsRejected() {
        val xml = list().toString(Charsets.UTF_8)
        rejected(xml.replace("<TSLSequenceNumber>25", "<TSLSequenceNumber>26").toByteArray())
        rejected(xml.replace("<SignatureValue>", "<SignatureValue>A").toByteArray())
    }
    @Test fun expiredFutureWrongTerritoryAndRollbackListsAreRejected() {
        rejected(list(expires = now.time - 1000))
        rejected(list(issued = now.time + 10000))
        rejected(list(territory = "XX"))
        rejected(list(sequence = 24))
        rejected(list(), floor = 26)
    }
    @Test fun duplicateIdsAndExternalReferencesAreRejected() {
        val xml = list().toString(Charsets.UTF_8)
        rejected(xml.replace("<SchemeInformation>", "<SchemeInformation Id=\"list\">" ).toByteArray())
        rejected(xml.replace("URI=\"#list\"", "URI=\"https://example.invalid/list\"").toByteArray())
    }
    @Test fun doctypeEntityAndDeepNestingAreRejectedBeforeResolution() {
        val xml = list().toString(Charsets.UTF_8)
        rejected(xml.replace("?>", "?><!DOCTYPE x [<!ENTITY ex SYSTEM 'file:///secret'>]>").toByteArray())
        rejected(xml.replace("<SchemeInformation>", "<SchemeInformation>" + "<x>".repeat(45))
            .replace("</SchemeInformation>", "</x>".repeat(45) + "</SchemeInformation>").toByteArray())
    }
    @Test fun withdrawnWrongTypeAndUnsupportedScopeCannotBecomeAnchors() {
        for (xml in listOf(list(status = "withdrawn"), list(serviceType = "RA"), list(extension = true)))
            assertTrue(TrustedListVerifier(authorities).verify(xml, now).currentCertificateAuthorities(now).isEmpty())
    }
    @Test fun wrappingRootInsideAnotherDocumentIsRejected() {
        val xml = list().toString(Charsets.UTF_8).substringAfter("?>")
        rejected("<wrapper>$xml</wrapper>".toByteArray())
    }
}
