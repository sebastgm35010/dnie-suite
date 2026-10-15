# Estado guardado y retomar tras reiniciar

2026-10-14 · Android 3.1.1 / APK85 instalada por ADB, sin desinstalar ni borrar datos. Usuario confirmó APK84 y reactivó ADB. Versionado central en gradle.properties y comando para incrementar patch/código por entrega; Configuración muestra versión. Firma app/auxiliar idéntica y compatible con84; ambos instalados. 89 unitarias pasan/1 omitida y4 instrumentadas R8 pasan (9,22s), sin DNI. Auxiliar queda instalado con identidad estable. NFC/TSA/háptica físicos siguen pendientes; Windows instalado conserva R8. [Detalle](VERSIONADO_Y_FIRMA_3_1_1.md). Los avisos de ADB suspendido siguientes son históricos y están revocados por el usuario.

2026-10-14 · Pantallas y NFC R8 / APK84 (3.1.0) compilada debug/release, sin instalar ni usar ADB; APK82 sigue siendo la última instalada. Hoja continua credenciales→NFC→progreso→resultado; CAN compacto y PIN transitorio. Firma con preview superior, tres pestañas y barra fija, resultados parciales explícitos. Cinco destinos comunes; Mi DNIe agrupa visualmente Certificados/Identidad, credencial ICAO y detalles plegables. Suite Windows con plantillas 2×2, indicador de actividad y apertura del PDF real. 251 JVM pasan/1 omitida y Manager44 pasan; auxiliar Android compilado, sin ejecutar. Suite preparada y acceso actualizado; Bridge/servicio/driver y motores criptográficos sin cambios. Pruebas Android/NFC pendientes. [R8](../design/PANTALLAS_FLUJOS_NFC_R8.md).

2026-10-14 · Personalización R7 / APK83 (3.1.0) compilada debug/release; no instalada: ADB suspendido hasta nuevo aviso del usuario. APK82 sigue siendo la última instalada. Sello con fondo transparente, insignias check/candado, fecha/densidad/colores, plantillas y esquinas al6%; diseño Android persistente, navegación y acciones Mi DNIe. Respuesta táctil por eventos NFC con ajuste de usuario. Windows añade PNG de rúbrica, DnD visual y tema claro/oscuro. 241 JVM pasan/1 omitida por permisos Windows y 44 comprobaciones Manager pasan. Interfaces Windows y accesos actualizados; servicio, driver, identidad y motores criptográficos conservados. Pruebas Android y firma física R7 pendientes. [R7](../design/PERSONALIZACION_SELLO_HAPTICA_R7.md).

2026-10-14 · Red R6 / APK82 (3.1.0) instalada: Bridge Red (Wi-Fi/Tailscale) + USB; descubrimiento autenticado y TLS fijado por huella/token, IP opcional en Suite/gestor y CLI. Vista previa Android muestra anotaciones existentes. QR de sello/cotejador retirados de UI; QR de emparejamiento conservado. 283 pruebas JVM pasan, 3 optativas/privilegios omitidas; 261 Bridge + 48 Control + 41 Manager + 3 servicio instalado pasan; 8 instrumentadas Android pasan. Descubrimiento Windows→Android verificado por LAN y Tailscale por separado, credenciales sintéticas, sin DNI. Gestor/servicio/Suite actualizados, identidad conservada y driver sin reinstalar. Firma NFC y cambio físico de red pendientes. [Rutas, pruebas y límites](../architecture/RED_COORDINADA_R6.md).

2026-10-14 · Estado más reciente QR R5: APK81 / 3.1.0 instalada. Suite actual: D:/DNIeToolchain/Builds/stamp-qr-r5/signing-ui/install/dnie-signing-ui; CLI: sibling signing-desktop/install/dnie-signing-desktop. Acceso directo actualizado. Bridge/servicio siguen R4, driver 2.0.0.4 intacto. Ver docs/design/QR_COTEJO_R5.md y SQL feature stamp_qr. No usar PIDs/sesiones antiguos como estado actual.

2026-10-14 · Aether R4 / 3.1.0: Bridge corrige pintura y selección, inputs, bandeja y consulta WinSCard. Suite añade hoja de muestra, controles, preferencias visuales, zoom y apertura/autocompletado de resultados; emblema compartido actualizado. 196 pruebas pasan/1 omitida preexistente, 60 comprobaciones nativas y 2 instrumentadas Android pasan. Windows actualizado con identidad y 13 archivos runtime conservados; APK80 instalada. QR enriquecido diferido; firma física nueva y suspensión siguen pendientes. [Detalle](../design/RESULTADO_AETHER_R4.md).

2026-10-14 · Aether Android79 / 3.1.0 instalada por ADB con reemplazo sin borrar datos. Dos pruebas instrumentadas pasan (4,01 s): cinco estilos/rúbrica transparente y selector de páginas/preview Compose. Arranque normal comprobado, sin fatal en el PID observado; auxiliar retirado. Firma con DNI/TSA y regresión física Suite/puente pendientes. [Detalle](../design/VALIDACION_ANDROID_AETHER_R3.md).

2026-10-14 · Rutas vigentes: Suite `D:/DNIeToolchain/Builds/aether-redesign/signing-ui/install/dnie-signing-ui`; Manager `D:/DNIeBridgeApp/versions/29f121205ffe412a8e165bf404d49667/DNIeBridge.exe`. Accesos actualizados, servicio conservado. APK79 compilada sin instalar; Android78 es la última comprobada. No usar las rutas históricas siguientes como estado actual. [Entrega Aether](../design/RESULTADO_AETHER_R2.md).

2026-10-13 · Controlador R2 2.0.0.4/oem239 y backend Windows instalados sin reinicio. Errores locales de transmisión ya no retiran la tarjeta; protocolo tras apagado y cancelación antes de entrega corregidos. Pulsos 2 s/operaciones 25 s frente a plazo del driver 30 s. 7860 comprobaciones C++, 137 backend, 31 PCSC nativas y 13 TLSrelay pasan. PnP0 y ReaderPublished=true al finalizar, sin tarjeta. Firma DNI y suspensión físicas pendientes; Android78 no cambia. [Detalle](../architecture/CONTROLADOR_COMPLETADOS_Y_PROTOCOLO_R2.md).

2026-10-13 · Versión 3.1.0 / APK78 instalada: integración v4 de autenticación resistente, plazos por candidato, integridad SHA-256 de originales/resultados, migración de identidad Windows protegida y DPAPI acotado. Servicio/gestor Windows y Suite actualizados; identidad conservada según metadatos. 177 JVM, 134 comprobaciones Windows y 3 pruebas Android pasan; debug/release compilados y producción comprobada. Firma y USB/NFC físicos nuevos pendientes. [Detalle](../architecture/SEGURIDAD_BACKEND_3_1_V4.md).

2026-10-09 · Conexiones R5: APK77 / 3.0.0-alpha05-mobility instalada sin borrar datos. Suite y Bridge Wi-Fi ya no fijan identidad a la IP; TLS y token siguen obligatorios. Windows conserva la identidad DPAPI al cambiar IP. TLS directo permite editar destino; Suite tiene pantalla completa independiente y limpia conexión al finalizar lote único. 159 JVM pasan / 3 omitidas, release compilado y 3 pruebas instrumentadas de Suite/Bridge pasan en el móvil. Cambio físico de IP con DNI pendiente. [Detalle](../architecture/CONEXIONES_DIRECCION_E_IDENTIDAD_R5.md).

2026-10-09 · Controlador R1: driver2.0.0.3 oem237 instalado, PnP0 y PCSC publicado. Retiro EvtIoStop sin replay, cancelación invalida backend, purge síncrono y host UMDF aislado. Servicio/gestor distinguen ReaderPublished del CardPresent privado. 6797 comprobaciones C++ pasan, 25 PCSC nativas y 13 TLSrelay pasan; Control45 Debug/45 Release. Firma física/suspensión reales pendientes; APK76 intacta. [Detalle](../architecture/CONTROLADOR_ENERGIA_Y_PUBLICACION_R1.md).

2026-10-09 · Tras reinicio manual vuelve lector propio a WinSCard y PnP código0, sin tarjeta presente. Revisión de driver detecta falta de EvtIoStop para solicitudes pendientes, coordinación de purge/arranque y host UMDF compartido; estado backend no acredita publicación. Sin cambio de controlador; corrección y reproducción de suspensión pendientes. [Detalle](LECTOR_AUSENTE_PCSC_2026_10_07.md).

2026-10-09 · Conexiones R4: APK76 / 3.0.0-alpha04-links instalada. Puente autentica PC antes de esperar DNI; Suite tiene pantalla completa con presencia separada y diagnóstico de conexión. Red agrupa Wi-Fi/Tailscale, USB separado. Windows host actualizado y GUI Suite selecciona IP vigente; emparejamiento anterior puede requerir TXT/QR actualizado de la misma PC. 154 JVM pasan, 3 omitidas; 93 comprobaciones Windows y 34 Android pasan. Prueba física bilateral y detección de cierre remoto antes de NFC pendientes. [Detalle](../architecture/CONEXIONES_INDEPENDIENTES_R4.md).

2026-10-09 · Rediseño Android R3: APK75 / 3.0.0-alpha03-sessions instalada. Disposición compacta basada en Aether, estados con comprobaciones y detalles técnicos desplegables. Puente Tailscale/Wi-Fi/USB con pantalla completa PC–teléfono–DNI y único botón Detener sesión. Vibración común; pulso de 90 ms ejecutado por el sistema verificado. 79 JVM pasan, uno omitido; 33 Android pasan. Sesión física y percepción al leer DNI pendientes. [Detalle](../architecture/REDISENO_ANDROID_3_0_R3.md).

2026-10-08 · Rediseño Android R2: APK74 / 3.0.0-alpha02-interface instalada. Apariencia Claro/Oscuro/Del sistema en Configuración; bóveda integrada en navegación principal y paleta común; interiores, vistas previas, Bridge/Suite y diagnóstico usan presentación compartida. Retorno PC y restauración de Inicio corregidos. 77 JVM pasan, uno omitido, 32 Android pasan. Flujos NFC físicos tras rediseño pendientes. [Detalle](../architecture/REDISENO_ANDROID_3_0_R2.md).

2026-10-08 · Rediseño Android R1: APK73 / 3.0.0-alpha01-interface instalada. Inicio, navegación de cinco secciones, acceso diferenciado Bridge/Suite y tema Aether adaptados a funciones reales. Cierre inicial de navegación corregido (lista lazy); 77 pruebas JVM pasan, una omitida y 28 Android pasan. Interiores y medición de rendimiento pendientes. [Detalle](../architecture/REDISENO_ANDROID_3_0_R1.md).

Comprobado antes del reinicio: 2026-10-07 02:42 UTC (2026-10-06, America/Bogota). Este documento no inicia sesiones, no modifica controladores y no ejecuta el reinicio.

## Referencia guardada

| Elemento | Valor |
| --- | --- |
| Repositorio autorizado | `G:/Mi unidad/BOT TRADER/DNI_CODEX/modest-bose-evolution` |
| Rama | `refactor/modularizacion` |
| Código Windows instalado | commit `77afc1f622454221b2ed84b2e95c9cd543708aad` |
| Checkpoint funcional | `checkpoint/windows-native-2.0.0-rc4` |
| Android | `2.0.0-rc3` / 51, debug `pe.gob.reniec.dnie.evolution.debug` |
| Servicio | `DNIeBridgeAgent`, SYSTEM, Automatic, Running |
| Directorio instalado | `D:/DNIeBridgeApp/versions/8cdc92ea00f8433f8fa724a8a5e4d93d` |
| Gestor | `DNIeBridge.exe` Release; una ventana; acceso **DNIe Bridge** en escritorio |
| Host consultado por pipe autenticado | Stopped; phase idle; CardPresent false; sin error; último perfil LAN |
| Red del puente | Sin conexiones/listener en 39443 al comprobar |
| Lector propio | `DNIe Bridge Android NFC Private Reader 0`, PnP OK |
| Listener anterior | `0.0.0.0:35963`, WUDFHost; dueño exacto pendiente de identificar |

Los PIDs observados fueron 55684 (servicio), 58808 (GUI) y 1880 (listener anterior). Son solo evidencia de esta comprobación: **no reutilizarlos después del reinicio**. El perfil anterior no constituye una conexión activa ni demuestra éxito de LAN.

## Datos que sobreviven

Binarios y estado están en D:, independientes de la carpeta Google Drive G:. `D:/DNIeBridgeApp/installed.json` indica la versión instalada y `install-result.json` la ruta del gestor. Las preferencias públicas están bajo `%LOCALAPPDATA%/DNIeBridge/Manager`; inscripción/identidad en `D:/DNIeBridgeApp/state`, reservadas a SYSTEM/administradores.

Certificado PFX y metadatos de identidad se compararon con la identidad anterior: ambos idénticos; ACL del estado protegida y sin concesión al propietario normal. No imprimir ni copiar esos contenidos a Git. El QR/token y la huella de PC son persistentes; no generar otra identidad para recuperarse del reinicio. Android conserva los perfiles cifrados por transporte. El CAN/PIN no se guardan en esta documentación.

Una sesión NFC/PACE y sus SSC **no sobreviven al reinicio**. Reabrir y autorizar una sesión nueva; no intentar continuar la última APDU ni repetir una operación privada automáticamente.

## Evidencia previa

- Host Windows: 123 comprobaciones sintéticas.
- Control: 31 comprobaciones en Debug y 31 en Release.
- Servicio Release instalado: 10 comprobaciones desde cuenta normal, sin tarjeta; mismo PID durante inicio/parada/reinicio del host; denegación de comando debug directo.
- Ventana única y Release sin DebugForm, BAT/PowerShell/PDB en su paquete.
- APK Debug/Release rc3 recompilados; validación Android rc3 previa: 251 pruebas, dos omisiones opcionales.
- Firma real histórica: PDF Firma Perú Android21, CMS/P7S ReFirma y reconocimiento Ciudadano Digital Android22. Falta repetir con el gestor nativo actual.

Paquetes, hashes y resultados: `D:/DNIeToolchain/Checkpoints/windows-native-rc4/manifest.json`. APK preservados: `D:/DNIeToolchain/Checkpoints/2.0.0-rc3`. Distribuciones nativas: `D:/DNIeToolchain/DesktopPackages/Native-Release-rc4-final` y `Native-Debug-rc4-final`. Release ZIP ~331 KB; no se instaló otro SDK/WDK para el gestor.

## Después de reiniciar

1. Esperar a que estén disponibles D:, Windows y el método de conexión elegido. Leer `docs/CONTEXTO_ACTUAL.md` antes de ejecutar herramientas antiguas.
2. Verificar `Get-Service DNIeBridgeAgent`: debe estar Running/Automatic. Abrir **DNIe Bridge** del escritorio; el servicio al arrancar abre únicamente su broker, sin publicar tarjeta/red. No debe pedir UAC para iniciar/parar el host. Si falta el servicio, registrar estado/error antes de reparar; no reinstalar controladores automáticamente.
3. Verificar el lector propio y el listener legado 35963. Identificar el dispositivo de este último antes de retirarlo; no detener genéricamente WUDFHost ni servicios de tarjetas. No iniciar hosts BAT/Tailscale históricos en paralelo al nuevo servicio.
4. Repetir primero Tailscale: seleccionar PC/teléfono de la tailnet, **Iniciar puente**, autorizar Android y presentar DNI. En Android usar el modo recomendado de traducción/PACE, conservando los modos de comparación solo para una prueba aislada. El flujo Tailscale no necesita importar JSON ni generar una sesión nueva por cada prueba.
5. Abrir un solo consumidor, comprobar certificados y firmar un documento de prueba con ReFirma/Firma Perú. Verificar integridad y firmante con herramienta independiente; conservar resultado fuera de Git. Detener, reconectar y repetir lectura sin reenviar la firma anterior.
6. Probar USB: cable de datos, **Actualizar dispositivos**, seleccionar teléfono, modo USB directo, QR de primera vinculación y permiso de accesorio Android. El reinicio pendiente puede resolver la enumeración de UsbDk; si no aparece el teléfono, registrar etapa sin usar ADB/RNDIS como sustituto. Validar lectura/firma antes de darlo por cerrado.
7. Probar LAN: IP LAN de PC e IP Wi-Fi actual de teléfono, QR y comparación de huella. Medir avance TCP→DNIREADY1→TLS→token→PC/SC→NFC. No sustituir la IP Wi-Fi por el nombre Tailscale en ese perfil.
8. Para Ciudadano Digital, activar el selector RENIEC opcional antes del flujo. La validación facial/ID Perú y programa posterior de cambio de PIN quedan fuera de esta prueba. No hacer intentos automáticos de cambio de PIN o instalación de certificados.

Estos pasos son una secuencia pendiente, no acciones ya realizadas. La primera prueba puede empezar por comprobaciones sin tarjeta. Para diagnóstico detallado usar una compilación Debug explícita y captura APDU móvil opt-in; Release no puede habilitar debug por configuración.

## Precauciones para continuar el desarrollo

Mantener base y Antigravity intactas. Minidriver DNI aislado por los ensayos previos; respaldo en `D:/DNIeToolchain/MinidriverBackups`. No restaurarlo ni modificar Hello, SCardSvr, CertPropSvc, firewall o perfiles de red como efecto lateral de una prueba. Mantener dependencias/builds grandes en D: y reutilizar toolchains existentes.

.NET 8/Windows Desktop y caché NuGet `D:/DNIeToolchain/NuGetPackages` disponibles. El gestor se construye mediante `tools/windows-manager/package-manager.py`. Para APK usar caché Gradle existente `%USERPROFILE%/.gradle`, Java 17 configurado en `gradle.properties`, modo offline; en PowerShell citar `'-Pkotlin.incremental=false'` como un único argumento. No crear otra caché Gradle vacía esperando que tenga dependencias.

Esta entrega solo guarda documentación. No valida todavía arranque tras reinicio, USB/LAN físicos, regresión de firma del nuevo gestor ni firma de producción del controlador/editor. Seguir [pendientes priorizados](../PENDIENTES_ACTUALES.md).
