# Rendimiento UI — diagnóstico y cambios 1.4.4-ui-perf

## Alcance

Usuario confirma que firma/descifrado/nombres funcionan, pero percibe tirones en navegación, escritura y desplazamiento. Revisión delegada al subagente rendimiento_ui; mediciones, integración y verificación por el agente principal. Se modificó exclusivamente modest-bose-evolution. Se preservaron el motor de firma, NFC/PACE, SSC/MAC, claves y formato de bóveda.

## Costes concretos encontrados

- Bóveda creaba un ColorScheme nuevo en cada composición: al escribir CAN/PIN propagaba un tema nuevo a toda su subjerarquía. Ahora se reutiliza el esquema y el resultado recibe parámetros específicos, sin las credenciales.
- DnieTheme recalculaba colores dinámicos y reenviaba atributos de ventana/barra de estado en cada recomposición. Se memorizan por configuración/tema y se actualiza la ventana solo cuando cambian.
- MainActivity ejecutaba clearFlags(FLAG_SECURE) al cambiar entre rutas normales aunque ya estuviera limpio. Se compara el estado real y solo se cambia al entrar/salir de Bóveda. Se mantiene la protección de capturas.
- Navigation aplicaba crossfade por defecto: dibujaba y componía simultáneamente pantalla entrante y saliente. Las pestañas ahora cambian inmediatamente, manteniendo restoreState/saveState y back stack.
- Había dos IdentitySignatureBridge; cada uno preparaba DG7 y comprimía PNG en Main tras la decodificación. Se prepara una sola imagen para PDF y lotes, con decodificación/compresión en Default y reciclado del bitmap temporal. La interpretación del certificado público también se hace fuera de Main.
- Consola recreaba listas filtradas varias veces durante la misma composición; se memoriza filtrado/contadores. El seguimiento salta al último registro en vez de animar el recorrido de todo el histórico, incluso después del límite 1000. ToneGenerator se construye en IO.
- La onda del diálogo NFC leía escala/opacidad durante composición cada frame. La lectura se mueve a graphicsLayer: conserva la onda y actualiza el dibujo sin recomponer todo el diálogo.
- Las pantallas recogen StateFlow con collectAsStateWithLifecycle. La exportación de imagen ICAO escribe al proveedor en IO.

No se encontró cifrado/descifrado de archivos ejecutado por cada pulsación en los campos de Bóveda. Los cambios no constituyen un rediseño visual; el cambio visible principal es que las pestañas ya no hacen fundido.

## Diagnóstico por ADB

Registros fuera de Git: ../diagnosticos-locales/2026-10-05-rendimiento. La captura DNIE_PERF de la sesión anterior sigue activa.

Los intentos de trazas ART de esta sesión devolvieron archivos vacíos y en un intento el proceso terminó antes de completar la medición. No se utilizaron esas trazas para atribuir tiempos a métodos ni se declaró un nuevo crash Java sin excepción registrada. La traza válida de arranque de la sesión anterior sigue disponible en la carpeta de diagnóstico de bóveda.

Se midió una secuencia fija sin profiler: 3 ciclos de Identidad → Certificados → Firmar PDF → Lotes → Consola APDU, intervalo 600 ms después de cada tap ADB. Se verificó app en primer plano al inicio y existencia del proceso durante la secuencia. Se reinician contadores gfxinfo antes de cada muestra. Los scripts y métricas están en la carpeta local indicada.

Una secuencia aislada de pestañas no reproduce por sí sola escritura sostenida, documentos abiertos, imágenes ICAO o una consola poblada. Estos casos se validan con uso del usuario; no se extrapola un resultado de navegación a toda la app.

UiFrameDiagnostics se incorpora solo en debug. Usa Window.OnFrameMetricsAvailableListener en HandlerThread propio y registra únicamente cuadros de al menos 48 ms, con ruta, tiempo total, medición/layout, dibujo, sincronización y callbacks descartados. No incluye texto, documentos, CAN/PIN ni claves. 48 ms es un umbral diagnóstico de cuadros largos, no el presupuesto de refresco de todos los dispositivos. Se retira el listener y se cierra el hilo al destruir la actividad. La ruta de cada evento es la última ruta notificada, una aproximación durante cambios rápidos.

Fuentes API: https://developer.android.com/reference/android/view/Window.OnFrameMetricsAvailableListener ; implementación Window.setFlags: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/view/Window.java .

## Validación

APK 1.4.4-ui-perf, código 20, mismo paquete y firma de desarrollo para actualizar conservando datos. Pruebas JVM de app y compilación de pruebas instrumentadas ejecutadas. No se volvió a intentar instalar el APK auxiliar de pruebas rechazado previamente por el dispositivo. Se verifica navegación directa por ADB y firma v2 del APK.

## Resultado comparativo y validación del usuario

| Métrica (secuencia fija sin profiler) | 1.4.3 | 1.4.4 |
|---|---:|---:|
| Cuadros renderizados | 2808 | 2816 |
| Cuadros janky (criterio actual gfxinfo) | 1 | 1 |
| Percentil 95 | 9 ms | 8 ms |
| Percentil 99 | 57 ms | 48 ms |
| Eventos Slow UI thread | 0 | 0 |

Una sola muestra por versión; no constituye evidencia estadística ni verifica por sí sola la escritura. La muestra anterior con proceso terminado/No process found se descartó y no integra la tabla. Los bloques duplicados de gfxinfo se reportan una sola vez, no se suman.

La captura posterior con UiFrameDiagnostics sigue mostrando cuadros largos en primeras entradas: Bóveda 218 ms (layout 157 ms) y 264 ms (layout 21 ms), y algunos cuadros largos al volver a Certificados. Esto permite seguir separando render/layout de trabajo del motor; el total no es igual a la suma de los campos registrados. No se afirma que todos los tirones hayan desaparecido.

Después de probar escritura y desplazamiento en la versión instalada, el usuario respondió «Más fluida». Esta validación práctica respalda la mejora percibida, además del cambio pequeño observado en la secuencia fija.

43 pruebas JVM de app pasaron; pruebas instrumentadas compilaron. APK instalado conservando datos y firma v2 verificada. SHA-256: CEFFB83CA740E23332C40DBA131B4E5B8822F5B848B1499C0C978E1CC2CD270F. Versión 1.4.4-ui-perf (20). Captura DNIE_PERF continúa activa; no se deja ART profiler ejecutándose.
