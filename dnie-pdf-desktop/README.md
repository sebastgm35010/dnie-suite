# PDF de escritorio

Adaptador JVM modular para firma integrada invisible, con PDFBox 3.0.8. Reutiliza CMS/RSA del núcleo; no contiene transporte, UI, PIN ni motor Android.

Contrato, límites y prueba física: [PDF_INVISIBLE_H3.md](../docs/suite/PDF_INVISIBLE_H3.md).

## Dependencias y avisos

PDFBox, PDFBox IO y FontBox 3.0.8, más Commons Logging 1.4.0: licencia principal Apache-2.0. Los avisos/licencias completos se extraen sin modificaciones de los JAR resueltos y se conservan en `third-party/`, incluidos avisos de datos Adobe/Unicode, perfiles CC0 y fuentes SIL OFL. La distribución copia estos archivos a `licenses/pdf/`; cada JAR también conserva sus metadatos originales. No se agregan codecs opcionales JBIG2/JPEG2000 ni dependencias iText/AGPL.

Fuentes oficiales: https://pdfbox.apache.org/ y https://www.apache.org/licenses/LICENSE-2.0 . El inventario describe estas dependencias nuevas; no sustituye los avisos de las dependencias JVM existentes.
