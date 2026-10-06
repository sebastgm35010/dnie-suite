# Fallo real de SELECT protegido tras GET UID

El registro móvil `1-dnie-apdu-pace-android-tail2.jsonl.json` completa la evidencia Windows: el host registró 76 respuestas porque el comando 77 falló dentro de Android antes de devolver una respuesta. No concluir éxito de la sesión a partir de esos 76 retornos.

El archivo contiene cuatro sesiones. Se separaron el fallo antiguo DO85 de 1.8.8, el primer ensayo de 41 comandos cerrado manualmente y los dos ensayos posteriores:

| Sesión | Hora inicial UTC | Comandos / respuestas | Resultado |
|---|---|---|---|
| e9ad1676-ea4c-44d0-bab4-0bee2111c767 | 17:45:55.725 | 77 / 76 | SELECT 77 rechazado |
| 91cc46d1-fc48-4791-8a1f-e877d493a9c3 | 18:08:10.516 | 77 / 76 | mismo punto con IDplug activo |

En ambas, secuencia 75 READ protegido recibe 9000; secuencia 76 GET UID `FFCA0000` recibe 9000; secuencia 77 SELECT `0CA4040C` produce `transport_failed: IllegalArgumentException` aproximadamente 1 ms después. El formato del comando 77 es una APDU corta válida con Lc=29, DO87 de 17 bytes, DO8E de 8 bytes y Le=00. DO87 tiene indicador 01. Es el mismo formato que otros SELECT anteriores respondidos; no contiene DO85.

En cada ensayo, 59 respuestas protegidas completas presentan DO99=9000. El 6102 y GET RESPONSE observado pertenecen al fraccionamiento de una respuesta protegida; no se interpretan como un fallo. Tiempo local comando→respuesta: mediana/máximo 34/98 ms sin IDplug y 52/166 ms con IDplug. No son latencias de red completas.

## Interpretación y siguiente diagnóstico

La reproducción en Android en 1 ms apunta a rechazo de software del traductor, no evidencia un timeout NFC. Los tipos registrados no permiten decidir entre MAC/SSC, padding u otra validación interna; se necesita la cadena de causas y los frames del diagnóstico general. No desactivar verificación MAC ni adivinar/reajustar SSC.

La excepción Win32 que aparece después en Windows corresponde a la fase de cierre/backend y no explica por sí sola el fallo original del comando 77. El GET UID inmediatamente anterior motiva revisar la transición entre consumidores/contextos PC/SC y el contador de la sesión, pero no demuestra que GET UID corrompa PACE ni identifica al consumidor.

Pendiente: diagnóstico general de Android para identificar el punto exacto del rechazo, mejorar el registro de etapa SM sin mensajes/cargas sensibles y observar qué cliente inicia el SELECT posterior al UID. La corrección DO85 sigue sin ejercicio real en estos ensayos.
