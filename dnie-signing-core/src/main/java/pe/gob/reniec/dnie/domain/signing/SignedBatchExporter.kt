package pe.gob.reniec.dnie.domain.signing

import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SignedBatchExporter {
    fun write(artifacts: List<SignedArtifact>, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            val certificates = mutableSetOf<String>()
            val lines = mutableListOf<String>()
            fun entry(name: String, write: () -> Unit) { zip.putNextEntry(ZipEntry(name)); write(); zip.closeEntry() }
            artifacts.filter { it.status == DocumentSignStatus.SIGNED }.forEachIndexed { index, artifact ->
                val prefix = "%03d_".format(index + 1)
                val signed = requireNotNull(artifact.output)
                if (!artifact.document.isPdf) {
                    entry("originales/$prefix${artifact.document.name}") {
                        CancellableSigningInput(
                            artifact.document.file.inputStream(),
                            expectedSize = artifact.document.size,
                            expectedSha256 = artifact.document.sha256
                        ).use { input ->
                            input.copyTo(zip)
                            input.verifyComplete()
                        }
                    }
                }
                entry("firmados/$prefix${signed.name}") {
                    CancellableSigningInput(
                        signed.inputStream(),
                        expectedSize = artifact.outputSize,
                        expectedSha256 = artifact.outputSha256
                    ).use { input ->
                        input.copyTo(zip)
                        input.verifyComplete()
                    }
                }
                artifact.certificate?.let { cert ->
                    val id = MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) }
                    if (certificates.add(id)) entry("certificados/$id.cer") { zip.write(cert) }
                }
                lines += "$prefix${artifact.document.name}: ${if (artifact.document.isPdf) "PDF con firma incorporada" else "original y firma CMS .p7s separados"}"
            }
            entry("LEEME.txt") {
                zip.write(("Los PDF contienen su firma digital. Para verificar una firma .p7s se necesita el original exacto incluido en originales/.\n\n" + lines.joinToString("\n")).toByteArray())
            }
        }
    }
}
