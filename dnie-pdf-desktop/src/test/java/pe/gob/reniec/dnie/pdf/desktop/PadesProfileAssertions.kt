package pe.gob.reniec.dnie.pdf.desktop

import java.security.MessageDigest
import java.security.cert.X509Certificate
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cms.CMSSignedData
import org.junit.Assert.*

/** Profile checks supplement cryptographic verification; they do not establish trust. */
internal fun assertPadesProfile(signature: PDSignature, pdf: ByteArray, certificate: X509Certificate) {
    assertEquals("ETSI.CAdES.detached", signature.subFilter)
    assertEquals(0, signature.byteRange[0])
    assertNotNull(signature.signDate)
    assertEquals(0, signature.signDate.timeZone.rawOffset)
    val contents = signature.getContents(pdf)
    val der = ASN1InputStream(contents).use { it.readObject().getEncoded(ASN1Encoding.DER) }
    assertArrayEquals(der, contents.copyOf(der.size))
    val cms = CMSSignedData(der)
    val signer = cms.signerInfos.signers.single()
    assertNull(signer.signedAttributes.get(CMSAttributes.signingTime))
    val binding = SigningCertificateV2.getInstance(signer.signedAttributes.get(
        PKCSObjectIdentifiers.id_aa_signingCertificateV2).attrValues.getObjectAt(0))
    assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(certificate.encoded), binding.certs.single().certHash)
}
