package pe.gob.reniec.dnie.pdf.desktop

import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument

internal object PdfSigningPolicy {
    fun validate(document: PDDocument, visible: Boolean = false) {
        require(document.numberOfPages > 0) { "El PDF no tiene paginas" }
        require(!document.isEncrypted) { "Desbloquee el PDF antes de firmarlo" }
        val certification = document.documentCatalog.cosObject.getCOSDictionary(COSName.PERMS)?.getCOSDictionary(COSName.DOCMDP)
        val signatures = document.signatureDictionaries.map { it.cosObject } + listOfNotNull(certification)
        signatures.forEach { signature ->
            val references = signature.getCOSArray(COSName.REFERENCE)
            if (references != null) for (i in 0 until references.size()) {
                val reference = references.getObject(i) as? COSDictionary ?: continue
                when (reference.getNameAsString(COSName.TRANSFORM_METHOD)) {
                    "DocMDP" -> { require(!visible) { "Sello visible en PDF certificado no admitido en esta entrega" }; require(reference.getCOSDictionary(COSName.TRANSFORM_PARAMS)?.getInt(COSName.P, 2) in 2..3) {
                        "El PDF certificado no permite agregar firmas"
                    } }
                    "FieldMDP" -> error("PDF con restricciones FieldMDP no admitido en esta entrega")
                }
            }
        }
    }
}
