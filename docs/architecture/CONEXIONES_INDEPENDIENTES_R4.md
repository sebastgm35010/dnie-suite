# Conexiones independientes · Android R4 / APK76

2026-10-09. Checkpoint previo: `checkpoint/pre-connection-separation-apk75`.

## Resultado y capas

APK76 / 3.0.0-alpha04-links instalada por ADB sin borrar datos. La conexión PC–teléfono del puente autentica primero el transporte y espera después el DNI. `PendingCard` coordina la espera cancelable; `BridgeConnection.runAwaitingCard` conserva autenticación, secuencia PACE y protocolo DNIBRDG1. `BridgeAndroidAdapter` entrega el Tag físico y ATR real; el ViewModel coordina estados y cancelación. No se crea tarjeta PCSC antes de obtener ATR real.

Windows `PcscBackend` permite esperar la primera respuesta ATR sin el límite anterior de 30 segundos, con cancelación de sesión. La lectura del resto de la respuesta y operaciones posteriores conserva sus plazos. Servicio actualizado por el instalador autorizado, ejecutable en D:/DNIeBridgeApp/versions/2330c61d3c7043a884c86f60581239f7/DNIeBridge.exe; DNIeBridgeAgent comprobado Running. Identidad, controlador y configuración de seguridad conservados.

`ConnectionSessionScreen` presenta estado común; Bridge y Suite lo proyectan mediante pantallas independientes. Primer indicador: PC autenticada. Segundo: DNI listo en Bridge, DNI detectado en Suite; detectar NFC no acredita PACE. Única acción Detener sesión, también al volver. Producción mantiene FLAG_SECURE. Suite registra fases listener_ready, pc_address_verified, tls_ready y peer_authorized, además de fallos; no registra PIN/CAN ni solicitudes completas.

Configuración agrupa Wi-Fi y Tailscale en Red y USB en Cable. QR determina el transporte; Wi-Fi sigue requiriendo TLS anclado, Tailscale conserva su validación de dirección y seguridad propia. No hay fallback de LAN a texto claro.

## Suite esperando a la PC

Usuario confirmó espera de PC. Dirección histórica configurada 192.168.1.5; durante inspección PC tenía Ethernet 192.168.1.8, Wi-Fi 192.168.1.241 y Tailscale 100.104.106.125. No se observó TCP Suite activo. La discrepancia es compatible con el síntoma; no demuestra por sí sola la causa de todos los intentos anteriores.

`SuiteNetworkAddress` conserva direcciones asignadas y selecciona una dirección vigente según ruta al teléfono cuando la anterior desaparece. La GUI elimina valores IP fijos, permite abrir QR también para PC recordada y avisa de actualizar emparejamiento al cambiar dirección. Android sigue verificando dirección e identidad esperadas. Importar el TXT/QR actualizado de la misma PC conserva identidad; no regenerar DPAPI. Nueva GUI abierta desde D:/DNIeToolchain/Builds/links76/signing-ui/install/dnie-signing-ui.

## Evidencia

- Gradle offline: APK debug, auxiliar instrumental, Kotlin release, módulos afectados y distribución GUI: BUILD SUCCESSFUL. D:/DNIeToolchain/Checkpoints/apk76-final-build.txt.
- JVM debug app: 79 pasan / 1 omitida; bridge Android: 27 pasan / 2 omitidas; signing-link: 23 pasan; signing-desktop: 25 pasan. Cero fallos. Omisiones preexistentes, no certifican casos físicos.
- Windows backend: 93 comprobaciones pasan, incluida cancelación mientras espera ATR sin adjuntar tarjeta. D:/DNIeToolchain/Checkpoints/links76-windows-tests.txt.
- Android físico: OK (34 tests), 61.018 s. Navegación y estados por fixtures aislados, no firma real. D:/DNIeToolchain/Checkpoints/apk76-instrumented.txt.
- Prueba TLS real con certificado sintético: conexión autenticada antes de NFC, fábrica física sin invocar y cierre cancelable. No sustituye DNI real.
- Captura visual del fixture Bridge revisada: D:/DNIeToolchain/Diagnostics/apk76/bridge-session-fixture.png. No es evidencia de sesión física activa.
- APK: D:/DNIeToolchain/AndroidPackages/dnie-evolution-3.0.0-alpha04-links-debug.apk. SHA256 D68BF1B1E38BD5B30B86F17D79B8E2D04E9DF9AD628F8E3EF313F489F9CF6257. Paquete pe.gob.reniec.dnie.evolution.debug, versión instalada verificada 76.

## Próxima prueba y límites

Abrir Conectar Android en nueva GUI Windows. Si cambió la IP, importar su emparejamiento TXT/QR actualizado y recordar la PC; abrir Suite continua / Conectar PC guardada. Debe mostrar PC conectada antes de acercar DNI. Después comprobar presencia, retirada y lote real; no repetir automáticamente firmas incompletas. Para Bridge iniciar el gestor/servicio y autorizar: comprobar primer indicador sin tarjeta, luego segundo con DNI. Pendientes físicos LAN/Tailscale/USB.

Durante espera NFC del puente, cierre local/Detener cancela inmediatamente. Un cierre remoto mientras el proveedor espera Tag puede detectarse recién al acercar el DNI o detener; falta vigilancia de EOF antes del ATR. El primer indicador acredita autenticación completada, no una comprobación continua de conectividad. No confundir esta limitación con pérdida de PACE ni relajar autenticación.
