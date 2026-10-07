package pe.gob.reniec.dnie.desktop.application

import java.nio.file.Files
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface
import pe.gob.reniec.dnie.crypto.CertificateExporter
import pe.gob.reniec.dnie.desktop.model.*
import pe.gob.reniec.dnie.desktop.pdf.UnavailablePdfEngine
import pe.gob.reniec.dnie.desktop.storage.DesktopWorkspace
import pe.gob.reniec.dnie.domain.signing.BatchSigner
import pe.gob.reniec.dnie.domain.signing.SignedBatchExporter
import pe.gob.reniec.dnie.pdf.PadesSignerEngineInterface

/** Platform coordination only; CMS, PIN lifetime and sequential signing remain in core. */
class DesktopSigningService(private val pdf: PadesSignerEngineInterface = UnavailablePdfEngine()) {
    suspend fun execute(request: DesktopSigningRequest, card: DnieTransceiverInterface, pin: String,
                        backend: SigningBackend): DesktopSigningResult {
        val owned = OwnedCard(card)
        try {
            DesktopWorkspace.validate(request)
            val workspace = DesktopWorkspace.create(request.workspaceRoot)
            val documents = workspace.import(request.inputs)
            val signed = BatchSigner(pdf).sign(documents, owned, pin, workspace.directory.resolve("signed").toFile())
            val results = signed.artifacts.map { DesktopDocumentResult(it.document.id, it.document.name, it.status,
                it.document.file.toPath(), it.output?.toPath(), it.message) }
            signed.artifacts.firstOrNull { it.certificate != null }?.certificate?.let { cert ->
                Files.write(workspace.directory.resolve("certificate.cer"), cert)
                Files.writeString(workspace.directory.resolve("certificate.pem"), CertificateExporter.toPem(cert))
            }
            if (signed.artifacts.any { it.output != null }) {
                val staging = workspace.directory.resolve("signed-documents.zip.part")
                try {
                    Files.newOutputStream(staging).use { SignedBatchExporter.write(signed.artifacts, it) }
                    Files.move(staging, workspace.directory.resolve("signed-documents.zip"))
                } finally { Files.deleteIfExists(staging) }
            }
            val result = DesktopSigningResult(workspace.id, backend, workspace.directory, results, signed.sessionError)
            DesktopResultWriter.write(result)
            return result
        } finally {
            // Also covers preflight/import failure, before BatchSigner owns the card.
            owned.close()
        }
    }
}
