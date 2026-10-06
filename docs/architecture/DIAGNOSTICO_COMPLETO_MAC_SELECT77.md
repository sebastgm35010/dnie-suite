# Diagnóstico completo: MAC del SELECT 77

Archivo general recibido: `1-dnie-diagnostico-completo.jsonl`, 1.525 eventos acumulados y diez fallos de la categoría bridge. Se filtraron por horario, evento, causa y frames; no se incluyeron valores personales ni cargas APDU en este informe.

## Correspondencia con los ensayos actuales

| Evento | UTC | Sesión APDU | Punto de fallo |
|---|---|---|---|
| 5914 | 17:46:16.105 | e9ad1676-ea4c-44d0-bab4-0bee2111c767 | TerminalSecureMessaging.decode:40 |
| 6106 | 18:08:32.077 | 91cc46d1-fc48-4791-8a1f-e877d493a9c3 | TerminalSecureMessaging.decode:40 |

Ambos: `IllegalStateException > IllegalArgumentException`. La excepción externa proviene de TransparentBridge; la interna proviene de la comparación constante del MAC del extremo PC:

```kotlin
require(MessageDigest.isEqual(expected, fields[0x8e])) { "Terminal SM integrity failure" }
```

Esto confirma el rechazo de integridad del comando 77 `0CA4040C` después de GET UID. No es un fallo de formato DO85, padding, enlace NFC ni error producido por la tarjeta: la validación ocurre antes de descifrar el comando y antes de `logical(clear)`, por tanto ese SELECT no llega al DNI físico.

La evidencia Windows de 76 respuestas no cubre el comando 77; EOF y Win32Exception aparecen después del cierre Android. El rechazo se reprodujo con IDplug abierto y sin esa ventana activa. No demuestra que IDplug sea el consumidor de esas APDU.

## Separación de historia

- 13:00–13:01 UTC: errores de conexión/TLS anteriores.
- 13:33 y 13:37 UTC: CardDisconnectedException > TagLostException en ensayos raw anteriores.
- 13:57 UTC, evento 5618: rechazo del formato DO85 en la versión anterior, ya tratado por 1.8.9. Los números de línea de aquella compilación no equivalen al código actual.
- 17:33 UTC, evento 5744: SocketException del ensayo de 41 comandos cuyo cierre manual confirmó el usuario; no equipararlo a los dos rechazos MAC nuevos.
- 17:46 y 18:08 UTC: dos fallos actuales idénticos en la verificación MAC.

## Qué falta determinar

La causa inmediata está confirmada; la razón por la que PC y Android no calculan el mismo MAC todavía no. Hay que comprobar el contexto y SSC que el consumidor Windows utiliza tras GET UID, incluyendo cambios de consumidor/contexto y datos descartados antes de la traducción. GET UID es una operación local del lector y no debe modificar el SSC de la tarjeta. Su cercanía temporal no prueba que deba incrementarlo ni resetearlo.

No reenviar el comando rechazado, desactivar MAC, probar contadores hasta aceptar una trama ni reiniciar claves silenciosamente. La sesión correctamente falla cerrada ante la divergencia. La siguiente iteración debe registrar etapa de validación/contador y transiciones de contexto sin claves, CAN, PIN o texto sensible, y obtener atribución del cliente PC/SC. El diagnóstico entregado ya es suficiente para localizar el rechazo; no pedir otro registro genérico equivalente.
