# Canal privado de la suite (incremento H1a)

Actualización H1b: `SigningProvision` y `PhoneSigningTransport` implementan solicitud de
un trabajo y TLS inverso con huella fijada; cliente `ReverseTlsExchange` en escritorio.
La UI Android y comando `remote` están conectados al motor. Tests TLS reales con tarjeta
simulada pasan; primera firma física LAN de suite completada y verificada con .NET. El estado H1a al final es histórico.
[Procedimiento vigente](../docs/suite/FIRMA_REMOTA_H1B.md).

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


## Extensión H2

`SuitePairing` y `SuitePackets` separan confianza persistente del trabajo. `ContinuousSigningClient`/`ContinuousSigningConnection` multiplexan trabajos secuenciales dentro de un TLS autenticado; el PIN desde PC solo aparece en BEGIN y se destruye al finalizar el lote. Los frames H1a y `PrivateSigningCard` siguen requiriendo PIN vacío en el motor CMS: autorización se resuelve en el endpoint Android. `SuiteCardSlot` posee NFC/PACE entre trabajos y descarta tarjeta al fallar. [Contrato completo](../docs/suite/SESION_CONTINUA_H2.md). Sin reconexión/reenvío automático ni APDU libres.
