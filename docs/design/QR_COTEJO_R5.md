# Sello QR y cotejo Android · R5

2026-10-14, America/Bogota. Solicitud: «continuemos con el qr». Checkpoint previo: `checkpoint/pre-stamp-qr-r5`. Continúa el QR diferido de Aether R4. No modifica driver, servicio Windows, protocolos de emparejamiento, PACE, CMS ni TSA.

## Función y límites

Al seleccionar la insignia QR, Android y Suite Windows incorporan un registro público versionado al sello vectorial. Ambos motores calculan las huellas SHA-256 completas del certificado DER real y del archivo de entrada exacto a esta firma, mientras copian el original a su área temporal. El certificado real también determina nombre, DNI y serie. El motor reemplaza cualquier metadata aportada por el llamador. Los lotes obtienen datos propios por documento; el modelo no se guarda entre archivos ni en preferencias visuales.

El QR final queda dentro de la apariencia cubierta por la firma del PDF. Sin embargo, el contenido QR aislado es una declaración que cualquiera puede copiar o fabricar: ni la marca de final ni sus huellas constituyen una firma autónoma. La aplicación nunca presenta su lectura como validación del PDF o autenticidad de una impresión.

Las vistas previas usan una marca explícita de muestra y huellas de relleno. El cotejador no permite comparaciones ni consulta de confianza con estas muestras. Un QR final leído sin documentos también se presenta solo como datos declarados.

## Uso

1. Firma PDF o Lotes: elegir insignia QR y tamaño suficiente; firmar por el flujo habitual. Suite usa el mismo motor de disposición y formato público.
2. Android → Mi DNIe → **Cotejar QR**. Escanear con la cámara o pegar el contenido. No se abren enlaces ni se usa el QR como emparejamiento Bridge/Suite.
3. Seleccionar el **archivo de entrada anterior a esta firma**. Se cotejan huella completa y longitud. El PDF firmado es otro archivo y su huella difiere; no se extraen automáticamente revisiones anteriores.
4. Seleccionar el certificado público CER/PEM del firmante, exportable desde Mi DNIe. Se comparan huella DER, serie y, por separado, nombre/DNI declarado frente al certificado realmente importado.
5. Solo si certificado e identidad coinciden se ofrece el panel existente de consulta manual TSL Perú/PKIX/OCSP/CRL. Describe el estado actual del certificado; no verifica el CMS del PDF ni demuestra validez histórica al firmar. Para validar criptográficamente el documento se continúa utilizando un validador de firmas.

No requiere DNI ni PIN para escanear/cotejar archivos públicos. No añade biometría ni passkeys. Los certificados/archivos privados de prueba no forman parte del producto.

## Formato público propio `DNIEQR1`

Registro ASCII canónico, separador `|`. No es un perfil QR oficial RENIEC/ETSI ni cambia PAdES B/T.

| Campo | Contenido |
| --- | --- |
| M | P: vista previa; F: generado por motor de firma. Sigue siendo una declaración al escanear. |
| C | SHA-256 completo del certificado DER, base64url sin padding (43 caracteres). |
| S | Serie hexadecimal mayúscula canónica. |
| D | SHA-256 completo del original, misma codificación. |
| L | Longitud exacta del original en bytes. |
| P | Perfil solicitado B/T. No demuestra que una TSA haya respondido. |
| N | Nombre, UTF-8 en base64url. |
| I | DNI opcional; respeta ocultar DNI. |
| F | Fecha/hora declarada opcional; respeta ocultar fecha/hora. |

La interfaz muestra las huellas en sus 64 dígitos hexadecimales. No hay hash abreviado, URL de consulta, token, PIN, CAN, clave privada ni firma manuscrita en el QR. Ocultar DNI no vuelve anónimo el certificado: su huella sigue siendo un identificador público. Elegir QR es una opción explícita del sello.

El parser rechaza claves repetidas/desconocidas, campos ausentes, serial/tamaño no canónicos, UTF-8 inválido, controles y marcas bidireccionales, huellas truncadas, padding/codificación no canónica y payloads de más de 1800 caracteres. Nombre/campos de texto: máximo 256 bytes UTF-8. Original: máximo 256 MiB, lectura por bloques con cancelación. CER/PEM importado: máximo 128 KiB. Los QR anteriores se rechazan como no cotejables; conservan su valor como resumen visual en PDFs antiguos.

El renderizador exige módulos de al menos 0,5 pt y margen blanco de cuatro módulos. Es una protección geométrica; la legibilidad de cámara/impresión depende del dispositivo, resolución y tamaño. Rechaza un sello demasiado pequeño antes de invocar al firmador privado y evita omitir silenciosamente el QR. Rúbrica e insignia QR comparten columna: se pide elegir una de ellas, conservando «Solo rúbrica».

## Capas

- `dnie-signing-core`: formato/parser, streaming SHA-256, comparaciones y disposición vectorial; sin tipos Android, PDFBox o Swing. Codificación base64url compatible con Android 24/25.
- `dnie-pdf` y `dnie-pdf-desktop`: derivación de metadata desde certificado/original reales y renderizadores existentes. Los callbacks CMS/RSA/TSA no cambian.
- `app/data/qr`: adaptador de cámara compartido y lectura SAF en IO. No copia archivos importados al almacén, registra payloads ni consulta URLs del QR.
- `app/ui/verification`: estado, cancelación por generación, vista Aether claro/oscuro y acciones. Cambiar QR elimina las evidencias anteriores; elegir un reemplazo elimina el cotejo previo incluso si el nuevo archivo falla.
- `app/ui/certificates`: reutiliza el panel de confianza ya existente. No duplica motores ni utiliza otro ViewModel como servicio.

La distribución/release conserva el diagnóstico de desarrollo excluido. `qrMetadata` es efímera y no entra en preferencias Suite. No se cambia la identidad instalada ni los emparejamientos.

## Verificación y pendientes

Resultados finales en `docs/operations/QR_R5_RESULTADOS.json`. Pruebas sintéticas distinguen la verificación criptográfica del PDF generado del cotejo ofrecido por la pantalla. No sustituyen una nueva firma NFC con DNI real ni una prueba física de cámara apuntando a pantalla/papel.

En el teléfono se observó que `android.graphics.pdf.PdfRenderer`, usado por el adaptador de vista previa de página, omite el widget de firma de un PDF sintético. La misma salida muestra su apariencia/QR con PDFBox Android y un renderizador externo con anotaciones, y se decodifica independientemente. La prueba final usa este renderizador con anotaciones; no se declara que el adaptador de preview de PDFs anteriores muestre todos sus sellos. Ese comportamiento previo se registra como pendiente separado, sin alterar las firmas para aplanarlas.

Pendiente posterior: validación de PDF/CMS completa dentro del cotejador, selección de firma/revisión en PDFs con varias firmas y extracción autenticada de certificado/original de esa revisión. No se incorpora una comparación heurística de prefijos ni se supone que el hash del original sea el del PDF final.
