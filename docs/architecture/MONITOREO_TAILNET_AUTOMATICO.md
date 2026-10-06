# Monitoreo automático de la siguiente sesión

`tools/host-diagnostics/watch-tailnet-session.py` sigue únicamente los eventos nuevos del host. Al observar `tailnet_peer_verified` comienza la captura; al recibir `tailnet_ready_for_next_session` escribe `stop.txt`, detiene los recolectores y guarda `result.json`. No mezcla los registros históricos ni exige que el usuario coordine un cronómetro con la PC.

Guarda eventos del puente con timestamps originales, headers/estados APDU sin cargas, metadata de procesos/módulos/servicios/lectores y actividad de Edge si ya existe una sesión CDP disponible en el puerto 19371. No abre el navegador ni inicia un flujo de RENIEC por sí mismo; no inyecta comandos APDU. Incluye los procesos de los servicios SCardSvr/CertPropSvc en el muestreo de metadata, además de middleware y backend.

Sin ADB fuera de casa, el registro APDU móvil se exporta por separado y se cruza con la captura. El registro del servidor inicia en la solicitud del teléfono verificado; el muestreo adicional comienza después de lanzar los recolectores. No promete capturar un evento de proceso que ocurra y desaparezca entre muestras.

Límites: espera hasta dos horas por la siguiente conexión, captura hasta treinta minutos, hasta 20 MiB de eventos del puente. Se arma para un único intento y termina después de su cierre. `cancel.txt` en la carpeta de monitor permite cancelarlo. La ausencia de CDP o errores de un recolector quedan registrados; no equivalen a cobertura total de la PC.

Prueba sintética aprobada: ignora la sesión antigua, dispara ante una nueva solicitud, conserva los eventos de esa sesión y se detiene automáticamente tras completar.

Monitor armado para el usuario: `D:\DNIeToolchain\LiveCaptures\tailnet-auto-94ec96768d9d449784bc0f28d82a62d4`, PID observado 52472. La ruta de la captura activa se escribe en `current-capture.txt` de esa carpeta. No incorporar capturas personales a Git. No cambió el APK ni el protocolo de lectura.
