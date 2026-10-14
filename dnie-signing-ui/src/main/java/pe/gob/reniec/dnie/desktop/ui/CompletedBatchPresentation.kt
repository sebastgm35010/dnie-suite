package pe.gob.reniec.dnie.desktop.ui

import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import pe.gob.reniec.dnie.desktop.model.DesktopSigningResult
import pe.gob.reniec.dnie.domain.model.CitizenData
import pe.gob.reniec.dnie.crypto.X509CertificateParser

/** Reads the already-exported public certificate. Never opens a card or requests a signature. */
object CompletedBatchPresentation {
    fun citizen(result: DesktopSigningResult): CitizenData?=runCatching {
        val cert=result.directory.resolve("certificate.cer")
        require(Files.size(cert) in 1..65536)
        X509CertificateParser.extractCitizenData(X509CertificateParser.toX509Certificate(Files.readAllBytes(cert)))
    }.getOrNull()
    fun outputs(inputs: List<Path>,result: DesktopSigningResult): Map<Path,Path> =
        inputs.zip(result.documents).mapNotNull { (input,document)->document.signature?.let { input to it } }.toMap()
}

interface SuiteDesktopActions {
    fun open(path: Path)
    fun reveal(path: Path)
}
object SystemSuiteDesktopActions : SuiteDesktopActions {
    override fun open(path: Path) { require(Files.isRegularFile(path));Desktop.getDesktop().open(path.toFile()) }
    override fun reveal(path: Path) { require(Files.isRegularFile(path));Desktop.getDesktop().open(path.toAbsolutePath().parent.toFile()) }
}
