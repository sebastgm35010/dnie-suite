# Herramientas de bóveda — 1.5.0-vault-tools

Implementación en el fork de evolución, código de versión 22. La versión funcional 1.4.5 se respalda en `app/build/outputs/apk/checkpoints/dnie-evolution-1.4.5-refactor.apk`. No se modifican la base clausurada ni Antigravity.

## Funciones y uso

1. **Organización.** En cada archivo, «Carpeta y etiquetas» asigna una carpeta lógica y hasta 16 etiquetas. Una carpeta aparece al asignar su primer archivo. La biblioteca permite buscar por nombre/carpeta/etiquetas, combinar términos y ordenar por fecha, nombre o tamaño. Las carpetas son agrupaciones lógicas, no rutas del sistema. Los nuevos archivos y notas se asignan a la carpeta seleccionada.
2. **Cifrado por lotes.** «Elegir archivos y cifrar lote» abre el selector múltiple. Se procesa un archivo por vez, con progreso y recuento de fallos; un origen ilegible no bloquea los siguientes. La cancelación conserva los contenedores terminados y detiene los restantes. Máximo 1000 selecciones por lote y 1 GiB por archivo. No se borran originales.
3. **Respaldo completo.** «Respaldar todo» genera `boveda-completa.dniebackup.zip` en el destino elegido. Incluye todos los contenedores, nombres, carpetas, etiquetas y estado de papelera. «Restaurar respaldo» añade copias con nuevos IDs locales, evitando sobrescribir archivos existentes. Reimportar el mismo respaldo crea duplicados deliberadamente. El certificado público vinculado y el consentimiento no se trasladan: se puede descifrar con el DNI destinatario y, para cifrar archivos nuevos, vincularlo de nuevo.
4. **Vista previa protegida.** «Ver con DNI · vista privada» selecciona el archivo sin pedir destino externo. CAN, PIN y NFC recuperan la clave por el flujo existente, se autentica el contenido completo y se abre un visor interno. PDF tiene navegación por páginas; imágenes se muestrean para limitar memoria; el texto se muestra como texto literal, sin ejecutar HTML. No se usa una aplicación externa ni WebView. Vista previa hasta 32 MiB; texto mostrado hasta 256 KiB; formatos admitidos: PDF, PNG/JPEG/WebP y MIME `text/*`. Los demás formatos conservan la opción de descifrar a un destino externo.
5. **Entrada desde Compartir.** Android ofrece DNIe Evolución para compartir uno o varios archivos. La bóveda muestra el número de elementos recibidos y solicita «Cifrar recibidos». No cifra automáticamente. Solo admite URI `content://`, con acceso temporal concedido por la aplicación origen; no persiste la selección. Si el proveedor revoca el acceso, el archivo puede fallar y debe seleccionarse de nuevo.
6. **Ficha del archivo.** Muestra tamaño cifrado, fecha local de incorporación, carpeta, estado, destinatario SHA-256 y fechas informativas de apertura verificada/copia cifrada guardada. Importar un respaldo reinicia las fechas de verificación y respaldo local; no se confía en esos indicadores del origen. Estas fechas no prueban confianza en la identidad del emisor ni integridad actual: cada apertura autentica nuevamente el contenedor.
7. **Papelera.** Eliminar normalmente mueve a una papelera lógica conservando el contenedor cifrado. Desde Papelera se recupera o se elimina definitivamente con confirmación. No hay borrado automático ni promesa de borrado forense.
8. **Notas cifradas.** Permite crear notas `.txt` con el mismo motor y leerlas mediante DNI en el visor. El nombre es visible y el contenido está cifrado. El borrador permanece solo en memoria, sin saved-state ni preferencias. Esta entrega crea notas; no incorpora edición de una nota existente. El editor admite hasta 65536 caracteres; el caso de uso rechaza cuerpos UTF-8 mayores de 1 MiB.

No se implementa biometría. El DNI continúa siendo necesario para abrir contenido. No se añade un proveedor de passkeys, recuperación alternativa, segundo destinatario ni descifrado de varios archivos con un solo PIN.

## Límites entre capas

| Capa | Archivos y responsabilidades |
| --- | --- |
| Motor JVM `dnie-vault-core` | `VaultCatalog`: etiquetas acotadas y persistencia; `VaultInventory`: búsqueda y orden; `VaultBackup`: archivo ZIP, validación y rollback; `VaultContainer` y `VaultFileStore`: cifrado, autenticación y commits de archivos. |
| Casos de uso `app/domain/vault` | `VaultBatchImportUseCase`: continuar tras fallos independientes y propagar cancelación; `VaultNoteDraft`: validar una nota; `VaultFileDetails`: resultado de lectura independiente del repositorio. |
| Adaptadores `app/data/vault` | `AndroidVaultRepository`: serialización de IO, SAF y coordinación del motor; `VaultShareIntake`: intents y URI; `VaultPreviewFiles`: temporales privados y tickets; `VaultPreviewReader`: decodificación PDF/imagen/texto fuera del hilo principal. |
| Presentación `app/ui/vault` | ViewModel: estado, acciones y sesión NFC; componentes de biblioteca, diálogos, tarjetas y pantalla: presentación; `VaultPreviewActivity`: ciclo de vida del visor y bloqueo de captura. |

El grafo de navegación observa únicamente el contador de archivos recibidos para abrir la bóveda: escribir CAN/PIN no invalida el grafo completo. La búsqueda/orden se ejecutan fuera del hilo principal con cancelación de resultados obsoletos. IO, cifrado, ZIP y decodificación no se ejecutan en componentes Compose.

## Formato, integridad y compatibilidad

El formato criptográfico `DNIEVLT1` y sus claves por archivo no cambian. Los contenedores anteriores se siguen leyendo. No se cambia IAS → PKI → PACE → PKI protegido, verificación PIN, SSC/MAC ni la operación privada del DNI.

La organización se guarda en archivos privados `.labels` dentro de un catálogo separado. Son metadatos visibles, como los nombres ya autorizados por el usuario. El esquema local versión 2 conserva lectura de la versión 1. Las etiquetas no deciden rutas, identidades criptográficas o permisos de descifrado.

El respaldo ZIP contiene un marcador de versión con recuento, pares `.info`/`.dnievault` identificados por UUID y un marcador final obligatorio. No comprime datos ya cifrados. El importador no extrae rutas ZIP: valida los nombres y escribe mediante el almacén. Limita a 10000 objetos, 16 GiB de contenedores en total y límites por entrada. Rechaza entradas adicionales, repetidas, estructura incompatible o cierre incompleto; ante error/cancelación elimina los objetos añadidos por esa importación, conservando los existentes.

Un cierre forzado del proceso durante importación puede dejar objetos ya terminados: no hay transacción durable global entre miles de archivos. El marcador final evita aceptar silenciosamente un archivo que termina antes de todos los pares declarados. El ZIP y su catálogo no son una firma criptográfica de la colección. La autenticación del contenido sigue exigiendo el DNI al abrir cada contenedor; importar valida estructura y límites, no descifra ni certifica integridad criptográfica.

Un proveedor SAF puede dejar un respaldo parcial si falla o se cancela la escritura. Solo se marca una copia como guardada tras completar/cerrar su salida. El destino queda bajo control del proveedor externo; el resultado de escritura no constituye una auditoría futura del respaldo.

## Ciclo de vida del visor

El descifrado se autentica primero en el staging privado existente. Solo después se copia a `cacheDir/vault-viewer-private/<UUID>.plain`. La carpeta no está expuesta por FileProvider. El visor es una Activity no exportada y recibe un ticket opaco, nunca una clave ni una ruta arbitraria. El transporte NFC se cierra antes de procesar el archivo.

Al perder foco/visibilidad se oculta el contenido, se elimina el temporal y se termina el visor. Cerrar o rotar el dispositivo requiere volver a autorizar la apertura. Los temporales abandonados por muerte del proceso se eliminan al arrancar la app. `FLAG_SECURE` bloquea capturas y pantallas no seguras según el soporte Android; no promete protección frente a un sistema comprometido. No se garantiza borrado forense de flash ni de todas las copias de memoria del runtime.

Referencias de plataforma: [PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer), [FLAG_SECURE](https://developer.android.com/security/fraud-prevention/activities), [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files).

## Validación de esta entrega

La regresión completa registra 228 pruebas JVM sin fallos, errores ni omisiones: núcleo 49, protocolo 61, bóveda 23, ICAO 27, PDF 20 y app 48. Se añaden 9 pruebas del motor de biblioteca y 5 pruebas de casos de uso; cubren respaldo/descifrado posterior, rollback, marcador final, compatibilidad del catálogo, búsqueda, independencia de fallos, cancelación y validación de notas. Gradle reutilizó los resultados válidos de módulos sin cambios y ejecutó las pruebas de los módulos afectados.

Los fuentes de pruebas instrumentadas Android compilan. Se prepararon pruebas de autenticación previa a vista previa, límites de texto/imagen, limpieza ante cancelación/cierre, bloqueo de captura y recepción de URI. No se ejecutaron en un teléfono: esa comprobación y la apertura NFC real de esta versión están pendientes.

El APK se exporta a `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`. Esta entrega no se instala automáticamente y conserva el APK 1.4.5 validado por el usuario como checkpoint.

Compilación final completada después de los ajustes del visor y navegación. `apksigner` verifica firma v2; `aapt` confirma paquete `pe.gob.reniec.dnie.evolution.debug`, código 22 y versión `1.5.0-vault-tools`. SHA-256 del APK: `A7997B262E83FE3C68A712788A7B1ED4B9D356689D56A949058E34630C0BCC29`. La revisión `git diff --check` terminó sin errores.

Comprobación física pendiente: organizar un archivo y reiniciar; cifrar/cancelar un lote; exportar/importar respaldo y abrir una copia importada con el DNI; abrir nota/PDF/imagen, cambiar página y ampliar con los dedos; salir del visor y comprobar que exige una nueva autorización; compartir archivos desde otra app; comprobar firma, ICAO y restauración a SAF existentes. Probar con copias conservando originales y respaldos anteriores.
