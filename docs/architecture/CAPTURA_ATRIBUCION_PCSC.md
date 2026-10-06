# Captura temporal del cliente PC/SC

2026-10-06. El control sin IDplug reprodujo PACE y el SELECT protegido fallido. Se amplía el monitoreo para identificar procesos y bibliotecas que participan, sin asumir que IDplug UI conserva CAN ni que Windows lo almacena.

`tools/host-diagnostics/Capture-PcscAttribution.ps1` usa sesiones ETW temporales, ventana máxima de 300 segundos y archivos circulares de 32+64 MB en D:. No instala software, cambia servicios, habilita canales persistentes ni altera firewall. Guarda inventario inicial de procesos (sin argumentos) y servicios, WMI de arranque/salida de procesos y sondeo de DLL relevantes; Kernel-Process captura eventos de procesos y carga de imágenes, incluyendo procesos que el sondeo puede perder.

Proveedores manifestados: Kernel-Process, Smartcard-Server, SmartCard-DeviceEnum, SmartCard-Audit, DriverFrameworks-UserMode y CAPI2. Proveedores WPP: winscard, scardsvr, certprop, basecsp/scksp, msclmd y scfilter, GUIDs de la [documentación Microsoft](https://learn.microsoft.com/en-us/windows/security/identity-protection/smart-cards/smart-card-debugging-information). Archivos circulares mediante [logman](https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/logman-create-trace).

Los ETL son evidencia privada y pueden contener rutas, datos de certificados o intercambios del proveedor. No subirlos a Git ni volcar el payload completo en chat. Extraer metadatos PID/TID, proveedor, evento, DLL y tiempos para la atribución. WPP puede necesitar símbolos/TMF: que una sesión arranque no garantiza que el proveedor emita eventos ni que se pueda decodificar toda la llamada. El PID de SCardSvr no identifica por sí solo al cliente que pidió transmitir; buscar el contexto cliente o eventos WinSCard en su proceso.

Primer ensayo preparado: captura privada D:\DNIeToolchain\LiveCaptures\be2a11014ad446ef848eab085bdb3d23; emparejamiento 787f26affc3d481ba5f90d815e895022. Ambos arranques ETW devolvieron código cero y existe attribution-ready.txt. Antes de conectar no se observaron procesos IDplug. Cruce pendiente con APDU del teléfono y fases del host tras el intento real.

## Resultado del intento real

Se detuvieron ambas sesiones ETW con código cero tras el aviso del usuario. Archivos finales: system.etl 33.554.432 bytes, pcsc-wpp.etl 9.633.792 bytes. El archivo system es circular y alcanzó su límite; la ventana crítica sí está presente, pero no asumir que conservó todo el inicio.

El inventario de servicios identifica svchost PID 2716 como anfitrión de CertPropSvc. El sondeo elevado muestra certprop.dll, WinSCard.dll y scksp.dll cargados en ese proceso. Kernel-Process registra en PID 2716, TID 55856:

- 2026-10-06T13:08:39.4025936Z: carga de PeruMinidriver64.dll.
- 2026-10-06T13:08:40.2236668Z: carga de idplug-peru-minidriver64.dll.

La ventana WPP 13:08:39–13:08:44 UTC contiene 1.354 eventos del PID 2716. Es evidencia de actividad del cliente de propagación de certificados y de los minidrivers de Perú, con la UI IDplug cerrada. Los encabezados WPP se pudieron extraer, pero ProviderId aparece vacío y los mensajes no están decodificados; no afirmar que se atribuyó cada SCardTransmit individual ni que se capturó CAN. Archivos derivados privados: minidriver-image-events.xml, wpp-summary.json y wpp-certprop-window.json.

El teléfono volvió a recibir MSE/GA y respondió 9000 al final. Comando lógico 16, 0CA40204, tiempo Android 1791292123053; CardProtocolException a 1791292123088: 35 ms. Cierre a 1791292123135. El host registró EOF a 2026-10-06T13:08:43.2956224Z. No comparar relojes entre equipos sin calibración.

Conclusión: se identificó un cliente automático relevante, CertPropSvc con el minidriver oficial, aunque no haya interfaz IDplug abierta. No se demuestra almacenamiento persistente del CAN. Próximo paso: centrar las trazas y decodificación WinSCard/CSP en PID 2716/TID observado, o medir un control limitado al lector propio que no exponga presencia PC/SC. La adaptación de PACE/SM sigue pendiente y no se debe sustituir por respuestas falsas ni desactivar integridad.

El script detiene exclusivamente sus sesiones con nombres únicos al terminar el plazo o encontrar stop.txt. Los colectores de capas se detienen mediante el mismo stop.txt. Validación inicial: parser PowerShell sin errores y arranque real de las dos sesiones confirmado. No se modificó Android ni el controlador.

## Continuación: símbolos y control sin presencia PC/SC

Se revisaron los archivos instalados PeruMinidriver64.dll e idplug-peru-minidriver64.dll: ambos versión 1.8.0.459, Authenticode Valid. Las entradas Calais de Perú contacto/contactless apuntan a PeruMinidriver64.dll y a CSP/KSP Microsoft. Esto no acredita compatibilidad NFC, pero no aporta evidencia de corrupción que justifique reinstalar.

Se descargaron del servidor público Microsoft únicamente PDB de WinSCard, certprop y SCardSvr, aproximadamente 1,6 MB, con caché en D:\DNIeToolchain\Symbols. El intento scksp no completó. Tracefmt cargó los tres PDB pero no generó formatos TMF: el payload WPP continúa sin decodificar. No confundir código de salida cero de Tracefmt con mensajes legibles. Ver [requisitos de Tracefmt](https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/tracefmt-commands).

Se preparó un control con `serve-local` y el cliente propio `tools/host-diagnostics/Test-PrivateBridgeIdle.ps1`. No adjunta la tarjeta al lector PC/SC y no altera servicios/controladores. El cliente se conecta al pipe privado del usuario después de TLS; pide únicamente ATR y presencia (control 4 y 5), cada cinco segundos durante unos 90 segundos. No transmite APDU de aplicación ni PACE desde el PC. Android sí prepara su sesión PACE física. Las comprobaciones de presencia no realizan intercambio NFC, por lo que estabilidad aquí valida transporte y ausencia de fallo espontáneo, no lectura continuada de tarjeta.

El cierre al completar 19 controles es deliberado y se registra como test_completed con expectedClose=true. No clasificar ese cierre final como desconexión prematura. `ReadAsync` tiene límite de diez segundos por respuesta y el pipe solo acepta la identidad local propietaria. Validación inicial: parser sin errores, generador/host compilados sin advertencias, listener local iniciado; resultado físico pendiente.

Emparejamiento del control 0c4817ec0be54dcbbedf1215be3d38e3; captura privada D:\DNIeToolchain\LiveCaptures\7af9084f3e3746d5a1828939e1277a60. No reinstalar controladores antes de revisar este control.

Resultado verificado: 19 control_ok (un ATR y 18 comprobaciones de presencia), 91.287 ms entre primer y último control, un test_completed esperado y cero test_failed. El usuario confirmó estabilidad durante el intervalo. Android registra session_started y session_closed sin transport_failed ni comandos de aplicación en la sesión del control. Los colectores se detuvieron tras confirmar el resultado.

Control siguiente preparado con autorización del usuario: Test-PrivateBridgeIdle.ps1 -PublicReads envía 19 ciclos de SELECT IAS, SELECT EF.CardAccess 011C y READ BINARY, separados por cinco segundos. Las 57 APDU son lógicas, CLA 00, y no incluyen MSE/GA del PC. Solo Android gestiona PACE. Se registra stage, SW, longitud y duración; las respuestas se comparan en memoria para verificar estabilidad y se limpian al finalizar. Un SW diferente de 9000, EOF o cambio del archivo detiene el ensayo. El cierre tras el último ciclo es esperado.

Captura privada fbf4b51e22d04a788effb731c9157237, emparejamiento 3079747f26424a78995c02a8876d45de. El colector ADB se inició con --metadata-only para omitir hex; el registro local existente de la app conserva su política habitual y no se borra. Parser PowerShell sin errores y generador/host compilados sin advertencias. Resultado real pendiente.

Resultado posterior verificado: los 19 ciclos y las 57 APDU SELECT/READ completaron con 9000. Cada READ entregó 109 bytes (107 de EF.CardAccess más SW), idénticos entre ciclos según comparación en memoria. Se registró test_completed con expectedClose=true y ningún test_failed; Android cerró después de responder la APDU 57, sin transport_failed. El aviso del usuario de que pareció durar menos corresponde al cierre programado, no a un aborto. Los colectores quedaron detenidos.

El control demuestra lecturas NFC repetidas y canal PACE/SM de Android funcionales en esta secuencia pública sin otro PACE del PC. No prueba certificados, PIN/firma o compatibilidad del minidriver. La secuencia fallida con PC/SC incluye un segundo PACE y un comando ya protegido; esa diferencia sigue siendo el foco de la adaptación. Reinstalar la misma versión firmada no aborda por sí solo esa diferencia de protocolo.

El host genérico imprime «Authentication, transport or framing failed» al recibir EOF por el cierre deliberado del pipe. En este ensayo corresponde al final esperado registrado por el cliente; no demuestra fallo de autenticación ni caída espontánea.

Esta evidencia separa estabilidad del enlace TLS/Android/PACE inicial de la incompatibilidad del tráfico PC/SC. No valida lecturas NFC repetidas, porque las comprobaciones de presencia consultan estado local. Próximo control útil: mismo endpoint privado, comandos lógicos SELECT/READ públicos protegidos exclusivamente por Android y comparación con la secuencia originada por el minidriver. Mantener minidriver/PACE del PC fuera de ese control y detener ante pérdida de integridad.
