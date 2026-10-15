# Sistema de diseño y guía R9 · 3.1.2 / APK86

2026-10-15. Implementación de `ESPECIFICACION_R9_RECIBIDA.md` sobre el checkpoint `checkpoint/pre-design-r9` (0935a29). Fuente de versión en gradle.properties; conservar firma y datos de actualización. Los resultados definitivos se registran en `docs/operations/DISENO_R9_RESULTADOS.json` y en la carpeta de evidencia de G.

## Cambios de producto

Android, Suite Swing y gestor WinForms comparten la paleta Aether clara/oscura. Primario oscuro #6B9EFF con texto #00174B, superficies por niveles y contorno fino; documento PDF blanco independiente del tema. Tipografía sans para categorías en formato oración, números tabulares en formularios/contadores, radios uniformes y presión de tarjetas interactivas.

La hoja de lectura mantiene las fases R8: credenciales, espera, DNI detectado, resultado y error. Radar de dos ondas en espera/lectura, transición a esmeralda cuando se detecta el DNI y entrada de éxito con resorte. Movimiento reducido desactiva radar y escala animada; pestañas principales instantáneas, configuración con desvanecimiento breve. Las transiciones no ejecutan, repiten ni autorizan operaciones.

Guía Android opcional de tres pasos, abierta por el usuario desde la tarjeta de bienvenida o Configuración. No aparece automáticamente como modal. La bienvenida se puede ocultar de forma persistente y no se muestra con CAN recordado o datos cargados. El CAN de seis dígitos se guarda únicamente al pulsar Guardar, mediante ViewModel y adaptador en IO; no se solicita PIN ni se inicia NFC. El formulario se limpia al cerrar y verifica que el CAN se haya persistido antes de anunciar éxito.

Guía rápida Windows mediante botón ? con nombre accesible y tres tarjetas. Botón de tema con etiqueta explícita e icono vectorial, sin depender de glifos del sistema. El gestor conserva foco, controles nativos y selección semántica al alternar temas.

## Límites y ajustes al documento externo

DESIGN.md establece nueve secciones propias del proyecto, enlazadas desde AGENTS.md. La especificación externa afirma que hay skills instaladas; no se encontraron las dos skills citadas y no se atribuye su ejecución. Las fuentes de diseño del documento externo orientan la presentación, no certifican seguridad o cumplimiento.

La ubicación de la antena NFC varía por teléfono. La guía pide esperar la confirmación en pantalla, incluso con vibración desactivada. No promete detección automática en redes que la bloquean ni instalación cero del lector PC/SC. CAN público no significa que deba registrarse o exportarse junto a diagnósticos; no se añaden logs de credenciales.

AndroidMotionPreference observa ANIMATOR_DURATION_SCALE en modo lectura, comparte una suscripción ligada a pantallas activas y no cambia ajustes del sistema. AetherMotion centraliza resortes/transiciones. AetherCard centraliza geometría/contorno/presión. NfcOperationVisual aplica ondas por dibujo; las lecturas de frame no recomponen el contenido textual. OnboardingViewModel guarda CAN; la hoja solo presenta estado y emite acciones. SuiteQuickGuideDialog no llama a conexión, servicio ni tarjeta.

Motores de PACE/SM, firma CMS/PAdES/TSA, recuperación/bóveda y transporte sin cambios. No se reinstala el servicio SYSTEM ni el controlador UMDF en esta entrega. Versionar las interfaces actualizadas no atribuye una nueva versión al servicio o driver instalado.

## Verificación y entrega

Pruebas JVM y Android unitarias, fixtures instrumentados de guía/movimiento reducido y regresión R8, pruebas de la guía Swing y controles nativos. Las pruebas de CAN usan un almacén inyectado y valor sintético; no sobrescriben el CAN del usuario. No se cambia la escala de animación del teléfono. Las pruebas de presentación no acreditan lectura NFC física, vibración percibida, firma TSA real, USB o suspensión.

Rutas de ejecución: `D:/DNIeToolchain/Builds/design-r9`. Evidencias de entrega: `G:/Mi unidad/BOT TRADER/DNI_CODEX/revision-design-r9-20261015`. Historial/checkpoints y código en Git privado; APK, logs, capturas y paquetes Windows fuera de Git. Retención de AndroidPackages: versiones85 y86; versión84 archivada en D, sin eliminar el checkpoint.

Fuentes de API: [Compose Animation](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/package-summary), [Android Settings.Global](https://developer.android.com/reference/android/provider/Settings.Global).

## Resultado comprobado

- 253 casos JVM/Android unitarios en65 suites:252 aprobados,0 fallos/errores,1 omisión preexistente por privilegios Windows.
- Manager:48 comprobaciones aprobadas,0 llamadas al servicio,0 operaciones de tarjeta. Suite: guía/tres tarjetas/temas/cierre aprobados dentro de la tanda JVM.
- Android:8 instrumentadas aprobadas en15,396s; primera tanda tuvo dos carreras de sincronización de fixtures, repetición individual2/2 y tanda final8/8. Capturas de los tres pasos extraídas directamente por ADB desde caché privada del fixture.
- APK debug/release y auxiliar compilados offline. Instalación final app/auxiliar Success; paquete instalado3.1.2/código86. Mismo certificado de firma que85. Producción Android sin implementación/manifiesto de diagnóstico. El verificador Windows heredado se ejecutó sobre un paquete Release anterior; el paquete nativo nuevo se comprueba mediante sus pruebas de exclusión de DebugForm/rechazo del comando de layout y distribución sin PDB.
- Accesos Suite/Bridge actualizados y respaldados; procesos de usuario activos conservados. Servicio SYSTEM/controlador no reinstalados. AndroidPackages conserva85/86,84 archivada.
