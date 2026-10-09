# Diagnóstico directo y pruebas por capas · R1

2026-10-08. Usuario autoriza ampliar eventos, extraerlos sin exportación manual y evaluar interacción/rendimiento. Checkpoint previo: `checkpoint/pre-diagnostics-apk72`, sobre `3fbdec4`.

## Arquitectura y alcance

Contrato JVM `DiagnosticLog`/`DiagnosticEvent`: conserva hora de pared y añade reloj monotónico, hilo y UUID de proceso; spans incluyen capa, operación, duración e identificador de correlación. Los nombres son estáticos y no incluyen nombres de documentos, CAN, PIN, contraseñas, claves, contenido de archivos, MRZ ni mensajes arbitrarios de excepciones. Las fallas conservan cadenas de clases y marcos estructurales, incluidos proveedores externos. El reloj monotónico solo se compara dentro de un proceso.

Capas explícitas: `ui` (navegación/cuadros/heartbeat), `coordination` (cambios de estado/operaciones de bóveda), `adapter` (SQLite/SAF/repositorio), `protocol` (abrir PACE, verificar PIN, leer certificado, firmar en tarjeta), `engine` (PDF y sellado). El estado failed de un span significa que no completó, y puede incluir cancelación; correlacionar con coordinación/exception antes de clasificarlo como bug. Las entradas históricas sin capa quedan sin clasificar; no se atribuyen retrospectivamente. Una operación de motor puede contener otra de protocolo: las duraciones incluyen esperas y NO se suman como CPU ni prueban causalidad por coincidencia temporal.

Implementación debug: SQLite persistente sin vencimiento automático; escritura fuera de UI en transacciones de hasta 256 eventos, cola máxima 8192, contadores de pérdida y barrera de vaciado. `clear` invalida la generación anterior para que los pendientes no reaparezcan; solo el usuario borra el historial real. Pruebas de borrado usan bases aisladas. Una falla de persistencia impide declarar completa una exportación.

`RuntimeDiagnostics` detecta IO de disco/red del hilo principal mediante StrictMode y muestrea bloqueos mientras hay actividades iniciadas. No mata la app ni incluye mensajes de violaciones. `UiFrameDiagnostics` usa el presupuesto de la frecuencia de pantalla, registra cuadros de al menos 32 ms o dos presupuestos y agregados cada cinco segundos de cuadros activos. No es un contador exacto de todos los cuadros omitidos del sistema; arranque y pruebas instrumentadas afectan los resultados.

## Extracción directa

`DiagnosticExportProvider`, solo en `app/src/debug`, expone lectura por pipe en `content://pe.gob.reniec.dnie.evolution.debug.diagnostic-export/events`. Requiere `android.permission.DUMP`, no concede permisos URI y rechaza escritura/rutas adicionales. ADB shell autorizado lo lee; no se agrega listener LAN/Tailscale, carga automática a nube ni receptor de órdenes de firma. [Contrato ContentProvider de Android](https://developer.android.com/reference/android/content/ContentProvider).

El pipe añade un registro final `export_complete` con watermark. El colector valida JSON, orden, watermark y terminación antes de sustituir el archivo local. Fallos/truncamientos conservan la captura anterior. Lectura disponible cuando el teléfono está conectado mediante ADB autorizado; no es una conexión permanente con Codex si ADB desaparece o este chat no está ejecutando herramientas.

Desde la raíz del repositorio:

```powershell
python tools/android-diagnostics/collect.py --output D:/DNIeToolchain/Diagnostics/events.jsonl
python tools/android-diagnostics/analyze.py D:/DNIeToolchain/Diagnostics/events.jsonl --since-minutes 10 --output D:/DNIeToolchain/Diagnostics/analysis.json
```

JSONL personal y artefactos de prueba se guardan en D:, fuera de Git. La pantalla conserva exportación y borrado manuales. Release conserva el contrato de medición desactivado y sus stubs; carece del proveedor, StrictMode, heartbeat y backend SQLite de desarrollo.

## Hallazgos y correcciones

- Escritura individual por evento: agrupada por transacciones para reducir commits en ráfagas; prueba concurrente de 1000 eventos exige persistencia/orden y verifica borrado con pendientes.
- `file.restore_name` era booleano interpretado como milisegundos: pasa a `verified`; ya no contamina la duración.
- SharedPreferences/CAN se leían desde la composición de `CredentialsDialog`: lectura asíncrona en `CanStorage.loadSavedCan`, sin sobrescribir texto ni preferencia que el usuario haya editado mientras carga. Confirmación espera la lectura inicial; cancelar nunca guarda los datos introducidos.
- Medición de coordinación, protocolo y PDF antes indistinguibles: spans explícitos y pares por UUID. Repositorio de bóveda incluye tiempo de espera de mutex/IO y no lo presenta como tiempo criptográfico exclusivo.
- Pruebas PC desde G: fallaron al configurar ACL del directorio PDF. Es una limitación del volumen sincronizado; no se elimina la ACL ni se habilita un fallback inseguro. Usar `-PdesktopBuildDir=D:/DNIeToolchain/Builds/diagnostics72/signing-desktop` para estas pruebas.

## Validación

Primera iteración instalada APK72: 26 pruebas Android pasan en 37,569 s. Incluyen navegación repetida por cinco pestañas y desplazamiento, escritura/cancelación sin NFC, firma manuscrita/PDF, lectura de imágenes ICAO, bóveda cifrada y alteración, recuperación/cambio con claves sintéticas, previews, selección/autorización y retiro de passkeys. El colector lee 216 eventos directamente; UID auxiliar sin privilegios es rechazado por Android. No se borra el historial real para aislar la prueba.

En ese registro hay 47 señales de IO en hilo principal y 20 cuadros lentos: mediana de esos cuadros 113,731 ms, máximo 297,107 ms. Se observa una recuperación de heartbeat de 372 ms. Una operación sintética de repositorio dura hasta 1405,013 ms, pero ocurre en IO; no es bloqueo de UI por sí misma. Las trazas localizan CAN, inicialización/limpieza de previews y marcos del entorno. Es evidencia instrumentada, no benchmark del uso cotidiano ni fallo demostrado del motor NFC.

Cuatro pruebas Python verifican transporte completo/vacío, truncamiento/watermark/orden, preservación del archivo previo y separación de ventana/capas. El analizador admite --after-id para solo eventos posteriores al watermark y --process para aislar un proceso; detecta cursor fuera de historia tras un reinicio de datos.

Batería JVM ampliada: 604 resultados, 598 pasan, seis omitidos y cero fallos. Las omisiones son dos casos Windows de enlaces simbólicos y cuatro casos opt-in de integración Windows/navegador (variantes debug/release); el enlace sí se prueba en Android. Gradle reutiliza tareas sin cambios. Tras ampliar la guarda de serialización, diagnostics-core se revalida con seis pruebas: una adicional exige que un valor cuyo toString falle no afecte al negocio. App: 77 casos, 76 pasan y un enlace omitido en Windows.

Ronda corregida Android: las mismas 26 pruebas pasan en 39,553 s. Captura de 452 eventos completa; en el proceso de esa ronda se cuentan 208 eventos, cero señales de CAN/SharedPreferences desde la composición (antes cinco), 42 señales restantes de IO y 24 cuadros lentos, máximo 339,005 ms; cero eventos crash. Esto NO demuestra mejora de latencia global; la variación bajo instrumental no es una comparación de uso cotidiano.

Se detecta carrera de arranque frío: el proveedor puede publicarse antes de DnieApplication.onCreate. Se corrige con una barrera CompletableDeferred de disponibilidad del almacén; el proveedor espera en IO y no bloquea al inicializador. Revalidación de endpoint y ráfaga: dos pruebas pasan en 0,369 s. Tres capturas consecutivas después de am force-stop pasan (465/471/479 eventos, 2,891/2,140/2,985 s de tiempo host). Historial conservado, aplicación reabierta al terminar.

Manifest Release fusionado y clases compiladas carecen de DiagnosticExportProvider, RuntimeDiagnostics y DiagnosticStore. No se ha generado un paquete de distribución de producción en esta tarea. Architecture boundaries OK.

Artefacto final instalado: D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc24-diagnostics-debug.apk. SHA256: 53E57C84A493F0D71BC923B6BC2E68165058B50A047DC36FC5135B1034745D3C. Evidencias locales fuera de Git: D:/DNIeToolchain/Checkpoints/apk72-* y D:/DNIeToolchain/Diagnostics/apk72-*.

Pendientes físicos: medición de firma/lote y bóveda con DNI, NFC débil/desconexión, Bridge/Suite por los tres transportes y comparación de cuadros en uso cotidiano. No se envían intentos de PIN ni órdenes al DNI durante esta batería. La limpieza de plaintext al arranque/cierre de previews sigue siendo una barrera de seguridad; moverla requiere coordinar apertura/cierre, no ejecutar borrado asíncrono que pueda eliminar una preview nueva.
