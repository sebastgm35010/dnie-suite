# Resultado de la primera captura automática

Solicitud verificada a las 17:45:55.630 UTC; retorno a espera a las 17:46:16.453 UTC. Ventana: aproximadamente 20,823 segundos. El monitor empezó en la siguiente solicitud, excluyó historia, finalizó con `session_completed` y confirmó `collectorsStopped: true`.

Captura privada: `D:\DNIeToolchain\LiveCaptures\tailnet-auto-94ec96768d9d449784bc0f28d82a62d4\2e2cd4a6c514499ba03c920cbd09bea5`.

## Evidencia obtenida

- Sincronización Tailscale, autorización y apertura del backend confirmadas; lector real virtual presente en las cuatro muestras de metadata.
- 76 comandos PC/SC respondidos: 71 estados externos 9000, cuatro sondas iniciales 6A86 y una continuación 6102. Se observó GET RESPONSE `00C00000` y READ con desplazamiento `0CB000E7` después de lecturas protegidas.
- Se completó la secuencia MSE/GENERAL AUTHENTICATE del middleware y continuaron los SELECT/READ protegidos. No apareció INS CB: el arreglo DO85 sigue pendiente de ejercicio real.
- Última respuesta: GET UID `FFCA0000`, estado 9000. Unos 486 ms después el host observó EOF.
- Después de `session_failed type=EndOfStreamException` apareció `tailnet_session_closed type=Win32Exception` y el listener volvió a esperar. La excepción Win32 puede proceder del backend/limpieza durante el cierre; con este registro de solo tipos no se puede determinar su origen ni código nativo. No atribuirle el inicio del corte sin más evidencia.
- SCardSvr/CertPropSvc estaban Running; metadata recoge svchost 2716/2884, WUDFHost y Java, pero no permite identificar con certeza el consumidor que generó cada APDU. No hubo procesos IDplug/cddnie observados en estas muestras.

## Cobertura y siguientes datos

La captura de Edge falló y no generó `edge.jsonl`; no considerar registrado el flujo web de RENIEC. El monitor funcionó para puente y metadata de Windows, no para navegador. Para correlacionar el cierre y validar estados internos DO99 se necesita el registro APDU móvil de esta prueba. Los estados 9000 del host son externos y no prueban por sí solos lectura completa de certificados ni autenticación final RENIEC.

Pendientes: móvil de esta ventana, motivo de cierre de Android, código/fase de la excepción Win32 del backend y habilitar CDP antes de otra prueba que incluya navegador. El cierre manual confirmado por el usuario corresponde al ensayo anterior; no trasladar esa confirmación a esta nueva ventana automáticamente.
