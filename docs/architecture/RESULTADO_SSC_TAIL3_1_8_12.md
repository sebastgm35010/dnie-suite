# Resultado 1.8.12: SSC reiniciado confirmado

## Separación del historial

Los archivos tail3 y diagnosticocomoleto2 acumulan pruebas anteriores. Se analiza exclusivamente la sesión móvil `f3bf83b4-71c5-43d9-a859-352bec0e2c86`, del 2026-10-06 20:15:49.102 a 20:16:09.891 UTC. Las otras cuatro sesiones APDU se excluyen de este resultado. El diagnóstico general contiene 1.737 eventos; la ventana nueva comprende los eventos puente 6191–6350, y Detener posterior 6351.

Captura Windows correlacionada: `D:\DNIeToolchain\LiveCaptures\tailnet-ssc-f09576843e51457dbdeb2281bb5149df\759d96b985df48aba16cad528f954017`. El monitor terminó por `session_completed`, con recolectores detenidos. No se obtuvo captura de Edge. Los tiempos de los equipos no son idénticos: cruzar por orden y headers, no por igualdad de milisegundos.

## Evidencia nueva

- 77 comandos y 76 respuestas en Android y Windows. Duración móvil 20,789 s.
- Respuestas externas: 71 con 9000, cuatro probes con 6A86 y una continuación 6102. Estos estados no prueban reconocimiento completo del token por IDplug ni autenticación RENIEC.
- Una negociación PACE del PC completada: `pc_pace_requested` evento 6227, `pc_pace_ready` evento 6228, contexto `5d51bb69-2433-4a48-8b73-01968037a77f:1`. No aparece segunda negociación antes del fallo.
- Comando 75: READ protegido, respondido. Comando 76: GET UID FFCA0000, respondido localmente. Comando 77: SELECT protegido 0CA4040C, rechazado antes de enviar al DNI.
- `smDiagnostic.stage=pc_command_mac`, `expectedSsc=119`, `matchingSsc=[1]`. El MAC de esa trama es válido con las claves MAC actuales y SSC=1. Queda demostrado el desfase; no es una inferencia por ausencia de otras causas.
- El comando 77 no es idéntico byte a byte a ningún comando previo de esta sesión. Esto descarta una repetición exacta de una trama anterior en este registro; no constituye una garantía general contra replay.
- Android registra `TerminalSmIntegrityException` y cierra. Windows registra requestId 2701, luego EOF y `backend_shutdown_failed type=Win32Exception code=5`. El código 5 aparece durante limpieza posterior y debe resolverse por separado; no explica el MAC rechazado.
- Windows no registra reset/power-off en esta ventana. GET UID por sí solo no demuestra que corresponda reiniciar el SSC. El cliente productor de la trama no está atribuido: requestId no es PID ni handle PC/SC.

## Conclusión y siguiente desarrollo

El emisor del SELECT vuelve a usar SSC=1 con las claves del contexto establecido, mientras Android continúa esperando 119. La causa inmediata de este cierre es integridad SM PC→Android, no una pérdida NFC observada en ese comando. Sigue pendiente explicar el ciclo de vida del cliente Windows que reinicia el contador sin una nueva negociación PACE visible.

No reajustar SSC al recibir GET UID ni aceptar automáticamente el candidato 1. Reusar claves y contadores compromete la protección contra repetición y puede reutilizar material de cifrado. La instrumentación diagnóstica permanece de solo observación y las validaciones siguen activas.

Próximo trabajo: instrumentar el ciclo de vida PC/SC y atribuir llamadas del minidriver/consumidor (conexión, reconexión, transacción, reset y transmisión) para identificar el límite de contexto. Reproducir sintéticamente 59 intercambios protegidos seguidos del SELECT con contador reiniciado, verificando que la sesión física no recibe el comando rechazado. Diseñar recuperación mediante una negociación PACE nueva, o aislamiento de contextos con identidad y límites verificables; comprobar primero qué admite el middleware. Una nueva prueba idéntica sin esa instrumentación no aporta explicación adicional.

Esta iteración no cambia el APK ni el controlador; registra el resultado confirmado de 1.8.12 y el siguiente punto de intervención.
