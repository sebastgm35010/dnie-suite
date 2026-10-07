package pe.gob.reniec.dnie.domain.scan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.crypto.X509CertificateParser
import pe.gob.reniec.dnie.domain.model.*

/** Public certificate branch; no PIN or private operation. Each absent certificate is independent. */
class DniePublicCertificateReader(private val card: DnieTransceiverInterface) : PublicCertificateReader {
    override suspend fun read(progress: (String)->Unit): CertificateSnapshot {
        card.selectPkiApplet().getOrThrow();coroutineContext.ensureActive()
        val certificates=linkedMapOf<CertificateType,CertificateInfo>(); val issues=mutableSetOf<CertificateType>()
        for(type in CertificateType.entries) {
            progress("Leyendo certificado de ${type.displayName}…")
            try {
                val bytes=card.readCertificate(type).getOrThrow();coroutineContext.ensureActive()
                certificates[type]=X509CertificateParser.parse(bytes,type)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(UnifiedDniScan.isFatal(e)) return CertificateSnapshot(certificates,issues+type,true); issues.add(type) }
        }
        return CertificateSnapshot(certificates,issues)
    }
    override fun close()=card.close()
}
