# Canal privado de la suite (incremento H1a)

Contrato JVM compartido entre escritorio y Android, independiente de PC/SC, controlador,
servicio SYSTEM, UI y transporte físico. No crea un lector del sistema ni sockets de red.

`PrivateSigningCard` adapta el motor CMS/lotes existente. `AuthorizedSigningEndpoint` usa
el transceptor preparado por Android y un PIN introducido localmente. El motor de PC recibe
un PIN vacío: no se envía PIN/CAN por el protocolo. Cada firma consume autorización; la
siguiente operación verifica el PIN en el teléfono dentro de la misma conexión física.

Operaciones v1: OPEN, AUTHORIZE, CERTIFICATE (solo firma), SIGN_SHA256 (32 bytes, RSA 256
bytes), CLOSE. No APDU arbitraria, reset, enumeración de lectores, archivos ni claves
privadas. UUID de sesión y secuencia estricta; ambos son correlación, no autenticación.
Error, repetición, pérdida de respuesta, cancelación, vencimiento o límite del lote cierran
la sesión sin reintentar una operación privada. Los errores físicos no viajan al cliente.

El endpoint se crea **después** de autenticar el par y aprobar expresamente el lote en
Android. Recibe cantidad máxima (1..50) y autorización temporal (5 minutos por defecto,
configurable hasta una hora); esto no cambia la duración del puente PC/SC existente.
El presupuesto no prueba que el hash corresponda al documento mostrado: la PC autorizada
es un extremo de confianza. La UI futura debe explicar que los originales permanecen en
la PC y el teléfono autoriza ese lote. No afirmar verificación visual del documento móvil.

`SigningFrames` es binario versionado con límites antes de asignar memoria. `SigningDuplex`
debe proceder de un transporte autenticado/cifrado; el protocolo por sí mismo no cifra.
`StreamSigningExchange` y `AuthorizedSigningConnection` adaptan flujos y poseen el cierre.
El dueño del socket/accesorio debe cerrar para desbloquear IO antes de cancelar el job;
debe limitar handshakes y lecturas incompletas. No hay reconexión automática.

Estado: tests de protocolo, extremos/streaming y CMS de escritorio con tarjeta simulada.
**Todavía no existe autenticador/transportador físico de suite, vinculación UI ni prueba
real con DNI.** No se habilita un puerto público de prueba ni se reutiliza DNIBRDG1 como
si fuera este protocolo. Próximo incremento: emparejamiento propio bajo usuario normal,
negociación inequívoca del servicio, transporte TLS fijado / tailnet verificada / USB,
consentimiento móvil y primera firma real. No publicar tarjeta PC/SC para ese recorrido.

El PIN se copia a un buffer borrable de sesión; la API heredada del transceptor todavía
requiere convertirlo a String para VERIFY. No se promete borrado de esas Strings del heap.
