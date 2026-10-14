package pe.gob.reniec.dnie.desktop.ui

import pe.gob.reniec.dnie.pdf.PdfSigningOptions
import pe.gob.reniec.dnie.pdf.StampTemplate

/** Only visual design selects a template; page, reason, location and TSA request remain independent. */
internal object SuiteTemplateSelection {
    fun matches(template: StampTemplate, options: PdfSigningOptions): Boolean =
        options.visible && options.appearance == template.apply(options).appearance
}
