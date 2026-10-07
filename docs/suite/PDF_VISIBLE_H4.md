# H4 · Sello visible de escritorio

2026-10-07. Base física: H3, dos PDF integrados y un CMS real verificados .NET. Usuario confirmó cierre manual Ctrl+C; no hay incidente de conexión en esa prueba. Checkpoint previo `checkpoint/pre-suite-pdf-visible`.

## Motor y alcance

`JvmPdfSigner` (antes `JvmInvisiblePdfSigner`) conserva firma externa incremental/CMS/RSA. `DesktopStampRenderer` crea `/AP /N` del widget de la nueva firma antes de calcular el contenido firmado; no aplana páginas ni pinta después de firmar. Contrato compartido `PdfSigningOptions`/`SignatureAppearance` y `NormalizedStampPlacement`, sin UI/Android/transporte en el renderizador. `DesktopPdfOptions` convierte argumentos a ese contrato y se comparte entre demo, remote y suite continua.

Sello: fondo blanco, borde negro en estilo card, texto negro con Liberation Sans embebida (recurso/licencia ya incluidos en PDFBox); minimal sin borde. Nombre obligatorio, DNI/fecha/hora/motivo/lugar opcionales. Fecha local America/Lima, no TSA. Ajuste de líneas y tamaño entre 6 y 10 puntos; si no cabe, falla documentalmente antes de SIGN, sin cortar texto. No interpreta etiquetas Markdown ni texto arbitrario como comandos PDF. Caracteres no soportados y controles se rechazan. Página inválida no firma. Campos omitidos de motivo/lugar tampoco se incluyen en metadatos de firma.

Ubicación fraccionaria desde esquina superior izquierda del CropBox visible, con giros 0/90/180/270. Sello por defecto: left .36, top .82, width .60, height .14, primera página. La misma configuración se aplica a todos los PDF del lote. La firma invisible sigue siendo el modo por defecto. PDFs certificados DocMDP se rechazan en modo visible por prudencia; FieldMDP/cifrados continúan no admitidos. No garantiza conformidad PDF/A.

No incorpora aún visor, posición con ratón ni extracción de manuscrita del teléfono hacia la PC. Esta entrega admite card/minimal; no simula soporte de estilos INK/HANDWRITTEN_ONLY. No cambia SSC/PACE, PIN ni transporte, emparejamiento o lector compatible.

## Uso

Añadir al mismo comando de Suite:

```powershell
--pdf-mode visible --pdf-page 1 --pdf-style card --pdf-fields dni,date,time,reason,location --pdf-reason 'Doy conformidad' --pdf-location 'Lima, Perú'
```

Opcional: `--pdf-box '0.36,0.82,0.60,0.14'`, números fraccionarios con punto. `--pdf-fields ''` deja solo encabezado/nombre; cada campo indicado es opcional. No enviar PIN/CAN por argumentos. `--pdf-mode invisible` produce PDF sin sello y `detached` produce .p7s. `--help` documenta opciones. En suite las opciones PDF van después de los cuatro pares de conexión/identidad existentes.

## Validación

Pruebas de cuatro giros y CropBox desplazado: posición/tamaño renderizados, asociación widget/campo/página y CMS válido. Segunda firma visible en otra página conserva ambas firmas verificables. Página/rectángulo insuficiente falla sin SIGN/salida parcial. Caso de uso de escritorio verifica widget visible; CLI verifica campos/posiciones válidos y rechaza entradas/duplicados inválidos. Regresión invisible/lotes/protocolo conservada. Evidencia sintética en `D:/DNIeToolchain/PdfVisibleEvidence`, sin datos DNI reales; imágenes de los cuatro giros inspeccionadas visualmente.

Verificador .NET usa ahora etiqueta `PDF_INTEGRATED` tanto para visible como invisible; no deduce visibilidad de un nombre de archivo. Confianza/revocación siguen fuera de su alcance. Falta validar sello real con el DNI usando el comando anterior; app Android instalada 56 sirve sin APK nuevo.

Referencia primaria del API consultada: https://github.com/apache/pdfbox/blob/3.0.8/examples/src/main/java/org/apache/pdfbox/examples/signature/CreateVisibleSignature2.java . Código propio del adaptador, sin copiar implementación del ejemplo.

Regresión final: 128 pruebas, cero fallos/errores/omisiones entre PDF desktop, signing desktop/core/link y app. `:app:assembleDebug` satisfactorio, sin cambios Android ni nueva instalación. Logs `D:/DNIeToolchain/Temp/suite-visible-final-checks.log` y `suite-visible-packaging.log`. Distribución actualizada en la ruta habitual.

Ejecutable demo generó un PDF visible integrado, trabajo `dc2a799e-5152-499c-86f6-0bfafd32f167` en `D:/DNIeToolchain/PdfVisibleEvidence/jobs`; .NET verificó integridad, certificado sintético y rechazo de alteración. Render final a 144 DPI inspeccionado, sin recortes de texto; no confundir con firma personal DNI. Checkpoint final `checkpoint/suite-pdf-visible`.
