package pe.gob.reniec.dnie.desktop.storage

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.ensureActive
import pe.gob.reniec.dnie.desktop.model.DesktopSigningRequest
import pe.gob.reniec.dnie.domain.signing.SigningDocument
import pe.gob.reniec.dnie.domain.signing.SigningFileNames
import kotlin.coroutines.coroutineContext

class DesktopWorkspace private constructor(val id: String, val directory: Path) {
    companion object {
        const val MAX_FILE_BYTES = 256L * 1024 * 1024
        const val MAX_BATCH_BYTES = 512L * 1024 * 1024
        fun create(root: Path): DesktopWorkspace {
            Files.createDirectories(root)
            val canonicalRoot = root.toRealPath()
            val id = UUID.randomUUID().toString()
            return DesktopWorkspace(id, Files.createDirectory(canonicalRoot.resolve(id)))
        }
        fun validate(request: DesktopSigningRequest) {
            require(request.inputs.size in 1..50) { "Seleccione entre 1 y 50 archivos" }
            request.inputs.forEach {
                require(Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS)) { "La entrada debe ser un archivo regular, no un enlace" }
                require(Files.size(it) <= MAX_FILE_BYTES) { "Archivo mayor de 256 MiB" }
            }
        }
    }

    suspend fun import(inputs: List<Path>, pdfMode: pe.gob.reniec.dnie.desktop.model.DesktopPdfMode = pe.gob.reniec.dnie.desktop.model.DesktopPdfMode.INVISIBLE, pdfOptions: pe.gob.reniec.dnie.pdf.PdfSigningOptions = pe.gob.reniec.dnie.pdf.PdfSigningOptions(visible=false,reason="",location="")): List<SigningDocument> {
        var total = 0L
        return inputs.map { source ->
            coroutineContext.ensureActive()
            val id = UUID.randomUUID().toString()
            val folder = Files.createDirectories(directory.resolve("originals").resolve(id))
            val name = SigningFileNames.safeName(source.fileName.toString())
            val target = folder.resolve(name)
            try {
                var bytes = 0L
                Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).use { input ->
                    Files.newOutputStream(target).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            total += count
                            require(bytes <= MAX_FILE_BYTES && total <= MAX_BATCH_BYTES) { "Se superó el límite del archivo o lote" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                SigningDocument(id = id, name = name, file = target.toFile(), size = Files.size(target),
                    pdfInput = name.endsWith(".pdf", ignoreCase = true),
                    detached = pdfMode == pe.gob.reniec.dnie.desktop.model.DesktopPdfMode.DETACHED,
                    options = pdfOptions.copy(visible = pdfMode == pe.gob.reniec.dnie.desktop.model.DesktopPdfMode.VISIBLE))
            } catch (e: Exception) {
                Files.deleteIfExists(target)
                throw e
            }
        }
    }
}
