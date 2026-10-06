# Desconexión real: teléfono y DNI, 2026-10-06

Versión instalada: 1.8.6-bridge-diagnostics. Emparejamiento fa1598a2df174c969467aad4ca91fa7f. Captura privada D:\DNIeToolchain\LiveCaptures\8ed7b03328ae48f0933d2157b9252b97. Se detuvieron los colectores tras la notificación del usuario; no se inició una reconexión ni se borraron registros.

## Secuencia observada

- Android abrió TLS, autorizó al par y preparó PACE local; el registro persistente contiene reset_started y pace_ready.
- El tráfico del PC leyó archivos públicos y ejecutó MSE y los cuatro GENERAL AUTHENTICATE. La última respuesta fue 9000. Esto no demuestra que apareciera un diálogo CAN nuevo.
- A tiempo Android 1791291223224, comando lógico 16: cabecera 0CA40204, 38 bytes, SELECT ya protegido por el PC.
- A 1791291223251, ese comando terminó con CardProtocolException: 27 ms. No quedó una respuesta lógica registrada.
- A 1791291223275, session_closed: 24 ms después de la excepción.
- Windows recibió EndOfStreamException a 2026-10-06T12:53:43.4189962Z. Se usa su orden local; no interpretar diferencias entre relojes PC/teléfono como latencia de red.
- El buffer crash del PID capturado quedó vacío. No se observó crash de ese proceso; esto no equivale a monitorizar todos los procesos del sistema.
- Un opening_failed con IllegalStateException inmediatamente posterior es consecuencia del cierre registrado por el adaptador, no prueba de una segunda aproximación física del DNI.

## Interpretación y límite

La primera evidencia de fallo es de protocolo en Android; el EOF del PC ocurre al perder el extremo remoto. No hay evidencia de TagLostException ni IOException en la traza del comando. Esto favorece un conflicto de Secure Messaging sobre una pérdida física NFC, sin determinar todavía el subtipo exacto.

El código confirma que RestartablePaceCard aplica PaceSecureChannel a todos los comandos y este envuelve también el SELECT con CLA 0C recibido del PC. Tras el intercambio PACE originado por el PC, las claves/SSC conservadas en Android podrían dejar de corresponder al estado de la tarjeta. Esta es una hipótesis, no una validación de claves ni prueba de MAC incorrecto.

La excepción persistente de la sesión pierde la causa: TransparentBridge.process captura la excepción y lanza IllegalStateException sin cause. Por eso los frames persistentes solo muestran el cierre en TransparentBridge:19; la traza APDU sí conserva CardProtocolException. La captura actual no distingue respuesta sin protección, MAC, SSC, TLV o padding.

## Siguiente trabajo

### Control posterior sin procesos IDplug

Emparejamiento 3d42de11fe124512bb71f693f4bd824f; captura privada D:\DNIeToolchain\LiveCaptures\c0187558eb9a41df9ce470a045097159; export móvil dnie-apdu-pace-android7.jsonl.json. Se verificó ausencia de procesos IDplug antes de conectar y después del corte. Los snapshots tampoco mostraron esos procesos.

Pese a cerrar IDplug, el PC originó SELECT/READ, MSE y cuatro GA, final 9000. El primer SELECT protegido 0CA40204 (comando 16) falló con CardProtocolException: tiempo Android 1791291638746 → 1791291638772, 26 ms. Android cerró a 1791291638808; después Windows registró EOF. Se reproduce el punto exacto de fallo anterior, no un corte en reposo.

CertPropSvc y SCardSvr estaban activos. Un cliente automático de Windows/minidriver puede originar estas consultas aunque IDplug UI esté cerrado; no se capturó PID por cada APDU, por lo que el cliente preciso sigue sin identificar. No atribuir exclusivamente a IDplug UI ni al hardware del teléfono. Un SSLHandshakeException > EOFException posterior a 1791291661984 pertenece a otro intento de apertura después de finalizar el servidor de una sola sesión, no al fallo inicial.

Siguiente control: enlace sin exponer presencia PC/SC, para medir estabilidad del transporte y PACE sin clientes automáticos, o identificar el cliente de SCardTransmit. Esto debe ir separado del diagnóstico del fallo SM y no requiere desactivar servicios globales de Windows.

1. Conservar cause al propagar el fallo y añadir categorías estables de error de protocolo y SW disponibles. No registrar mensajes arbitrarios, secretos o claves; no desactivar MAC/SSC ni fabricar respuestas exitosas.
2. Probar la propiedad única del canal: PC dueño de PACE con relay físico como control, o terminación/traducción explícita si Android mantiene el canal físico. Evitar repetir el mismo SELECT doblemente protegido como supuesto arreglo de NFC.
3. Antes de otro intento real, verificar sintéticamente la propagación del error y retirada del lector; compilar e instalar la versión diagnóstica si se cambia Android.
