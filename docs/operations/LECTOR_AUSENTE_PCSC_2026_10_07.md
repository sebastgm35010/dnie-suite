# Lector instalado pero ausente de PC/SC — 2026-10-07

Sesión Tailscale activa; consulta autenticada del agente: Running, CardPresent=true, sin error. Eso describe la interfaz privada del backend, no acredita enumeración en WinSCard.

Comprobaciones reales: PnP reconoce `ROOT\SMARTCARDREADER\0001`, estado OK/código 0; interfaz estándar habilitada. Sin embargo `SCardListReaders` enumera solo el lector legado y Windows Hello. La consulta PnP de tarjetas muestra nodos históricos no presentes. El servicio SCardSvr está Running y su SID coincide con la ACL del INF.

No se reinstaló el minidriver ni se modificaron Hello, CertPropSvc, SCardSvr, firewall o drivers. Se solicitó `/restart-device` solo para nuestro lector desde la terminal administrativa previamente autorizada. PnP devolvió **3010: requiere reiniciar Windows**. Después de esa solicitud el lector sigue ausente de WinSCard, aunque el backend sigue reportando tarjeta. No se reinició el equipo automáticamente; la causa última de la falta de enumeración aún no está demostrada.

Pendiente: reinicio manual, comprobar primero enumeración PC/SC con `tools/host-diagnostics/pcsc-presence.ps1`, después abrir una nueva sesión NFC y validar presencia/lectura/firma. Si no vuelve el lector, capturar arranque/reconocimiento de su interfaz por SCardSvr antes de reinstalar. La compatibilidad abierta sigue en el código; H1a de suite no sustituyó este puente ni se desplegó en Windows.

Deuda de observabilidad: diferenciar lector privado accesible, lector enumerado por PC/SC y tarjeta presente para clientes. Evitar presentar CardPresent del backend como garantía de compatibilidad. Evidencia operativa local: `D:/DNIeToolchain/Diagnostics/reader-enumeration/restart-result.txt`.

Actualización 2026-10-07: el usuario confirma que volvió a funcionar después del reinicio. Recuperación confirmada por el usuario; no demuestra causa raíz ni que sea un incidente aislado. Mantener pendiente captura de enumeración al arranque y correlación PnP/UMDF/SCardSvr si vuelve a ocurrir. No reinstalar componentes preventivamente ni atribuirlo al canal privado H1a sin evidencia.

Consulta posterior de solo lectura: último arranque Windows 2026-10-07 09:35:10 America/Bogota; `SCardListReaders` vuelve a enumerar `DNIe Bridge Android NFC Private Reader 0`, junto al lector legado y Windows Hello. En esa instantánea el lector propio no tenía tarjeta presente. Esto acredita recuperación de enumeración, no una firma nueva.

## Reincidencia tras suspensión · 2026-10-09

Usuario comunica segunda incidencia tras suspensión. Captura previa a intervención: Tailscale establecido entre agente y móvil, móvil con peer_authorized y pace_ready; agente Running / pcsc_card_present / CardPresent=true. PnP ROOT\SMARTCARDREADER\0001 OK, código 0; SCardSvr Running. WinSCard solo enumera lector legado y Windows Hello: lector propio ausente. Se confirma desfase entre interfaz privada y enumeración PCSC, no causa última de la suspensión.

Recuperación intentada: reinicio PnP solo de lector propio devuelve 3010; evento Kernel-PnP 225 acredita veto por handle abierto de DNIeBridge.Agent PID23724. Después se detiene puente por canal autenticado, se reintenta PnP y Windows exige reinicio por operación anterior. Reinicio SCardSvr ejecutado para reenumerar no recupera lector. Durante esos intentos Windows registra DriverFrameworks 10120 / 10116: bloqueo del host UMDF compartido, reportado para lector propio y Bix. PnP termina código43 / CM_PROB_FAILED_POST_START. Estos eventos posteriores no prueban que ese bloqueo fuese la causa del fallo inicial. No se reinició Windows, reinstaló ni cambió minidriver, CertPropSvc, Hello, firewall o ACL. Puente detenido, servicio agente sigue disponible.

Evidencia fuera de Git: D:/DNIeToolchain/Diagnostics/resume-reader/ contiene current-phone.jsonl, pcsc-before.json, pnp-before.json, host-before.json, deviceenum-before.json, recovery-events.json, restart-reader.txt y pcsc-after.json. No incluir logs personales en registro SQL.

Siguiente paso: reinicio manual Windows y comprobar lector con pcsc-presence.ps1 ANTES de sesión NFC; después conectar de nuevo y verificar presencia real. No insistir en reiniciar PnP con backend abierto ni considerar CardPresent privado garantía de publicación. Deuda: salud explícita WinSCard y recuperación después de energía, revisar D0Exit/D0Entry, propiedad del handle y colas pendientes; capturar trazas UMDF acotadas en próxima reproducción. El servicio actual no maneja notificaciones de energía en WindowsServiceLifetime; esto es inspección de código, no prueba causal. No desplegar una modificación del driver sin prueba sintética de suspensión y revisión de cancelación.

## Recuperación y revisión del controlador · 2026-10-09

Después del reinicio manual confirmado por usuario, WinSCard enumera de nuevo DNIe Bridge Android NFC Private Reader 0, sin tarjeta presente (flags18, ATR0). PnP código0 / OK, SCardSvr y DNIeBridgeAgent Running. Se verifica recuperación de enumeración; no se ejecuta una firma ni se acredita suspensión corregida. Evidencia: D:/DNIeToolchain/Diagnostics/resume-reader/after-reboot/pcsc.json y pnp.json.

Paquete activo oem236.inf, versión2.0.0.2. INF instalado coincide byte a byte con fuente y paquete build. DriverStore a7ead70180d6745b contiene código .text idéntico al build local; SHA256 de DLL firmada difiere del DLL no firmado, por lo que no se usa hash total como prueba de diferencia de código. Comparación guardada en driver-binary-comparison.json. No se cambió ni reinstaló driver durante esta revisión.

Hallazgos de revisión:

1. DniBridgeReader.cpp crea cola predeterminada sin EvtIoStop. QueueBackend conserva solicitudes cancelables hasta respuesta remota/timeout; D0Exit intenta retirarlas después. WDF espera completar/cancelar/posponer solicitudes entregadas antes de abandonar D0. Falta retirarlas en EvtIoStop, respetando UnmarkCancelable y propiedad del callback de cancelación. Una APDU ya entregada no debe reencolarse ni repetirse al reanudar. Riesgo confirmado por diseño, no causa del incidente demostrada por dump.
2. D0Exit purga colas manuales y D0Entry las inicia sin mecanismo explícito que acredite fin de purge asíncrono. Revisar política power-managed y coordinación, evitando una modificación basada solo en lectura de código. No se ha reproducido carrera.
3. INF no declara aislamiento de host UMDF. Eventos previos mencionaron lector propio y Bix en host compartido: un fallo compartido puede afectar ambos, sin demostrar cuál lo provocó. Para laboratorio evaluar ProcessSharingDisabled y capturas acotadas, no atribuir culpa a Bix ni cambiar ACL.
4. Servicio no acepta eventos de energía y CardPresent refleja Attach privado, no enumeración WinSCard. Incorporar estado de lector publicado distinto de tarjeta/transportes y coordinación de cierre de handles al suspender/reanudar. No reiniciar dispositivo PnP con backend abierto.

Fuentes oficiales:
- https://learn.microsoft.com/en-us/windows-hardware/drivers/wdf/using-power-managed-i-o-queues
- https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdfio/nc-wdfio-evt_wdf_io_queue_io_stop
- https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdfrequest/nf-wdfrequest-wdfrequeststopacknowledge
- https://learn.microsoft.com/en-us/windows-hardware/drivers/wdf/umdf-driver-host-process

Plan de corrección: checkpoint de paquete funcional, prueba sintética de retirada con solicitud pendiente y cancelación simultánea, EvtIoStop sin replay, revisión del ciclo de colas, diagnóstico WinSCard y captura de reanudación. Compilar/validar INF y firmar paquete de pruebas antes de instalar. Mantener el paquete instalado recuperado durante la revisión. Pendientes reproducción real y confirmación de causa por trazas/dump.


2026-10-09 · Corrección implementada/instalada y pruebas: 2026-10-09 · Controlador R1: driver2.0.0.3 oem237 instalado, PnP0 y PCSC publicado. Retiro EvtIoStop sin replay, cancelación invalida backend, purge síncrono y host UMDF aislado. Servicio/gestor distinguen ReaderPublished del CardPresent privado. 6797 comprobaciones C++ pasan, 25 PCSC nativas y 13 TLSrelay pasan; Control45 Debug/45 Release. Firma física/suspensión reales pendientes; APK76 intacta. [Alcance y pendientes](../architecture/CONTROLADOR_ENERGIA_Y_PUBLICACION_R1.md).
