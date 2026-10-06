# Prueba de DNI real por NFC mediante Smart Card Reader

Se cerró Edge de diagnóstico, se detuvieron ambos capturadores mediante stop.txt y se detuvo únicamente Smart Card Reader en Android a petición del usuario. El túnel ADB inverso había sido retirado; esta prueba se hizo por conexión directa LAN autorizada para esta ocasión. El driver Bix instalado sigue disponible, sin tarjeta conectada al finalizar.

## Resultado y límites

El log aportado por el usuario muestra conexión a VPCD, resets y sondeos repetidos de aplicaciones que responden 6A82 o 6E00. No se atribuyen esos sondeos a Ciudadano Digital individualmente; pueden proceder de Windows u otros componentes.

Después aparecen las dos pruebas identificadas en el análisis estático:

| Comando | Respuesta |
|---|---|
| SELECT v2 `00A4040C07A0000000003F00` | `6A82` |
| SELECT IAS v3 `00A4040C10A000000077030C60000000FE00000500` | `9000` |

Esto demuestra una respuesta satisfactoria del DNI real a IAS v3 a través del enlace. Coincide con el criterio de reconocimiento de versión del componente oficial. No demuestra PACE, lectura de identidad/certificados, PIN, firma ni éxito de la operación completa. El log termina allí y carece de timestamps. El usuario comunica que el flujo terminó en un mensaje de colocación incorrecta; no se capturó su texto exacto.

La captura del host sí observó cddnie-app con winscard.dll y conexiones HTTPS. Edge recibió HTTP 200 para URL de validación y manifiestos ClickOnce. La cancelación posterior del manifiesto en Edge no se interpreta como fallo: puede acompañar la apertura externa. El monitor no incluía java.exe en su filtro, por lo que su ausencia del registro no descarta ejecución del motor Java.

## Incidencia de configuración

EADDRINUSE correspondía a ServerSocket.bind dentro de Smart Card Reader. La opción Listen for connections estaba activada y el proceso ya tenía un listener en 35963; sucesivos descubrimientos NFC intentaban enlazar el mismo puerto. Al detener esa app el listener desapareció. Se cambió a conexión cliente hacia el lector Bix de PC. La recomendación inicial del túnel no resolvía ese modo servidor activo; no era un error del cifrado.

## Conservación y trabajo siguiente

Registro privado ignorado por Git: `build/host-diagnostics/20261005-174950-f2cab201/registro-dni-real-conservado.zip`, verificado por CRC, con trazas filtradas, resumen de los dos SELECT, errores de capturadores y manifiesto SHA-256. No se copió el log completo aportado con direcciones de red ni datos de tarjeta. No se cambió la app Android del fork ni los binarios oficiales.

Siguiente: trazar estáticamente la validación en dcdeliverydnie3.jar, incluyendo restricciones de ATR/lector y desconexión/reset entre detección .NET y lectura Java. El transporte genérico NFC transmite APDU pero no incorpora por sí solo el motor PACE del fork. Hace falta identificar la ruta posterior antes de atribuir el mensaje a pérdida NFC o incompatibilidad de protocolo.
