# Android16 / diagnóstico17: PACE real completado, transición PKI bloqueada

## Resultado

Sesión móvil e30a4065-45d6-4025-b9d7-a0d85acdb376, 2026-10-06 22:00:11.029–22:01:49.551 UTC: 98,522 segundos, 35 comandos y 35 respuestas, sin transport_failed. Cierre manual confirmado por el usuario y evento stop_requested7308 anterior a SocketException7309. No diagnosticar caída espontánea a partir de esa excepción.

Java original PID55116 ejecutado con filtro exacto del lector, exit0, terminó con respuesta funcional de error. Monitor finalizó por session_completed; attributionReady y collectorsStopped verdaderos. No se ejecutó validación web.

PACE PC completó MSE implícito y cuatro General Authenticate; pc_pace_ready7300, contexto dd3654c1-3c90-48c5-88f9-01242a0053b7:1. Esto confirma interoperabilidad criptográfica del cliente Java original con el endpoint, incluido CAN/token. Tres operaciones protegidas posteriores (SELECT aplicación de identidad, SELECT archivo y READ) obtuvieron respuestas externas9000; el cliente avanzó a PkiCheckerv3, coherente con lectura/parsing de identidad aceptada, sin publicar sus valores. No apareció desfase SSC ni fallo MAC.

Comando35: SELECT IAS sin SM, 00A4040C10A000000077030C60000000FE00000500. Android responde6985 porque PaceTranslationEndpoint1.8.13 bloquea comandos planos después de establecer secure. No se envió ese SELECT al canal físico. Error del lector oficial corresponde a abrir aplicación para certificados; no implica pérdida NFC ni fallo PACE.

## Corrección1.8.14

PlainCertificateReadRoute separa la adaptación del código criptográfico. Se activa únicamente dentro de un endpoint PC ya autenticado y acepta la secuencia explícita SELECT IAS -> SELECT applet PKI Generic -> SELECT certificado CE81/CE82/CE83 -> READ BINARY por offset. CLA00, SELECT P1/P2 restringidos y READ sin datos, sin SFI, Le1..256. Ningún estado autoriza PIN, firma, escritura, otros applets o archivos. Selección fallida o comando ajeno retira la ruta; READ6282 conserva el estado para representar EOF real.

Todos los comandos permitidos pasan por logical, la sesión física PACE/SM independiente existente; respuestas de tarjeta se devuelven reales, no se fabrica9000. No se usa canal NFC en claro ni se adopta SSC alternativo. Comandos planos no consumen contador SM del PC, pero sí los intercambios físicos protegidos correspondientes. Actividad protegida PC posterior invalida la selección plana; sus MAC/SSC se verifican como antes. Reautenticación, reset y close retiran la ruta junto al contexto.

Pruebas añadidas: dos sesiones criptográficas independientes con transición plana de certificados/EOF y retorno protegido sin desfase; rechazo de comandos sensibles tanto antes como durante selección de certificado; selección fallida no abre ruta. El lector Java oficial y sus JAR continúan sin modificar; Windows Hello y servicios siguen disponibles.

Estado: APK 1.8.14-reniec-pki (47) compilado, 185 pruebas aprobadas y 2 de interoperabilidad omitidas, cero fallos; arquitectura aprobada. Lectura completa de certificados y compatibilidad web siguen pendientes de prueba real. El motor oficial solo solicita firma/autenticación (CE82/CE81); aceptar CE83 en ruta no implica que esta aplicación lo lea.


Lectura/captura siguiente armada: D:\DNIeToolchain\LiveCaptures\tailnet-reader-selector-7f2f892e2ade425aab4a96ce66d27c9c. Espera dos horas para la conexión siguiente; proceso original sin ventana, resultado local privado, no validación web. Envío de APK solicitado al mensajero, pendiente confirmación de Telegram.

Entrega confirmada por Telegram: APK1.8.14-reniec-pki,30.171.450 bytes, únicamente APK e instrucciones. Instalar antes del control armado.
