# Diagnóstico APDU transparente — 1.8.3

Fecha: 2026-10-05. Perfil experimental separado del perfil de certificados validado en 1.8.2. No modifica el controlador Windows ni sus ACL, el emparejamiento o TLS.

## Flujo

Activar «Diagnóstico APDU transparente» en Puente NFC · PC antes de autorizar. En este perfil el CAN local no se exige ni se utiliza: el programa de la PC gestiona PACE, applets y secure messaging. Las APDU entrantes se envían al transporte IsoDep sin política de instrucciones, selección de aplicación, reescritura ni auto-GET RESPONSE. Las respuestas de la tarjeta se devuelven intactas; también se preservan SW de error. El perfil habitual sigue usando PACE Android y su política de lectura de certificados.

`TransparentBridge`, en el núcleo JVM, diferencia los controles VPCD de las APDU. `TransparentNfcCard`, en el adaptador de conexión, abre y vuelve a abrir un canal NFC sin invocar PACE. `PinnedBridgeClient` conserva autenticación TLS, framing y una única operación en vuelo. Los controles de reset/power-off reabren/cierran IsoDep; no garantizan un ciclo físico del campo RF. El ATR sigue siendo el mapeo de los datos contactless reales. No se inventan respuestas de applets ni ATR de un DNI de contacto.

Los límites de framing (65535 bytes), capacidad NFC y timeouts del transporte permanecen. Son límites del transporte, no filtros por aplicación. Si el teléfono no puede enviar una APDU, la sesión falla: no se parte ni trunca silenciosamente el comando. El consentimiento transparente permite operaciones reales de PC sobre la tarjeta; no convierte las APDU de firma o administración en inocuas.

## Captura

`BridgeApduTrace` en `app/data/bridge` decora la tarjeta únicamente en este perfil. Guarda un JSONL privado en `filesDir/bridge-apdu-trace.jsonl`, con identificador de sesión, secuencia, hora, dirección, tamaño y hex. Se acumula entre sesiones hasta borrar. El registro de comandos VERIFY/CHANGE/RESET/ENABLE/DISABLE PIN omite el cuerpo; esta omisión afecta solo a la captura, nunca a la transmisión. Las respuestas y otros comandos se conservan completos y pueden contener datos personales.

El archivo no pertenece a Git ni al diagnóstico estructurado general. Android backup está deshabilitado. La pantalla permite exportar por SAF y borrar cuando la sesión está detenida. Al alcanzar aproximadamente 50 MiB o fallar la escritura se detiene solo la captura, se emite `apdu_trace_stopped` al diagnóstico habitual y el puente sigue comunicando. La codificación hex evita String.format por byte.

## Validación y pendientes

Pruebas del núcleo verifican el paso intacto de SELECT P2=04, MSE, GENERAL AUTHENTICATE, SM, VERIFY, GET RESPONSE y una instrucción propietaria; respuesta 61xx intacta y controles independientes. Pruebas del adaptador verifican que abrir/reiniciar/heartbeat no transmiten APDU de PACE. Pruebas de captura verifican que el PIN se transmite íntegro sin guardarse y que fallar la escritura no bloquea la tarjeta.

Validación completada: pruebas del núcleo y del adaptador, dos pruebas de captura de app, comprobación de arquitectura y compilación/exportación del APK. 1.8.3 instalada mediante ADB, versionCode 36 verificado. Las dos pruebas de integración optativa sin fixture no se ejecutaron.

La sesión real posterior a la instalación produjo 22 pares comando/respuesta sin filtrado, incluyendo SW 6A82, 6E00 y 9000. Se obtuvo una copia de la captura privada de Android por run-as para cruzar las capas; no se imprimieron sus cuerpos. Los capturadores de Edge, presencia PC/SC, eventos WMI y fases oficiales permanecen activos durante un máximo de 30 minutos. Esta evidencia confirma transporte transparente, no compatibilidad completa con RENIEC.

La prueba real completa con RENIEC todavía es necesaria. Sigue pendiente confirmar el lector elegido por su Java; habilitar paso transparente no cambia la lista ni el orden de lectores PC/SC. La captura actual está en `D:\DNIeToolchain\LiveCaptures\ead200a74e2b4d40b87e5b1fb2906b08`, restringida al usuario, administradores y SYSTEM; la sesión del host está en `D:\DNIeBridgePrivileged\723d3a77bb3f4060bad25d3e2368fab6`. Crear stop.txt en la captura detiene los observadores, no la sesión de tarjeta. Para retirar esta última, detener desde la app.

La lectura real anterior por nuestro controlador obtuvo IAS, PKI, CE81 y READ BINARY con 9000. El SELECT posterior P2=04 daba 6985 generado por la política Android del perfil habitual; en el nuevo perfil se envía físicamente al DNI y se conserva la respuesta real.

## Archivo real recibido al terminar

`dnie-apdu-transparente.jsonl.json`: 56 eventos, 24 pares de APDU, 19 SELECT y cinco GET DATA. Tras las sondas Windows se observan dos detecciones v2/v3; ambas selecciones IAS v3 devuelven 9000 sin PACE Android. No hay SELECT P2=04, READ BINARY, MSE ni GENERAL AUTHENTICATE. No se probó todavía la negociación PACE del PC.

El segundo intento se cruza con .NET reconociendo v3 y lanzando Java, que registra verificaciones v2/v3 con 0000 sin transmitir esas APDU a nuestro puente. La presencia registrada incluye simultáneamente el lector propio y Windows Hello. La selección de otro lector sigue siendo hipótesis, no nombre observado en el Java oficial. El error sigue siendo anterior a PACE; no atribuirlo a falta de PACE local por este archivo.

Siguiente experimento: cliente Java con el runtime oficial y selección explícita de nuestro lector, registrar códigos PC/SC y SW de SELECT IAS P2=0C y P2=04. Los observadores de esta captura se detuvieron al recibir el archivo; el informe privado y la copia Android final están en la carpeta LiveCaptures indicada arriba.
