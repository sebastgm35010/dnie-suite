package pe.gob.reniec.dnie.data.certificates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import pe.gob.reniec.dnie.validation.*

fun interface CertificateValidationRepository {
    suspend fun inspect(der: ByteArray, forDocumentSignature: Boolean): CertificateValidationReport
}
class OnlineCertificateValidationRepository(private val inspector: CertificateTrustInspector = CertificateTrustInspector()) : CertificateValidationRepository {
    override suspend fun inspect(der: ByteArray, forDocumentSignature: Boolean): CertificateValidationReport =
        runInterruptible(Dispatchers.IO) {
            inspector.inspect(der, forDocumentSignature).also { report ->
                pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("certificate_validation", "completed",
                    "status" to report.trust.name, "state" to report.chainRevocation.name,
                    "code" to report.trustFailureCode.ifEmpty { "NONE" })
                report.caRevocationEvidence.forEach { evidence ->
                    pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("certificate_validation", "ca_revocation",
                        "stage" to "CA_${evidence.depth}", "state" to evidence.assessment.status.name,
                        "mode" to evidence.method.name, "code" to evidence.diagnosticCode.ifEmpty { "NONE" })
                }
            }
        }
    companion object {
        private var shared: OnlineCertificateValidationRepository? = null
        @Synchronized fun create(context: android.content.Context): OnlineCertificateValidationRepository {
            return shared ?: OnlineCertificateValidationRepository(CertificateTrustInspector(
                AndroidTrustedListSources.create(context),
                CachedPublicCertificateSource()
            )).also { shared = it }
        }
    }
}

/** Public rollback metadata only. No certificates, PIN, CAN or document contents. */
internal class AndroidTrustedListRecordStore(context: android.content.Context) : TrustedListRecordStore {
    private val preferences = context.getSharedPreferences("verified_tsl_version", android.content.Context.MODE_PRIVATE)
    override fun read(): TrustedListRecord? {
        val hash = preferences.getString("sha256", null) ?: return null
        return TrustedListRecord(preferences.getInt("sequence", 25), hash)
    }
    override fun save(record: TrustedListRecord) {
        check(preferences.edit().putInt("sequence", record.sequence).putString("sha256", record.sha256).commit())
    }
}
