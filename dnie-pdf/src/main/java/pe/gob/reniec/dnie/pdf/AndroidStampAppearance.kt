package pe.gob.reniec.dnie.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.*
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.util.Matrix

/** All pages share one field and one cryptographic signature. */
internal object AndroidStampAppearance {
    fun apply(document: PDDocument,signature: PDSignature,options: PdfSigningOptions,name: String,dni: String,timestamp: Long) {
        val pages=options.pageTarget.pages(document.numberOfPages,options.pageNumber)
        val field=document.documentCatalog.acroForm.fieldTree.filterIsInstance<PDSignatureField>()
            .single { it.signature?.cosObject === signature.cosObject }
        val old=field.widgets.map { it.cosObject }
        document.pages.forEach { page ->
            if(page.annotations.removeAll { widget -> old.any { it === widget.cosObject } }) page.cosObject.isNeedToBeUpdated=true
        }
        // Unmerge the default widget before adding children; no self-parented /Kids.
        listOf(COSName.TYPE,COSName.SUBTYPE,COSName.RECT,COSName.AP,COSName.P,COSName.F).forEach(field.cosObject::removeItem)
        val widgets=pages.map { number ->
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

        val widget=PDAnnotationWidget().apply { setParent(field);rectangle=rect;this.page=page;isPrinted=true }
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
        page.annotations.add(widget);page.cosObject.isNeedToBeUpdated=true
        widget.cosObject.isNeedToBeUpdated=true
        widget
        }
        field.setWidgets(widgets);field.cosObject.isNeedToBeUpdated=true
        document.documentCatalog.acroForm.cosObject.isNeedToBeUpdated=true
    }

}
