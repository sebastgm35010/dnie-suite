package pe.gob.reniec.dnie.data.signing

import pe.gob.reniec.dnie.domain.signing.PdfPageSize

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File

object PdfDocumentMetadata {
    /** Protected PDFs can be signed as exact binary originals using detached CMS. */
    fun pages(file: File): List<PdfPageSize> = try {
        PDDocument.load(file, MemoryUsageSetting.setupMixed(32L * 1024 * 1024)).use { doc ->
            if (doc.isEncrypted) return@use emptyList()
            require(doc.numberOfPages > 0) { "El PDF no tiene páginas" }
            doc.pages.map { page ->
                val crop = page.cropBox
                require(crop.width.isFinite() && crop.height.isFinite() && crop.width > 0 && crop.height > 0) { "Dimensiones de página inválidas" }
                val rotation = ((page.rotation % 360) + 360) % 360
                require(rotation in setOf(0, 90, 180, 270)) { "Rotación PDF no compatible" }
                val rotated = rotation == 90 || rotation == 270
                PdfPageSize(if (rotated) crop.height else crop.width, if (rotated) crop.width else crop.height)
            }
        }
    } catch (_: InvalidPasswordException) { emptyList() }
}
