# Rediseño Android 3.0 R2 · navegación y apariencia compartidas

2026-10-08. Reemplaza el alcance parcial R1. Checkpoint previo: `checkpoint/pre-android-redesign-r2-apk73`.

## Arquitectura

- `ui/design/FeatureScaffold.kt`: cabecera, categoría, acciones y superficies comunes; `FeatureSection` agrupa una tarea. No accede a almacenamiento ni motores.
- `data/preferences/AppearancePreferences.kt`: selección Claro/Oscuro/Del sistema, persistencia independiente de secretos, lectura y escrituras ordenadas en IO; estado observable compartido por actividades. `ui/settings/SettingsScreen.kt` presenta y solicita cambios. `DnieTheme` aplica la preferencia global sin mostrar el contenido con un tema temporal antes de cargarla.
- Inicio abre Configuración. Bóveda es destino principal de la misma barra, sin esquema oscuro propio. Los pasos Usar DNI/Respaldo/Opciones son tareas internas de esa sección y vuelven a Mis archivos, manteniendo disponibles las demás secciones.
- Identidad, certificados, PDF/lotes, editor PDF, vista privada, selector PC, Bridge/Suite y herramientas de desarrollo comparten cabeceras, superficies y colores semánticos. Los formularios de conexión están agrupados en conexión y autorización. Los detalles de certificado y APDU respetan ambos temas.
- `ui/navigation/ActivityNavigation.kt` permite cambiar directamente de Bridge/Suite a cualquier sección principal. Se conserva el propietario NFC independiente de cada actividad; no se fusionan ViewModels ni sesiones de transporte. La transición dispara onPause, detiene la autorización y devuelve el destino a MainActivity mediante intent validado.
- Al abandonar una función se cancelan sus operaciones pendientes y credenciales. Se conservan documentos, resultados y datos cifrados. La vista previa de bóveda conserva FLAG_SECURE y borrado al perder visibilidad.

## Funciones preservadas

PACE/SSC/MAC, CMS/PAdES/TSA, confianza TSL/OCSP/CRL, cifrado CIF hard y formato DNIEBKP2 no cambian. Selector SAF antes de credenciales, autorización explícita y limpieza de PIN al pausar siguen vigentes. No hay biometría ni passkeys. No se incorporan funciones ficticias del prototipo.

## Validación

- Compilación debug, APK auxiliar y Kotlin de release superadas offline.
- 78 casos JVM app: 77 pasan, uno omitido por permisos de enlaces simbólicos en Windows; cero fallos/errores.
- 32 pruebas Android pasan en teléfono, 44,806 s. Incluyen temas/recreación/retorno desde PC, entrada compartida sin bloqueo de pestañas, separación PDF/lotes, CAN, ICAO, archivo/respaldo/recuperación de bóveda, preview privado y diagnóstico.
- Límites de arquitectura pasan; componentes de diagnóstico ausentes del manifiesto y clases de release.
- APK74 instalada y versión comprobada: `D:/DNIeToolchain/AndroidPackages/dnie-evolution-3.0.0-alpha02-interface-debug.apk`, SHA256 `AECCD199B049A3152582D67FBB17FC2A4240BA333B7886B49785323C6CFC4231`.
- Logs de compilación y ejecución en `D:/DNIeToolchain/Checkpoints/apk74-*`; capturas de interfaz en `D:/DNIeToolchain/Diagnostics/apk74/`, fuera de Git. No hay una nueva firma o recuperación NFC física en esta iteración.

Configuración está en el icono de Inicio. La preferencia inicial es Del sistema; las pruebas restauran esa preferencia tras escoger temporalmente Claro y Oscuro.

## Hallazgo de navegación durante la regresión

La apertura de un destino por intent podía ejecutar `getGraph()` antes de `NavHost.setGraph()` al iniciar una actividad nueva. Se espera una ruta actual antes de consumir el destino y se elimina el extra del intent para no repetirlo al recrear la actividad. El ingreso de archivos compartidos espera `currentBackStackEntryFlow.first()` dentro de un efecto por ingreso; no se vuelve a ejecutar al cambiar pestañas. Pruebas específicas cubren retorno desde PC, recreación e ingreso externo sin encerrar al usuario en la bóveda.

El primer caso instrumental también cambió la acción del intent de lanzamiento, haciendo que ActivityScenario perdiera la referencia al recrearlo. Se conserva el intent base en la prueba. La segunda ejecución sí expuso la carrera real anterior; los registros fallidos se conservan junto a la ejecución corregida, fuera de Git.

La prueba de ingreso externo encontró además que restaurar el destino inicial de un grafo plano podía reponer la bóveda sobre Inicio. Inicio no restaura una pila de hijos; las demás secciones conservan su estado. Se aplica tanto a pestañas como al regreso por intent desde PC.

## Revisión visual y continuidad PC

Capturas claras de Inicio/Configuración/Certificados/Firma/Identidad y oscuras de Configuración/Firma/selector PC revisadas en el teléfono. Smoke de navegación real: Bridge → Identidad y Suite → PDF completados, sin autorizar NFC ni tocar archivos. Se restauró Del sistema. El XML de accesibilidad es auxiliar y puede no conseguir estado idle; las pruebas de Compose son la evidencia de navegación. La captura privada de bóveda sigue bloqueada; no se quitó FLAG_SECURE para obtener imágenes.
