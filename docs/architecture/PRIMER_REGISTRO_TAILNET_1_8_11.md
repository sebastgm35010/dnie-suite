# Primer registro móvil de sincronización Tailscale

Archivo recibido: `1-dnie-apdu-pace-android-tail1.jsonl.json`. Contiene dos sesiones, por lo que no se debe atribuir el fallo antiguo a la versión nueva.

## Separación de sesiones

- `7f2b131e-eebd-4430-b853-98a3c1faf21b`: 13:56:54 UTC, prueba anterior; 77 comandos, 76 respuestas, `IllegalArgumentException` en DO85 y cierre. Ya analizada en la corrección 1.8.9.
- `1a450938-ffa6-4a3b-bb1c-8792e9003c1d`: 17:33:34 UTC, nueva prueba; 41 comandos, 41 respuestas, duración registrada 18,069 segundos y cierre sin `transport_failed`.

## Correlación con Windows

El host reutilizable registra `tailnet_peer_verified`, `tailnet_synchronized`, `peer_authorized`, `pcsc_backend_opened` y `pcsc_card_present`. Confirma que el flujo sin JSON alcanzó la tarjeta por el lector Windows. El primer intercambio PACE del middleware y los comandos protegidos posteriores llegaron a responder.

De las 41 respuestas, cuatro sondas iniciales retornaron 6A86; las otras 37 tienen estado externo 9000. Las 26 respuestas protegidas también contienen DO99=9000. Las sondas iniciales no impidieron continuar al flujo PACE y READ. No confundir 9000 externo con un estado interno sin inspeccionar DO99.

Tiempo comando→respuesta medido dentro del móvil: mediana 44 ms y máximo 129 ms. Mide procesamiento local/traducción/NFC; **no mide** la latencia completa PC↔teléfono por Internet. No restar timestamps de dos equipos para inferir latencia sin controlar sus relojes.

Último comando: SELECT protegido `0C A4 02 04`, respuesta protegida con DO99=9000. El móvil registra cierre 190 ms después de esa respuesta. Windows observa EOF, retira la tarjeta y vuelve a `tailnet_ready_for_next_session`. El listener sigue activo en `100.104.106.125:39443` al revisar.

No hay comando CB/DO85 en esta sesión nueva: **esta prueba no valida todavía el arreglo del punto de fallo anterior**. Tampoco demuestra lectura completa de certificados ni compatibilidad final con IDplug/Ciudadano Digital.

## Cierre y pendientes

No aparece TagLostException ni excepción de transporte en la sesión nueva. Esto no demuestra que el cierre fuera manual: `session_closed` carece de motivo y el host solo observa EOF. Se preguntó al usuario si detuvo/salió para exportar o se cerró sola. La actividad detiene el puente al pasar a onPause, además del botón Detener.

Confirmación posterior del usuario: **el cierre fue suyo**, por lo que esta sesión finalizó deliberadamente y no se considera una desconexión espontánea.

Pendiente mejorar el motivo de cierre en el registro móvil (usuario, ciclo de vida, EOF del host, fallo físico y cancelación) y correlacionar identificador de sesión entre sincronización y host. Por ahora se puede correlacionar por horario y secuencia sin hacer capturas sincronizadas manuales. Los registros del host y del móvil ya proporcionan evidencia útil durante pruebas remotas.

Registros privados Windows: `D:\DNIeBridgePrivileged\tailnet-9f1b2c14019b42bfa59fb0c1a1bc1a3c\host.log`. No incorporar el registro APDU completo a Git.
