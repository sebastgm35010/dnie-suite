# Motor de firma comprobado y base de una suite propia

Revisión: 2026-10-06. Alcance: código actual de Android y pruebas existentes. No se modificó el motor, no se instaló una versión nueva y no se abrió una sesión con el DNI durante esta revisión.

Este documento conserva la revisión previa al cambio. Primer incremento implementado el 2026-10-07: [motor compartido y lotes rc5](MOTOR_FIRMA_LOTES_RC5.md), con extracción JVM y renovación de autorización dentro de la sesión. Sus rutas nuevas sustituyen las ubicaciones descritas aquí cuando se indica una extracción.

## Dirección acordada

La siguiente línea de desarrollo es una suite propia de firma, con motor independiente de la interfaz. El lector PC/SC actual conserva su valor para ReFirma, Firma Perú y otras aplicaciones; simplificar su instalación sigue pendiente. Una edición portable LAN no es la prioridad elegida por el usuario.

La primera suite Windows puede usar el lector actual como adaptador de tarjeta. Eso permite validar el motor de documentos y su interfaz sin cambiar simultáneamente transporte, PACE y firma. Una conexión directa al teléfono sería otro adaptador futuro: no hace falta imponerla como requisito del primer firmador.

## Flujo que usa realmente Android

`MainActivity` llama a `SignerViewModel.onCardDetected(transceiver, context)`. El ViewModel toma la selección aprobada, ejecuta `BatchSigner` en IO y presenta los resultados. El coordinador selecciona PKI, verifica el PIN una vez, lee el certificado y firma secuencialmente los documentos aprobados. Cierra la tarjeta al terminar.

Para PDF, `PadesSignerEngine` prepara el documento y pasa el contenido de los ByteRange al constructor CMS. Para otros archivos, `DetachedFileSigner` calcula el hash por bloques y genera un CMS separado `.p7s`, conservando los bytes originales. Ambos caminos reciben una función que pide la operación privada al DNI.

Una falla local de un archivo permite continuar el lote. Una falla de tarjeta o de integridad interrumpe los restantes y conserva los resultados completados. Un PIN rechazado no produce reintentos automáticos.

## Archivos y reutilización

| Componente | Archivos principales | Situación |
| --- | --- | --- |
| CMS y firma separada | `dnie-core/.../crypto/CmsSignatureBuilder.kt`, `DnieContentSigner.kt`, `DetachedFileSigner.kt` | Kotlin/JVM; reutilizables sin UI Android. |
| Integridad de la operación RSA | `dnie-core/.../crypto/CardSignatureVerifier.kt` | Reutilizable; comprueba clave/hash de la firma, no confianza de la cadena. |
| Sesión de tarjeta | `dnie-protocol/.../nfc/DnieTransceiverInterface.kt`, `DnieTransceiver.kt`, `V3PkiSession.kt` | Contrato y protocolo JVM; conservar secuencia validada. El adaptador de transporte se selecciona por plataforma. |
| Coordinación de lotes | `app/.../domain/signing/BatchSigner.kt` | No importa Android, pero depende de contratos alojados en el módulo PDF Android. Extraer después de separar esos contratos. |
| Modelos del trabajo | `app/.../domain/signing/SigningModels.kt` | Separar documentos/resultados del estado de pantalla. Dependen hoy de `PdfSigningOptions`. |
| PDF y apariencia | `dnie-pdf/.../pdf/PadesSignerEngine.kt`, `SignatureAppearance.kt`, `ModernStampRenderer.kt`, `VisualStampRenderer.kt`, `StampTextLayout.kt` | Módulo Android con PDFBox Android. Opciones y geometría son separables; renderizado y motor necesitan adaptador PDF de escritorio. |
| Exportación | `app/.../domain/signing/SignedBatchExporter.kt`, `SigningFileNames.kt`, exportador de certificados del core | ZIP, nombres y CER/PEM aprovechables después de separar modelos. |
| Importación y presentación | `DocumentImportService.kt`, `SignerViewModel.kt`, UI Compose | SAF, Uri, Context y estado de pantalla propios de Android. No trasladarlos al motor Windows. |

Las rutas abreviadas de esta tabla están bajo `src/main/java/pe/gob/reniec/dnie` en cada módulo.

## Contrato criptográfico que debe conservarse

Existen dos hashes distintos:

1. SHA-256 del archivo original o de los ByteRange del PDF se coloca en el atributo CMS `messageDigest`.
2. Bouncy Castle construye los atributos firmados DER. `DnieContentSigner` calcula SHA-256 de esos atributos; ese digest de 32 bytes es el que recibe el callback de tarjeta.

La firma RSA devuelta se comprueba con el certificado antes de aceptar el resultado. No sustituir este contrato por «firmar directamente el hash del archivo» al implementar un adaptador Windows o remoto.

En la ruta V3 actual, `V3PkiSession` establece el entorno de firma con MSE y envía los 32 bytes a PSO CDS. Un comentario de `DnieContentSigner` menciona que el adaptador añade DigestInfo, pero no describe esa ruta V3: documentar/corregir el comentario al refactorizar, sin añadir un prefijo al APDU funcional. La verificación RSA sí espera el DigestInfo SHA-256 en el resultado PKCS#1.

PACE y mensajería segura permanecen debajo del contrato de firma. Preservar IAS → PKI → PACE → SELECT PKI protegido, validación MAC/token, SSC y GET RESPONSE físico antes de desenvolver la respuesta.

## Capacidades ya presentes

- PDF con firma incremental, sello visible o invisible y posición por página; admite apariencia con solo firma manuscrita.
- Campos configurables, renderizado y ajuste del sello; tratamiento de páginas rotadas y CropBox.
- CMS separado `.p7s` para cualquier archivo, incluido un PDF que no se pueda modificar.
- Lotes secuenciales con resultados individuales y una autorización explícita del conjunto.
- Exportación de certificados CER/PEM y ZIP con originales necesarios para verificar firmas separadas.

El motor PDF rechaza archivos cifrados para firma integrada y documentos con DocMDP que prohíbe cambios. Reserva espacio para CMS y guarda una revisión incremental, usando archivos temporales. La compatibilidad actual se demuestra en Android; no equivale a haber portado PDFBox a Windows.

## Separación propuesta, por orden

1. **Contratos neutrales de firma:** extraer opciones PDF, colocación, apariencia y modelos de documento/resultado a Kotlin/JVM. Mantener selección, progreso de pantalla e importación/exportación Android fuera de ellos. Ningún contrato debe importar PDFBox o Context.
2. **Casos de uso compartidos:** llevar `BatchSigner` y exportación neutral a un módulo `dnie-signing-core`. Inyectar sesión de tarjeta, motor PDF, almacenamiento y progreso; definir quién cierra la sesión y cómo se publica un resultado terminado.
3. **Consolidar Android:** conservar su motor PDF como adaptador. Eliminar la ruta antigua de firma del ViewModel solamente después de migrar sus pruebas a la ruta real de producción.
4. **Adaptador de escritorio:** implementar PDF con una biblioteca JVM de escritorio y sesión de tarjeta sobre el lector PC/SC existente. Validar interoperabilidad de CMS y PDF antes de crear una UI extensa.
5. **Suite Windows:** interfaz delgada para PDF, archivos/lotes, certificados y resultados. Elegir después JVM empaquetada o un servicio JVM local para la GUI .NET actual. Compartir Kotlin no significa que C# pueda cargarlo directamente; no duplicar el algoritmo CMS por conveniencia de UI.

El API actual del PDF firma mediante callback síncrono/suspendido durante la operación; todavía no expone fases independientes `preparar/completar`. Esa división puede servir a un firmador remoto, pero requiere diseñar custodia del documento preparado, cancelación y publicación atómica. No presentarla como una función existente.

## Deuda concreta encontrada

- `SignerViewModel` conserva un overload `onCardDetected(transceiver, inputStream, outputStream)` que firma directamente. No se encontró un caller de producción; sí pruebas que lo usan. Duplica coordinación y puede ocultar diferencias respecto del lote real.
- Modelos neutrales viven en módulos de presentación o PDF Android, bloqueando reutilización directa en escritorio.
- `StampTextLayout` usa métricas de fuente PDFBox; requiere un contrato de métricas o implementación por adaptador, no solo mover el archivo.
- La firma cruda se verifica tanto en el callback de `BatchSigner` como en el motor PDF. Aclarar responsabilidades antes de retirar la duplicación.
- Para escritorio, publicar resultados mediante archivo temporal y movimiento final; evitar que un error deje un destino truncado. Ampliar pruebas de cancelación durante PDF/IO y cierre de sesión al extraer los casos de uso.
- `CmsSignatureBuilder` acepta certificados CA opcionales, pero la llamada PDF actual no aporta automáticamente una cadena completa. No prometer que se incluye toda la cadena RENIEC.
- Fecha/hora de firma provienen del reloj local. No se encontró incorporación de TSA, OCSP o CRL en esta ruta. Un verificador futuro debe diferenciar integridad, confianza, vigencia y revocación; la comprobación RSA actual no resuelve esas dimensiones.

## Validación de la revisión

Gradle offline completó `:dnie-core:test`, `:dnie-protocol:test`, `:dnie-pdf:testDebugUnitTest` y `:app:testDebugUnitTest` filtrado a `BatchSignerTest`. Los reportes contienen 49, 86, 20 y 10 pruebas respectivamente: 165, sin fallos ni errores. Algunas tareas estaban actualizadas y reutilizaron resultados; el lote filtrado se ejecutó en esta revisión.

Las pruebas cubren CMS contra bytes originales, rechazo de firma inválida, PDF incremental/rotación/posición/DocMDP y lotes con errores locales, fallo de tarjeta y PIN rechazado. Son evidencia sintética complementaria a las firmas físicas previas, no una prueba nueva de firma Windows ni una verificación completa de confianza.

Log local fuera del repositorio: `D:/DNIeToolchain/Temp/signing-engine-review.log`. No se requiere APK nuevo para esta revisión documental.
