package pe.gob.reniec.dnie.validation

import java.util.Date
import java.math.BigInteger
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cert.ocsp.*
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.Assert.*
import org.junit.Test

class OcspValidatorTest {
    private val fixture=CertificateValidationTest()
    private val issuer=fixture.cert(true)
    private val leaf=fixture.cert()
    private val now=Date()
    private fun response(status: CertificateStatus?=null, stale: Boolean=false,
        wrongId: Boolean=false, forged: Boolean=false, unauthorized: Boolean=false): ByteArray {
        val holder=JcaX509CertificateHolder(if(unauthorized) leaf else issuer)
        val builder=BasicOCSPRespBuilder(RespID(holder.subject))
        val id=if(wrongId) CertificateID(JcaDigestCalculatorProviderBuilder().build().get(CertificateID.HASH_SHA1),
            JcaX509CertificateHolder(issuer),BigInteger.valueOf(999)) else OcspValidator.id(leaf,issuer)
        builder.addResponse(id,status,Date(now.time-60000),Date(now.time+if(stale) -10000 else 300000),null)
        val signer=JcaContentSignerBuilder("SHA256withRSA").build(
            if(forged) CertificateValidationTest.otherKeys.private else CertificateValidationTest.keys.private)
        val basic=builder.build(signer,arrayOf(holder),now)
        return OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL,basic).encoded
    }
    @Test fun signedCurrentGoodResponseMatchesExactCertificate() {
        assertEquals(RevocationStatus.GOOD,OcspValidator.evaluate(leaf,issuer,response(),now).status)
        val request=OCSPReq(OcspValidator.request(leaf,issuer))
        assertEquals(OcspValidator.id(leaf,issuer),request.requestList.single().certID)
    }
    @Test fun signedRevocationIsReportedAndUnknownRemainsUnknown() {
        assertEquals(RevocationStatus.REVOKED,OcspValidator.evaluate(leaf,issuer,
            response(RevokedStatus(Date(now.time-10000),1)),now).status)
        assertEquals(RevocationStatus.UNKNOWN,OcspValidator.evaluate(leaf,issuer,response(UnknownStatus()),now).status)
    }
    @Test fun expiredForgedWrongCertificateAndUnauthorizedResponderAreRejected() {
        listOf(response(stale=true),response(forged=true),response(wrongId=true),response(unauthorized=true)).forEach {
            assertEquals(RevocationStatus.UNKNOWN,OcspValidator.evaluate(leaf,issuer,it,now).status)
        }
    }
    @Test fun malformedAndServerErrorNeverReturnGood() {
        listOf(byteArrayOf(1,2,3),OCSPRespBuilder().build(OCSPRespBuilder.UNAUTHORIZED,null).encoded).forEach {
            assertEquals(RevocationStatus.UNKNOWN,OcspValidator.evaluate(leaf,issuer,it,now).status)
        }
    }
}
