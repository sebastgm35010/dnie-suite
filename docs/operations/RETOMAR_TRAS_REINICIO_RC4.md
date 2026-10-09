# Estado guardado y retomar tras reiniciar

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
