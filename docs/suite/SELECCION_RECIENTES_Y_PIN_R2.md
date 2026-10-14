# Suite: archivos de Recientes y autorización PIN · R2

2026-10-13, America/Bogota. Checkpoint `checkpoint/pre-suite-input-r2`. Continúa QR nativo R1 a partir del fallo real comunicado por el usuario: sin vista previa PDF y cierre de la sesión Windows con IllegalArgumentException después del diálogo de PIN.

## Causa comprobada

La lista de documentos contenía una ruta virtual de Windows **Recent** terminada en `.pdf`. Ese archivo no existía: en disco había un `.pdf.lnk` cuyo destino era un PDF existente en Descargas. La vista previa intentaba abrir el alias inexistente. Al firmar, DesktopWorkspace.validate rechazaba esa misma ruta antes de ContinuousSigningClient.begin, por lo que este fallo local no enviaba PIN ni comenzaba una operación de tarjeta. SuiteController trataba ese error como pérdida de conexión y cerraba la sesión.

Se identificó la entrada mediante una sonda temporal local del proceso Swing que solo leyó rutas seleccionadas/estado de la interfaz, sin inspeccionar campos de contraseña. Las sondas y sus archivos permanecen fuera de Git, en D:. La corrección resolvió ese mismo alias a su destino y PDFBox renderizó la primera de 11 páginas a 918×1188. En la GUI abierta se comprobó `1 / 11` y la imagen presente con esas dimensiones, sin exportar su contenido visual.

## Cambios

- `DesktopFileSelection` es el adaptador de selección: conserva el objeto File del selector, resuelve shell links con las APIs públicas [FileSystemView.isLink/getLinkLocation de Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/swing/filechooser/FileSystemView.html#getLinkLocation(java.io.File)) y contempla la extensión `.lnk` oculta exclusivamente dentro de Recent. Rechaza enlaces de sistema de archivos, destinos ausentes, directorios y ciclos; límite de ocho pasos. El motor conserva NOFOLLOW_LINKS.
- SuiteController resuelve la selección fuera del EDT y devuelve destinos reales a la lista. Los archivos rechazados se avisan antes de autorizar; no se manda un acceso directo a firmar como si fuera el documento.
- Se comprueban entradas/tamaños antes de mostrar PIN. DesktopSuiteSession vuelve a comprobarlos antes de BEGIN. Un error en este segundo preflight se distingue con DesktopInputUnavailableException y conserva la sesión; un fallo después de iniciar el protocolo sigue cerrando de forma conservadora, sin replay.
- El callback de firma comunica explícitamente si se conserva la conexión. La vista ya no decide esto interpretando prefijos de mensajes.
- SigningPinPanel separa la presentación del PIN: rótulo, cantidad de archivos, campo de contraseña de altura explícita, instrucciones y limpieza al cancelar o entregar CharArray. Los motores PDF/CMS y la autorización de tarjeta mantienen su implementación.

## Pruebas y entrega

50 pruebas aprobadas, sin fallos ni omitidas: 12 UI, 29 escritorio, 9 orquestación. Incluyen acceso directo Windows creado por COM y alias de Recent alimentando PDFBox, rutas rotas/circulares, PIN extraído y borrado, botón QR real, y sesión TLS sintética que rechaza un archivo ausente antes de BEGIN y después firma dos trabajos válidos con la misma conexión. La sonda adicional sobre el PDF seleccionado por el usuario acredita lectura/vista previa, no firma nueva con DNI.

GUI y CLI compiladas en `D:/DNIeToolchain/Builds/suite-input-r2`; el acceso directo del escritorio **DNIe Suite** apunta a `signing-ui/install/dnie-signing-ui`. Se abrió esta distribución restaurando el PDF real seleccionado. El destino de resultados y la identidad PC permanecen en las preferencias existentes. Android y driver no se reinstalan. Resultados: [SUITE_ENTRADAS_R2_RESULTADOS.json](../operations/SUITE_ENTRADAS_R2_RESULTADOS.json).

Pendiente: repetir una firma física desde esta GUI con el DNI y comprobar su resultado. Las pruebas de transporte/tarjeta de esta iteración usan simulación; el archivo real solo se abrió para previsualización.
