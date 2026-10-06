# Firma de documentos — DNIe Evolucion 1.2.1

Trabajo realizado exclusivamente en `modest-bose-evolution`, sobre la organización modular y la lectura ICAO del fork. La base funcional 1.0.8 y la carpeta de Antigravity permanecen sin cambios.

## Actualización 1.2.1: PDF individual y lotes separados

- **Firmar PDF** selecciona un único PDF mediante el selector individual de Android. Elegir otro PDF sustituye el documento anterior solo después de validar la nueva importación. Guardar el resultado antes de cambiar. Los PDF protegidos se pueden firmar como binarios desde Lotes.
- **Lotes** tiene otro destino, otro ViewModel y otra cola; admite PDF y firmas separadas de otros formatos. Las dos colas no comparten documentos, resultados ni autorizaciones. Sí reciben los datos públicos del certificado y la imagen ICAO de la lectura actual.
- El editor se abre mediante **Colocar sello · pantalla completa**. Se mueve con un dedo, se escala pellizcando con dos y se cambia el ancho/alto con los puntos del borde o la esquina. Se retiraron las barras de ancho y alto. Las coordenadas permanecen dentro de la página.
- **Aplicar** guarda la página y posición del borrador; **Cancelar** las descarta. La navegación de páginas permanece dentro del editor. El marco y los puntos de selección son controles del editor y no aparecen en el PDF.
- La tarjeta visual tiene fondo blanco, borde y texto negros y fuentes estándar PDF Courier/Courier Bold. `StampTextLayout.kt` envuelve palabras y palabras largas en líneas nuevas, sin truncar ni añadir puntos suspensivos. Si la altura elegida es insuficiente, adapta el tamaño tipográfico para conservar el texto completo; ampliar el sello mejora su legibilidad.
- **Solo manuscrita** dibuja exclusivamente la imagen DG7, conservando su proporción. No añade texto, nombre, fecha, fondo ni borde. Conserva los píxeles de la imagen original, incluido su propio fondo si lo tiene. La firma criptográfica, su certificado y los metadatos propios de la firma digital siguen en el PDF.
- Los campos se ocultan en Solo manuscrita y recuperan su configuración al volver a un estilo con datos. Tanto Solo manuscrita como la opción de imagen con datos requieren lectura ICAO y confirmación del titular. Cambiar la identidad de origen invalida esa confirmación, aunque los bytes de imagen coincidan.

Validación de 1.2.1: **182 pruebas JVM aprobadas** (núcleo 45, protocolo 56, ICAO 27, PDF 20, app 34), APK compilado y pruebas Android compiladas. Las pruebas Android y los gestos aún no se ejecutaron en el teléfono en esta entrega. Versión `1.2.1-signing-dev`, código 13.

## Funciones añadidas

- Selección múltiple de archivos mediante el selector de Android; copia privada de los originales antes de solicitar CAN/PIN. Límite: 50 documentos, 256 MiB por documento, 512 MiB por lote.
- Configuración independiente por PDF: página, sello visible o invisible, estilo Tarjeta/Minimalista/Manuscrita, posición y tamaño. También se puede copiar la configuración a los PDF pendientes; la página se ajusta al número de páginas de cada archivo.
- Vista previa del PDF con el sello del mismo renderizador utilizado al firmar. Toque para colocarlo, arrastre para moverlo, controles de ancho/alto, acceso directo a página y posiciones habituales. Las coordenadas se refieren a la página visible, considerando CropBox y rotaciones de 0/90/180/270 grados.
- Campos opcionales: DNI, fecha, hora, motivo y lugar. El nombre real siempre procede del certificado leído durante la firma. La hora corresponde al reloj del dispositivo en zona América/Lima; esta versión no añade una autoridad de sellado de tiempo (TSA).
- Inclusión opcional de la imagen manuscrita del bloque ICAO DG7. Se decodifica JPEG/JPEG2000 y se prepara como PNG en memoria. Es un elemento visual del sello; la operación criptográfica sigue realizada con la clave privada del DNI.
- Antes de usar DG7 se pide confirmar que la imagen corresponde al titular. Si DG11 entrega número personal se compara con el DNI del certificado; una discrepancia impide firmar. Un hash DG7 que no coincida con EF.SOD impide usar la imagen. La comprobación de hashes no equivale a validar la cadena de confianza del emisor.
- Firma masiva secuencial. Una aprobación prepara una instantánea de los archivos y sus opciones; una sesión verifica PIN, lee el certificado y firma cada archivo. No se hacen reintentos automáticos del PIN.
- Firma incorporada en PDF mediante guardado incremental. Se corrigió el índice de página de SignatureOptions (base cero). La incorporación conserva criptográficamente firmas anteriores; se rechaza la certificación DocMDP con permiso P=1. El tratamiento de otros permisos y documentos particulares debe revisarse con su verificador.
- Firma CMS/CAdES separada `.p7s` para cualquier contenido binario: documentos Office, texto, imágenes, ZIP y otros formatos. No convierte ni modifica los originales. Un PDF normal permite elegir este modo; un PDF protegido se incorpora como archivo binario con firma separada, sin descifrarlo.
- Comprobación local de la firma RSA devuelta por el chip contra el hash solicitado y el certificado del titular antes de publicar un resultado. Los `.p7s` también se verifican como CMS antes de escribirlos.
- Resultados individuales, guardado/compartido y ZIP del lote. Para `.p7s`, compartir y ZIP incluyen el original exacto. El ZIP incorpora certificados públicos y una descripción de los contenidos.
- Exportación CER/PEM del certificado público de firma leído desde Certificados o durante la operación de firma. Los otros certificados siguen disponibles para exportación en su pantalla propia; las claves privadas no son exportables.
- Ante fallo local de un documento, los siguientes pueden continuar. Ante pérdida NFC, fallo de protocolo o respuesta criptográfica inválida, se detiene la sesión y se conservan los resultados completos; el resto queda pendiente. Reanudar exige una nueva aprobación y conexión. Cancelar o salir de la actividad cierra la sesión. El transporte y el transceptor cerrados rechazan comandos posteriores y no reabren automáticamente una sesión cancelada.

## Organización del código

| Responsabilidad | Archivos |
| --- | --- |
| Verificación criptográfica / formatos arbitrarios | `dnie-core/.../CardSignatureVerifier.kt`, `DetachedFileSigner.kt` |
| Opciones y apariencia PDF | `dnie-pdf/.../SignatureAppearance.kt`, `ModernStampRenderer.kt`, `VisualStampRenderer.kt`, `PadesSignerEngine.kt` |
| Importación, metadatos, cola y exportación | `app/.../domain/signing/SigningModels.kt`, `DocumentImportService.kt`, `PdfDocumentMetadata.kt`, `BatchSigner.kt`, `SignedBatchExporter.kt` |
| Interfaz, estado y vista previa | `app/.../ui/signer/SignerScreen.kt`, `SignerUiState.kt`, `SignerViewModel.kt`, `PdfPlacementEditor.kt` |
| Puente de datos públicos entre funciones | `app/.../ui/navigation/IdentitySignatureBridge.kt` |

Se mantienen IAS → PKI → PACE → PKI protegido, MSE/PSO, validación MAC/SSC y GET RESPONSE físico de la implementación validada. ICAO utiliza otra conexión; no se mezcla con la sesión de firma. Los archivos de trabajo PDF emplean un presupuesto de 32 MiB para el almacenamiento temporal de PDFBox, con respaldo a disco.

Los originales/resultados están en la caché privada de la app. Vaciar el lote elimina sus copias locales; los archivos guardados en un destino elegido por el usuario permanecen allí. La cola no se recupera tras terminar el proceso o perder la caché: guardar/exportar los resultados antes de cerrar. La imagen manuscrita y las opciones de la cola no se guardan como datos persistentes. No se añaden registros en claro de imágenes ni datos ICAO.

## Validación

Validación de la entrega anterior, 1.2.0: **173 pruebas JVM aprobadas** (núcleo 45, protocolo 56, ICAO 27, PDF 13, app 32), compilación del APK y compilación de las pruebas Android. El APK de la base 1.0.8 conserva su SHA-256 `B5E3754E409A5BED57E01A4810C7FC5F1D0E1597E44C8B1E3E0D4E21A571BA94`.

Ejecutar con JDK 17 y Android SDK:

```powershell
.\gradlew.bat :dnie-core:test :dnie-protocol:test :dnie-icao:test :dnie-pdf:testDebugUnitTest :app:testDebugUnitTest :app:exportBranchApk :app:compileDebugAndroidTestKotlin --console=plain
```

Las nuevas pruebas comprueban firmas separadas de contenido binario y vacío, rechazo de respuestas inválidas, detección de contenido alterado, rotaciones y recortes, selección real de la tercera página, dos firmas incrementales verificables, campos ocultos, límites de posición, rechazo DocMDP P=1, firma de PDF protegido como binario, una verificación PIN por lote, interrupción parcial, errores locales, identidad discrepante y exportación ZIP sin colisiones de nombres.

Se añadieron pruebas Android de navegación y renderizado de una imagen PNG dentro del sello. En esta entrega se compilan, pero no se ejecutan contra el teléfono. El funcionamiento del lote sobre el DNI real y la apariencia final de DG7 requieren la prueba física: las pruebas JVM usan una tarjeta RSA simulada y no demuestran que todos los perfiles RENIEC mantengan la autorización PIN para varios PSO.

## Prueba manual recomendada

1. Instalar el APK del fork; abrir Identidad y leer el DNI si se desea el sello manuscrito.
2. Abrir Certificados para leer/exportar el certificado y ver el nombre real en la vista previa.
3. En Lotes añadir dos PDF y un archivo de otro formato. En Firmar PDF elegir un único PDF. Seleccionar cada PDF, escoger campos y estilo; abrir el editor a pantalla completa, elegir página, mover/redimensionar y pulsar Aplicar. Confirmar el titular si se usa DG7. Probar Cancelar y verificar que no modifica la configuración.
4. Autorizar el lote con CAN/PIN y mantener el DNI inmóvil. Guardar el ZIP antes de vaciar el lote.
5. Verificar los PDF en los verificadores usados con la versión anterior. Para `.p7s`, emplear un verificador CMS/CAdES que acepte firma separada y el original correspondiente; no todos los verificadores orientados a PDF admiten ese formato.
6. Comprobar un lote interrumpido: los archivos completados deben poder guardarse y los pendientes requerir una nueva autorización. No hacer pruebas de PIN incorrecto con el DNI real.

APK: `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`. Paquete: `pe.gob.reniec.dnie.evolution.debug`. Versión: `1.2.1-signing-dev`, código 13.
