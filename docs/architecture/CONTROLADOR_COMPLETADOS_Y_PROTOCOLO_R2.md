# Controlador: completados, protocolo y plazos · R2

2026-10-13, America/Bogota. Revisión del informe externo de siete causas propuestas. Checkpoint previo `checkpoint/pre-driver-completion-r2`, sobre `94ce9ba`. Driver UMDF pasa de 2.0.0.3 a **2.0.0.4**; ABI, permisos, interfaces y motores Android no cambian.

## Contraste del informe

| Punto | Resultado de la revisión |
| --- | --- |
| 1. Cualquier error del backend retira la tarjeta | Confirmado. `BufferTooSmall` al transmitir podía revocar el propietario y cortar la siguiente consulta del backend. Se separa error del cliente de pérdida de sesión. Un APDU menor de cuatro bytes normalmente se rechaza antes del backend; no todo ejemplo del informe alcanza ese ramal. |
| 2. Protocolo cero tras power-off | La restricción existía. Un cliente que envía reset primero podía recuperarse; no era un bloqueo universal. Ahora se permite volver a elegir el protocolo soportado sobre la tarjeta NFC presente. Android ya reabre su canal tras power-off antes de la siguiente APDU. |
| 3. Toda cancelación retira la tarjeta | El callback afecta la única operación remota pendiente, no cualquier IRP ni las esperas de presencia. Se conserva la sesión si el backend aún no recogió el comando. Tras entregarlo, la cancelación sigue invalidando la sesión por ejecución incierta. |
| 4. Paralelismo y `DEVICE_BUSY` | Es una limitación de una sola operación en vuelo, no prueba de pérdida del lector. Una cola global secuencial bloquearía GET_REQUEST/COMPLETE detrás de la solicitud que espera su respuesta. Se conserva el modelo y se comprueba que un segundo cliente no sustituye la operación pendiente. Una cola adicional exclusiva de operaciones de tarjeta exigiría otro diseño. |
| 5. Suspensión y servicio sin eventos de energía | Hechos de código correctos, causalidad no demostrada. No se elimina la retirada segura en D0Exit ni se cambia SCardSvr/CertPropSvc. Requiere reproducción física de suspensión y trazas para atribuir la desaparición del lector. |
| 6. Reset reconstruye PACE | Es comportamiento intencional: reset retira SSC, selección y autenticación anteriores. Responder solo con ATR en caché puede conservar un estado incompatible con lo que espera Windows. Se mantienen el reset real y el cierre ante pérdida NFC/integridad. |
| 7. Heartbeat serializado bloquea Poll | Riesgo de plazos confirmado: un pulso podía esperar 30 s y consumir el plazo completo del driver. Se acota a 2 s y la respuesta de tarjeta a 25 s. El stream sigue serializado; jamás se continúa tras un pulso vencido ni se reutiliza su respuesta tardía. |

Referencias: Microsoft documenta [BUFFER_TOO_SMALL como resultado de transmisión](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/winsmcrd/ni-winsmcrd-ioctl_smartcard_transmit), [selección de protocolo](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/winsmcrd/ni-winsmcrd-ioctl_smartcard_set_protocol) y [estados del lector](https://learn.microsoft.com/en-us/windows-hardware/drivers/smartcard/smart-card-reader-states). La adaptación de apagado virtual se apoya además en los contratos existentes `TransparentNfcCard`, `RestartablePaceCard` y `TranslatedPaceCard`; no se modifica el protocolo PACE.

## Correcciones y límites

- Solo `OP_TRANSMIT` con `STATUS_BUFFER_TOO_SMALL` o `STATUS_NOT_SUPPORTED` conserva la sesión. La APDU se completa una sola vez con error y se borra su payload; no se repite ni se inventa una respuesta de éxito. El backend recibe confirmación de aceptación de su completado, distinta del error entregado al cliente PC/SC.
- Errores desconocidos, pérdida de enlace y fallos de reset/power-off siguen retirando la tarjeta. Los buffers de reset inferiores al ATR conocido se rechazan antes de enviar una operación física. Un ATR cambiado que ya no cabe no se acepta silenciosamente.
- SET_PROTOCOL verifica el buffer de salida antes de cambiar el estado, respeta el protocolo soportado y rechaza cambios mientras hay una operación pendiente.
- Cancelación antes de GET_REQUEST conserva dueño/ATR; después de GET_REQUEST cierra sesión. El lock hace atómica esa decisión. D0Exit, IoStop y timeout mantienen la invalidación y la prohibición de replay.
- Pulse de 2 s + operación de 25 s deja margen frente a los 30 s desde enqueue del driver. No garantiza un plazo absoluto si el scheduler o una API bloqueante de Windows se atascan; en esos casos se mantiene la retirada segura. La espera inicial de un DNI aún ausente continúa cancelable y sin ese límite de inactividad.

## Evidencia

Las nuevas regresiones `completion`, `protocol` y `cancel` fallaron contra el código anterior y pasan con la corrección. Se añadió también una prueba que bloquea el heartbeat: antes agotaba la cancelación externa de 8 s; ahora vence por su propio plazo sin ejecutar una APDU ni aceptar frames tardíos.

La batería C++ incluye directamente el código de producción mediante el seam WDF: 7860 comprobaciones, 200 carreras cancel/stop, 200 carreras cancel/entrega y 200 ciclos de energía simulados. No reproduce el scheduler WDF ni suspensión real. Backend .NET: 137 comprobaciones completas, de ellas 37 del contrato PC/SC. Driver Release/InfVerif y paquete nativo Release compilados sin errores ni advertencias. Arquitectura y diff comprobados.

Resultados del despliegue y pruebas del controlador instalado: [CONTROLADOR_R2_RESULTADOS.json](../operations/CONTROLADOR_R2_RESULTADOS.json). Logs, paquetes y ejecutables de prueba están en `D:/DNIeToolchain/ReaderCompletionR2`; no se agregan a Git claves, capturas personales ni binarios.

Antes de actualizar: driver 2.0.0.3/oem237, PnP código 0, SCardSvr y agente Running, host Stopped sin tarjeta, ReaderPublished false. Se preserva esta observación: el lector ya estaba ausente de WinSCard antes de nuestros cambios. Se usa el certificado local de desarrollo existente, sin crear nueva raíz de confianza ni cambiar políticas de arranque. El rollback firmado R1 permanece en `D:/DNIeToolchain/Checkpoints/driver-power-r1/rollback-package`; los binarios previos del servicio también se conservan.

Siguen pendientes una sesión/firma con DNI real tras R2 y la reproducción física de suspensión/reanudación. El éxito sintético y la recuperación de publicación al instalar no demuestran la causa original de las incidencias de suspensión.
