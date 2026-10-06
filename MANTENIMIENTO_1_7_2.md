# Primera entrega de deuda técnica — 1.7.2

## Checkpoint anterior

Antes de editar se creó `build/checkpoints/1.7.1-before-debt`: APK 1.7.1, ZIP de 395 archivos del árbol de trabajo y manifiesto. Incluye cambios sin commit; no depende de que HEAD refleje todo el desarrollo. ZIP verificado por CRC y cantidad de archivos. No contiene claves privadas, configuración local ni logs personales. La clave de firma de desarrollo permanece separada; el checkpoint no es un respaldo de los datos del teléfono.

- APK SHA-256: `1ac278b56119c4dddd564e5f01251afbeb94404d39cf29b4c9db21d112d1cc34`.
- Fuentes SHA-256: `20b36af6e6efb076d2d9d21d567b3dae52387605f4978b6a11c4994aec01a81e`.

La base clausurada y Antigravity no se modificaron.

## Cambios concretos

### Biblioteca de bóveda

La búsqueda ya no vuelve a ordenar el inventario ni reconstruye las tarjetas y su política de vista previa con cada tecla. `VaultInventoryIndex` prepara una copia del inventario público y sus tres órdenes una vez por actualización; el filtro conserva términos múltiples, comparación sin distinguir mayúsculas, carpetas, papelera y orden estable en empates. Usa una expresión regular compartida y compara directamente nombre/carpeta/etiquetas sin construir listas por término.

`VaultLibrarySnapshot` adapta ese inventario a tarjetas de presentación una vez, fuera del hilo principal. El ViewModel conserva coordinación/cancelación y publica un estado completo. La búsqueda actualiza inmediatamente el campo escrito y espera 120 ms de pausa antes de filtrar; carpeta, orden y papelera se aplican sin esa espera. Una generación impide que un resultado anterior sobrescriba el filtro más reciente. Los chips de carpetas tienen claves estables.

La preparación ocupa referencias adicionales para tres órdenes del inventario. No contiene contenido descifrado, claves ni credenciales. Reduce trabajo repetido por diseño; **la mejora de fluidez en teléfono aún requiere medición o confirmación del usuario**. No se atribuye una mejora de navegación general a este cambio.

### Importación de firma

- `DocumentImportService` y `PdfDocumentMetadata` pasan de `domain/signing` a `data/signing`: SAF, contexto y PDFBox son adaptadores, no política de negocio.
- `SigningFileNames` mantiene la política pura compartida de nombres y reutiliza su Regex.
- `ImportSigningDocuments` coordina aceptación, límites y errores parciales mediante funciones inyectadas de importación/descarte. No depende de Android ni Compose. El ViewModel publica el resultado y conserva el estado de edición.
- El cálculo de tamaño total se acumula una vez en lugar de sumar repetidamente todos los documentos. Sustituir un PDF considera únicamente el documento nuevo; el lote que se reemplazará ya no provoca rechazo por cantidad/tamaño.
- Descarte y limpieza de la sustitución se ejecutan en IO. La propiedad de nuevos archivos se transfiere al estado antes de suspender la limpieza de los anteriores. Los nuevos archivos se limpian si la operación se cancela o deja de pertenecer a su generación.
- El adaptador limpia también si el dispatcher cancela la entrega de un documento ya importado. No cambia la secuencia de firma, contenido de originales ni mecanismos criptográficos.

### Organización y límites

`docs/README.md` ofrece un índice por función, arquitectura, pendientes y entregas. Los documentos históricos conservan sus rutas y enlaces. `tools/check_architecture.py` verifica imports principales prohibidos entre capas y ciclos de dependencias Gradle; no modifica archivos. El dominio de `app` ya no importa Context/SAF/PDFBox. Esta verificación estática no detecta por sí sola reflexión, todos los nombres cualificados ni conducta en ejecución.

La investigación delegada está en `docs/architecture/PLAN_PUENTE_ANDROID_PC_2_0.md`. No se añadió un servidor, socket, driver ni integración con RENIEC a este APK.

## Deuda restante y siguiente etapa

1. Pruebas Android de SAF con proveedores que fallen al escribir/renombrar y con interrupción de proceso; comprobar limpieza y resultados parciales explícitos.
2. Separar limpieza/exportación restante de `SignerViewModel`, especialmente eliminación/reset que aún pueden tocar archivos en Main. Mantener propiedad de archivos y cancelación antes de extraer.
3. Medir navegación/escritura/desplazamiento con trazas comparables. El registro 5 no permite localizar todos los costes por sí solo.
4. Diseñar una reserva común de tarjeta antes del puente: un solo propietario de sesión, exclusión entre firma, bóveda, passkeys e ICAO, cierre ante cancelación y pérdida de transporte. Mantener MAC/SSC/GET RESPONSE en su propietario actual.
5. Revisar límites de ciclo de vida de mantenimiento/passkeys y ampliar pruebas negativas de entrega/cancelación, sin conservar autorizaciones reutilizables.

QR sigue con error interno 13 pendiente de diagnóstico y prueba PC real aplazada. Las pruebas físicas de migración de DNI y biometría siguen separadas de las simulaciones. Toda la deuda no queda saldada por esta entrega.

## Validación y APK

Compilación/exportación APK y compilación de pruebas instrumentadas completadas. Los XML de los ocho módulos solicitados registran **307 pruebas JVM, cero fallos, errores u omisiones**: app 61, bóveda 26, núcleo 49, protocolo 63, ICAO 27, PDF 20, núcleo passkeys 47 y adaptador passkeys 14. Las comprobaciones incrementales de Gradle reutilizaron resultados válidos en módulos sin cambios. No se ejecutó el emulador ni una prueba NFC física en esta entrega.

Las seis pruebas añadidas verifican semántica de búsqueda/empates e independencia del snapshot; reemplazo PDF con lote anterior lleno; límites y descarte de entradas; cancelación con limpieza; y continuación tras un archivo fallido. Las pruebas existentes de firma/lotes siguen pasando tras mover sus adaptadores.

El script de límites de arquitectura pasó. Se compararon los fuentes contra el ZIP del checkpoint: los cambios se limitan a los archivos documentados, sin tocar el protocolo. APK `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`: `1.7.2-maintenance`, code 32, 30.089.376 bytes, paquete `pe.gob.reniec.dnie.evolution.debug`. Firma verificada; certificado SHA-256 `16cda33b24d0e5f3fea46b766f9e9f198a581721464938bc8068fda398036350`, igual a entregas anteriores. No se instaló ni controló el teléfono.

APK SHA-256: `92770449E9527D0B299C0FD3DBFB2A73A6D0F09113AC9D799D4F530C6814860E`.
