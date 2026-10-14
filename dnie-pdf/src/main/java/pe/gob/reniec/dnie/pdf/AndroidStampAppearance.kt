package pe.gob.reniec.dnie.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.*
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.util.Matrix

/** One signature widget and signed visual replicas, per ISO 32000-2. */
internal object AndroidStampAppearance {
    fun apply(document: PDDocument,signature: PDSignature,options: PdfSigningOptions,name: String,dni: String,timestamp: Long) {
        val pages=options.pageTarget.pages(document.numberOfPages,options.pageNumber)
        if(pages.size>1) {
            val certification=document.documentCatalog.cosObject.getCOSDictionary(COSName.PERMS)?.getCOSDictionary(COSName.DOCMDP)
            val references=certification?.getCOSArray(COSName.REFERENCE)
            if(references!=null) for(index in 0 until references.size()) {
                val reference=references.getObject(index) as? com.tom_roush.pdfbox.cos.COSDictionary ?: continue
                if(reference.getNameAsString(COSName.TRANSFORM_METHOD)=="DocMDP") require(
                    reference.getCOSDictionary(COSName.TRANSFORM_PARAMS)?.getInt(COSName.P,2)==3
                ) { "El PDF certificado no permite réplicas gráficas; selecciona una sola página" }
            }
        }
        val field=document.documentCatalog.acroForm.fieldTree.filterIsInstance<PDSignatureField>()
            .single { it.signature?.cosObject === signature.cosObject }
        val old=field.widgets.map { it.cosObject }
        document.pages.forEach { page ->
            val annotations=page.annotations
            val retained=annotations.filterNot { widget -> old.any { it === widget.cosObject } }
            // COSArrayList iterator removal does not reliably update /Annots.
            if(retained.size!=annotations.size) { page.annotations=retained;page.cosObject.isNeedToBeUpdated=true }
        }
        // Unmerge the default widget before adding children; no self-parented /Kids.
        listOf(COSName.TYPE,COSName.SUBTYPE,COSName.RECT,COSName.AP,COSName.P,COSName.F).forEach(field.cosObject::removeItem)
        val primary=PDAnnotationWidget().apply { setParent(field) }
        pages.forEachIndexed { pageIndex,number ->
            val page=document.getPage(number-1)
        val crop = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360
        require(rotation in listOf(0,90,180,270))
        val p = options.placement
        val w = p.width * if (rotation % 180 == 0) crop.width else crop.height
        val h = p.height * if (rotation % 180 == 0) crop.height else crop.width
        val rect = when(rotation) {
            90 -> PDRectangle(crop.lowerLeftX+p.top*crop.width, crop.lowerLeftY+p.left*crop.height, p.height*crop.width,p.width*crop.height)
            180 -> PDRectangle(crop.lowerLeftX+(1-p.left-p.width)*crop.width,crop.lowerLeftY+p.top*crop.height,w,h)
            270 -> PDRectangle(crop.lowerLeftX+(1-p.top-p.height)*crop.width,crop.lowerLeftY+(1-p.left-p.width)*crop.height,p.height*crop.width,p.width*crop.height)
            else -> PDRectangle(crop.lowerLeftX+p.left*crop.width,crop.lowerLeftY+(1-p.top-p.height)*crop.height,w,h)
        }

        // ISO 32000-2 12.7.5.5: signature fields have one widget only.
        // Other pages carry ordinary visual copies covered by this same CMS.
        val widget: PDAnnotation=if(pageIndex==0) primary else PDAnnotationRubberStamp().apply {
            setName("DNIeVisualCopy");contents="Réplica visual. Comprueba la firma digital del documento."
            isReadOnly=true
        }
        widget.rectangle=rect;widget.page=page;widget.isPrinted=true
        val stream = PDAppearanceStream(document).apply { resources=PDResources();bBox=PDRectangle(w,h) }

        PDPageContentStream(document,stream).use { content ->
            when(rotation) {
                90 -> content.transform(Matrix(0f,1f,-1f,0f,h,0f))
                180 -> content.transform(Matrix(-1f,0f,0f,-1f,w,h))
                270 -> content.transform(Matrix(0f,-1f,1f,0f,0f,w))
            }
            ModernStampRenderer.draw(document,content,w,h,name,dni,options,timestamp)
        }
        widget.appearance=PDAppearanceDictionary().apply { setNormalAppearance(stream) }
        page.annotations.add(widget)
        // Incremental writers must reach replicas through the original page tree,
        // not through extra signature widgets. Mark every existing ancestor.
        var ancestor=page.cosObject
        val visited=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<com.tom_roush.pdfbox.cos.COSDictionary,Boolean>())
        while(true) {
            require(visited.size<64 && visited.add(ancestor)) { "Árbol de páginas no compatible" }
            ancestor.isNeedToBeUpdated=true
            ancestor=ancestor.getCOSDictionary(COSName.PARENT) ?: break
        }
        widget.cosObject.isNeedToBeUpdated=true
        }
        field.setWidgets(listOf(primary));field.cosObject.isNeedToBeUpdated=true
        document.documentCatalog.acroForm.cosObject.isNeedToBeUpdated=true
        document.documentCatalog.cosObject.isNeedToBeUpdated=true
    }

}
