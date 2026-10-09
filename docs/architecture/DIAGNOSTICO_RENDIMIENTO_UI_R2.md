# Diagnóstico de tirones · APK72 · 2026-10-08

Los picos reproducidos al navegar y escribir corresponden a trabajo de interfaz: recomposición, medición de Compose y actualización de ventanas. Las pruebas realizadas no ejecutan firma, cifrado ni NFC, por lo que no permiten atribuirles esos picos. Se mantiene APK72 sin modificar sus motores ni instalar otra versión.

## Captura y aislamiento

Teléfono conectado por ADB inalámbrico; aplicación debug, proceso 20179 durante esta captura. La primera navegación produjo cero cuadros porque el teléfono estaba bloqueado: muestra descartada, no prueba de fluidez. Tras el desbloqueo confirmado por el usuario, se verificó MainActivity en primer plano.

Dos rondas válidas recorren Identidad, Firma PDF, Lotes, Desarrollo y Certificados, con desplazamientos de ida/vuelta y aplicación ya iniciada. Se mide con dumpsys gfxinfo y el exportador persistente. Escritura: cuatro ciclos de edición con valor sintético en el diálogo CAN, cancelado mediante Atrás sin confirmar, guardar ni transmitir credenciales al DNI. La captura de escritura comienza después de abrir teclado/diálogo: no incluye su apertura.

| Captura | Cuadros de gfxinfo | Jank moderno | P50 | P95 | P99 |
|---|---:|---:|---:|---:|---:|
| Navegación caliente, primera ronda válida | 2350 | 3 / 0,13 % | 6 ms | 8 ms | 38 ms |
| Navegación caliente, segunda ronda válida | 2348 | 1 / 0,04 % | 6 ms | 7 ms | 22 ms |
| Edición CAN, teclado ya visible | 528 | 5 / 0,95 % | 11 ms | 38 ms | 61 ms |

El indicador legacy de gfxinfo difiere mucho (2,30 %, 2,21 % y 72,35 % respectivamente). No se intercambia con el moderno ni se presenta ninguno como porcentaje universal de tirones. Cuadros de varias ventanas y métricas del fabricante pueden diferir del listener instalado solo en MainActivity. Los percentiles bajos no eliminan los picos de transición; las muestras tampoco son un benchmark de producción ni de todas las pantallas con datos reales.

## Hallazgos

1. **Entrada a pestañas:** FrameMetrics registra cuadros de 72–115 ms alrededor del cambio de ruta, incluidos 43–68 ms en layout/medición. La traza corta completa confirma Choreographer#doFrame hasta 78,043 ms y AndroidOwner:onMeasure hasta 42,175 ms; recomposición hasta 15,042 ms y espera de render hasta 2,583 ms. La medición/construcción de la pantalla ocupa una parte importante del pico. No se identificó aún el composable individual más costoso.
2. **Escritura:** traza completa de 385110 eventos, sin sobreescritura del buffer. Choreographer#doFrame hasta 49,579 ms, Recomposer:recompose hasta 35,728 ms, medición hasta 7,550 ms, layout hasta 8,881 ms, espera de render hasta 3,323 ms. GPU P50 2 ms/P90 4 ms. El trabajo principal observado está en CPU/interfaz, no en una GPU saturada. Se observaron 579 secciones de compilación JIT baseline en segundo plano; presencia no demuestra que causen el tirón ni equivale a un Baseline Profile distribuido.
3. **Arranque y reaparición:** el historial del proceso normal registra primer dibujo de 331,588 ms, con 302 ms en layout; otra reaparición registra 450,263 ms, con 347 ms en layout y 73 ms en draw. Son eventos de primera presentación, separados de la navegación caliente. En onCreate se leen directorios para limpiar previews y archivos plaintext. Son operaciones reales del hilo principal, pero StrictMode no mide su duración: no se demuestra cuánto aportan al arranque.
4. **Alertas IO:** en el último proceso instrumentado, de 42 alertas hay nueve con pilas de limpieza/inicialización de previews de la app y 33 con pilas HyperOS/MIUI. Las lecturas CAN desde composición desaparecieron después de la corrección R1. El nombre main_thread_io no prueba que toda pila remota provenga de código de nuestra app: StrictMode puede propagar violaciones de llamadas Binder. Mantener la pila y separar origen antes de contar deuda propia.
5. **UNKNOWN_DELAY:** en muchos cuadros históricos es el componente mayoritario. Android lo define como espera para que el hilo UI atienda el cuadro; no identifica por sí solo la causa de esa espera. No se concluye que toda espera sea culpa del instrumental o de un motor. [Referencia oficial FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics).

## Integridad de las trazas

Las trazas largas con categorías de sistema sobreescribieron el buffer (578241/1972617 y 1096916/1328466 eventos retenidos/escritos). Se descartan sus intervalos de secciones anidadas: pérdida de marcadores puede producir duraciones ficticias de segundos. La primera transferencia sin compresión excedió el timeout; se cambió a archivo comprimido en el teléfono y pull. Las estadísticas gfxinfo y los eventos persistentes se extraen aparte.

Para atribución se usa la traza corta de navegación con 49847/49847 eventos y la de escritura con 385110/385110. Las secciones anidadas no se suman como CPU exclusiva. La captura de escritura incluye la ventana del diálogo en la traza del proceso; el listener propio de MainActivity no cubre necesariamente todos sus cuadros.

Evidencias y scripts temporales fuera de Git: D:/DNIeToolchain/Diagnostics/ui72/{warm-gfx.txt,keyboard-gfx.txt,focused-atrace.txt,focused-analysis.json,keyboard-atrace.txt,keyboard-analysis.json,final-events.jsonl}. Scripts de ejecución/análisis en D:/DNIeToolchain/Diagnostics. JSONL y trazas no se incorporan al repositorio. No se captura heap, contenido de documentos, PIN ni operaciones privadas.

## Prioridad de corrección

- Reducir el coste de entrada: localizar bloques de medición/recomposición de SignerScreen y DevelopmentScreen; dividir estado y composición, y evitar construir contenido fuera de pantalla cuando corresponda. NavGraph ya desactiva crossfade: no proponer quitar una animación que ya está desactivada.
- Aislar el estado editable de CredentialsDialog y medir nuevamente teclado/ventanas. La validación es local y CAN carga en IO; no hay evidencia para alterar PACE o el motor de almacenamiento como solución a estos cuadros.
- Medir individualmente limpieza de previews e inicializadores de DnieApplication antes de moverlos. La eliminación de plaintext es una barrera de seguridad: requiere coordinación con apertura/cierre, no borrado asíncrono que pueda eliminar una preview nueva.
- Comparar con una variante optimizada/profileable y posteriormente Baseline Profiles. APK72 es debuggable, sin optimización R8 y con diagnóstico activo; los números actuales describen esa variante. No se desactiva el diagnóstico ni se instala producción durante esta revisión.
- Quedan pendientes escritura/desplazamiento con biblioteca grande, documento PDF real y operaciones físicas con DNI/Bridge/Suite. Esta revisión diagnostica los picos reproducidos, no certifica ausencia de lentitud en todos los flujos.

No hubo cambios de Kotlin, instalación ni nuevos tests funcionales: se ejecutaron capturas de comportamiento sobre la versión instalada. La arquitectura y el registro SQL se actualizan con los hallazgos; la optimización concreta permanece pendiente.
