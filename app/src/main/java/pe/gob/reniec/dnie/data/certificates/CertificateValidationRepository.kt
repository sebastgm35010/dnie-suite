package pe.gob.reniec.dnie.data.certificates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import pe.gob.reniec.dnie.validation.*

fun interface CertificateValidationRepository {
    suspend fun inspect(der: ByteArray, forDocumentSignature: Boolean): CertificateValidationReport
}
class OnlineCertificateValidationRepository(private val inspector: OnlineRevocationInspector = OnlineRevocationInspector()) : CertificateValidationRepository {
    override suspend fun inspect(der: ByteArray, forDocumentSignature: Boolean): CertificateValidationReport =
        runInterruptible(Dispatchers.IO) { inspector.inspect(der, forDocumentSignature) }
}
