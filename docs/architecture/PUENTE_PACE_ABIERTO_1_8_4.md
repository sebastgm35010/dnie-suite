# Puente PACE Android con APDU lógicas abiertas

Fecha de cliente: 2026-10-05. Versión 1.8.4-pace-bridge, versionCode 37.

## Comportamiento

Se retira el selector de modo transparente de la app. BridgeConnection siempre abre IAS → PKI → PACE → PKI protegido mediante RestartablePaceCard. El CAN se usa en Android; no se transmite al host Windows. TLS, pin del servidor, autorización por QR y duración limitada permanecen.

El procesador de la sesión ya no usa BridgeReadPolicy. SELECT con P2=04 y Le, selección de otros archivos, READ, GET DATA, VERIFY y otras instrucciones pasan al adaptador PACE sin la lista restrictiva. No se fabrican respuestas exitosas ni se reescriben SW del canal lógico. Se mantienen los límites de framing, tamaño NFC, integridad MAC/SSC y recolección de GET RESPONSE físico del motor existente.

La PC debe usar APDU lógicas sin una segunda sesión de secure messaging. Esta iteración no emula PACE del PC ni convierte protocolos de otros applets. Si un cliente intenta negociar su propio PACE o enviar SM ya protegido, sus bytes también se entregan al canal local; no se garantiza compatibilidad ni se oculta el error. No equivale a implementar las pseudo-APDU del lector como FFCA/UID.

Un reset crea otra conexión y otro PACE. Después de power-off, el siguiente comando reconstruye PACE antes de transmitirse, incluso si Windows no envía un reset explícito. Un fallo de reapertura cierra el puente. No se reutiliza el SSC anterior.

## Registro de esta iteración

BridgeApduTrace ahora se instala siempre en el perfil PACE. Captura las APDU lógicas que entrega la PC y las respuestas ya autenticadas y desenvueltas por Android; no son las tramas SM físicas ni una captura de los secretos de establecimiento PACE.

Cada fila añade profile=pace_android_logical y conserva session, sequence, time, event, bytes, hex y redacted. Los comandos PIN se transmiten íntegros, pero el registro solo conserva sus cuatro bytes de cabecera. Los controles reset/power-off y fallos de transporte quedan registrados. El archivo privado se acumula hasta borrar, con límite de 50 MiB; un fallo de escritura no interrumpe el tráfico. Exportación: dnie-apdu-pace-android.jsonl. Las filas anteriores permanecen distinguibles por sesión y ausencia del nuevo profile.

## Validación y límites

Ensayo real previo: PACE Android y lectura parcial CE81 desde PC/SC devolvieron 9000. La compatibilidad real del perfil ampliado con IDplug y Ciudadano Digital debe probarse después de instalar. La selección del último lector presente en Java oficial sigue siendo un problema independiente; abrir las APDU no cambia el algoritmo de selección del middleware.

Las pruebas del motor cubren entrega intacta de SELECT fuera de la lista anterior y VERIFY, reapertura tras power-off, reset y pérdida de sesión. Las pruebas del registro comprueban omisión de PIN y continuidad ante fallos de escritura. La base clausurada y la carpeta de Antigravity no se modifican.

Validación completada: ocho pruebas bridge-core, 17 bridge-android ejecutadas (dos ensayos de integración optativos omitidos), 64 pruebas de protocolo y dos del registro de la app, sin fallos. check_architecture.py pasó. assembleDebug/exportBranchApk completaron correctamente y ADB confirmó instalada 1.8.4-pace-bridge, versionCode 37. No se ha validado todavía el flujo real ampliado de IDplug/RENIEC.

## Primer ensayo real ampliado

El archivo recibido dnie-apdu-pace-android.jsonl.json contiene una única sesión nueva del perfil pace_android_logical: 12 eventos, cinco comandos, cuatro respuestas. Tres SELECT de sondeo y GET DATA 7F68 devuelven 6A86. El quinto comando FFCA000000 termina en transport_failed tras 17 ms; después se cierra la sesión. No hubo controles reset/power-off registrados. El host coincide con las cuatro respuestas y luego recibe EOF.

No hay evidencia suficiente para afirmar microdesconexión NFC: transport_failed agrupa actualmente excepciones del adaptador y de secure messaging. FFCA es una consulta de lector que el puente está envolviendo como APDU del DNI. Comparar en otro ensayo y después adaptar el UID a los datos reales de Tag, en un módulo independiente, sin enviar esa pseudo-APDU por PACE ni fabricar una identidad. No relajar MAC/SSC ni aceptar respuestas SM sin autenticación para ocultar el fallo.
