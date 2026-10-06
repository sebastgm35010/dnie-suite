# Captura preparada para el flujo manual de Ciudadano Digital

## Capacidad comprobada

Este host tiene Microsoft Edge `154.0.4258.53` y Node `24.18.0`, con WebSocket nativo. La prueba local abrió un perfil separado en modo headless, verificó acceso CDP a Page/Network/Runtime, navegación de `about:blank`, consulta de estado DOM y captura de un evento de consola sintético. Después cerró esa instancia. El puerto 19371 quedó sin listener. No se inició una sesión personal de Ciudadano Digital.

Microsoft documenta el protocolo DevTools y el uso de un perfil separado para instrumentar Edge: [referencia oficial](https://learn.microsoft.com/en-us/microsoft-edge/devtools/protocol/). El control es por CDP local desde CLI; no depende de tener una integración gráfica con Edge ni permite tomar una sesión ordinaria que no tenga depuración habilitada.

## Inicio cuando el usuario esté en casa

Desde PowerShell en el fork:

```powershell
& 'G:\Mi unidad\BOT TRADER\DNI_CODEX\modest-bose-evolution\tools\host-diagnostics\Start-HostDiagnostics.ps1' -Minutes 30
```

Esto abre una ventana interactiva de Edge en blanco, con perfil de diagnóstico separado. Los dos capturadores se lanzan ocultos, con un límite de 30 minutos y CDP restringido a loopback. **Usar esa ventana para el flujo**: entrar manualmente a `https://pki.reniec.gob.pe/ciudadanodigital/index.html`, descargar/abrir el componente y seguir los pasos que el usuario decida. Las credenciales y las operaciones del documento las realiza el usuario; la preparación no automatiza emisión, renovación ni cambio de PIN.

La instancia normal de Edge no se cierra ni se cambian preferencias de su perfil. Desde la revisión del diagnóstico 7, los nuevos perfiles se guardan en `D:\DNIeToolchain\EdgeDiagnostics\profile`; es almacenamiento privado del navegador y puede conservar la sesión que el usuario inicie. El perfil anterior de C: no se mueve ni elimina automáticamente. No es parte de Git ni debe compartirse como registro.

La carpeta de salida se informa al iniciar: `build/host-diagnostics/<fecha-id>`, ignorada por Git. Contiene `edge.jsonl`, `host.jsonl`, identificadores de procesos en `session.json` y errores operativos sanitizados. No enviar el perfil del navegador.

## Qué podemos ver

- Navegador: peticiones RENIEC/loopback, método, estado HTTP, tiempos, bytes, cancelaciones, navegación, creación de WebSocket y eventos de consola/errores por severidad. No se guardan encabezados, cookies, query/hash, cuerpos, mensajes SSE ni frames WebSocket. Las rutas desconocidas se sustituyen por un marcador; los argumentos/textos de consola tampoco se persisten.
- Host: cambios de procesos IDplug/Ciudadano/ClickOnce/instalador/Firma, Java/javaw, dotnet/WUDFHost, nombres de DLL relevantes cargadas, SCardSvr/CertPropSvc, presencia de lectores y estados/puertos TCP de esos procesos. No se guardan líneas de comandos, ventanas con información personal, direcciones remotas ni contenido de comunicaciones.
- CDP permite además consultar la página o realizar acciones dirigidas cuando haga falta. Capturas visuales o inspección detallada de errores se harán solo si ayudan a un paso concreto; el colector no fotografía automáticamente documentos o formularios.

El muestreo de host usa intervalos de dos segundos y puede perder procesos muy breves; la lectura de módulos puede ser inaccesible para procesos elevados. Los nombres de proceso son un filtro inicial que se ajustará si el componente usa otro nombre. No intercepta APDU ni garantiza acceso al log interno de IDplug. Cuando el componente esté instalado/ejecutándose se buscarán sus registros reales, sin asumir que existe un log soportado o un endpoint local.

La prueba real del 2026-10-05 añadió dos capturadores independientes, con el mismo `stop.txt` y límite de 30 minutos:

- `watch-process-events.ps1 -OutputDirectory <captura> -Seconds 1800`, ejecutado en Windows PowerShell elevado, registra eventos WMI de inicio/cierre de todos los procesos: nombre, PID, padre y estado de salida. No registra argumentos. Su archivo compartido permite lectura durante la captura. Los tiempos WMI pueden agruparse; no permiten medir con precisión la duración de un proceso breve.
- `python watch-live-logs.py --output <captura> --host <sesión>/host.log --seconds 1800` registra únicamente fases conocidas de los logs oficiales .NET/Java a partir del EOF inicial, y fases del puente de esa sesión. No copia texto arbitrario. Su marca temporal es la hora de observación; conservar el log original del puente para correlación precisa. No sigue archivos nuevos creados después de iniciar el capturador; reiniciarlo si el middleware cambia el archivo de registro.

Se comprobó el inicio/cierre de un proceso sintético breve y la relación real `cddnie-app.exe` → `java.exe`. La captura conjunta contiene dos reconocimientos v3 con `9000` y errores posteriores de Java con verificaciones `0000`. El nombre del lector elegido por el Java oficial no se registró directamente. El registro exportado de Android de esa ejecución sigue pendiente.

## Fin y comprobaciones

Los capturadores terminan al vencer la duración o al crear un archivo vacío `stop.txt` en la carpeta de esa captura:

```powershell
New-Item -ItemType File -Path '<carpeta de captura>\stop.txt' -Force
```

Después cerrar **la ventana de Edge de diagnóstico** para apagar su puerto CDP; detener la captura no cierra la ventana. No queda una tarea programada ni un monitor permanente.

Validación de preparación: dos pruebas Node sobre exclusión de tokens/identidad/cuerpos y conservación de estados/tiempos; prueba CDP con consola sintética; parseo de scripts PowerShell; muestreo real breve de servicios/procesos sin datos de tarjeta. El flujo real está pendiente. No se modificó la aplicación Android ni se generó otro APK.

La instalación del middleware sigue necesitando privilegios de administrador, según [inspección del host](INSPECCION_HOST_RENIEC.md). Capturar Edge no resuelve ese requisito.
