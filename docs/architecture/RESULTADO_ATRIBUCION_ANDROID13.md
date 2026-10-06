# Resultado de atribución PC/SC: intento android13

## Evidencia móvil y correlación

El APDU recibido contiene una única sesión: `4413ca43-b575-419a-a701-7cd2c8535c82`, 2026-10-06 20:28:13.544–20:28:32.082 UTC, duración18,538s. El diagnóstico general contiene196 eventos y corresponde a esta ventana: no se mezclaron los acumulados anteriores. PACE físico completado evento6415; PACE PC solicitado6434 y completado6435, contexto `d2ff99e8-9ac8-4880-b869-aef79bc8c447:1`. No segunda negociación.

77 comandos,76 respuestas. GET UID76 local respondido; SELECT77 protegido0CA4040C falla con `TerminalSmIntegrityException`, esperado119 y `matchingSsc=[1]`. Reproduce el desfase confirmado con claves MAC actuales. El comando se rechaza antes de enviarse al DNI.

Windows registra77 solicitudes/76 respuestas. Solicitud final requestId2778 a20:28:32.3335932UTC; EOF20:28:32.5465607, limpieza Win32 código5 inmediatamente después. Relojes móvil/PC tienen desfase; correlación por sesión/orden/header. El cierre Windows no antecede al rechazo móvil.

## Captura administrativa

Monitor `tailnet-pcsc-lifecycle-900d4734d802495d9ce98dab8c1cf244` terminó por `session_completed`, `attributionReady=true`, `collectorsStopped=true`. ETW privado: `D:\DNIeToolchain\LiveCaptures\d675bb6a22044f64870dd38c36c356dc`. Dos sesiones iniciadas/paradas con código0. Archivos system22.347.776 bytes, WPP13.172.736 bytes, por debajo de sus límites. Edge no capturado.

Extracción:4.830 eventos system y7.029 WPP. WPP registra1.650 eventos PID2716 (CertPropSvc),3.680 PID2884 (SCardSvr),1.249 PID53000(host),401 PID39860(observador metadata),33 PID1880(WUDFHost). IDplugManager PID59216 confirmado en inventario y módulos, pero sin eventos WPP de ese PID en estos proveedores; ausencia no prueba inactividad.

Eventos Kernel-Process en PID2716:

- Carga PeruMinidriver64.dll20:28:14.0702737UTC.
- Carga idplug-peru-minidriver64.dll20:28:15.1148713UTC.
- Descarga idplug-peru-minidriver64.dll20:28:32.8067984UTC.
- Descarga PeruMinidriver64.dll20:28:32.8070079UTC.

Las descargas son posteriores al cierre. CertPropSvc permanece con actividad WPP hasta20:28:32.8294405UTC. Esto confirma que el minidriver se ejecuta en ese servicio durante el intento y refuerza la hipótesis de un consumidor automático. No atribuye de forma concluyente el SELECT77 ni explica todavía el reinicio SSC.

WPP conserva encabezados PID/TID/ID/tiempo, pero ProviderName y Properties aparecen vacíos con Get-WinEvent. No asignar nombres SCardConnect/Transmit/Reconnect a IDs sin TMF/símbolos. Los metadatos derivados usan límites UTC explícitos para evitar comparar DateTime de distinta zona.

Revisado el observador PID39860: pcsc-presence.ps1 solo usa EstablishContext, ListReaders, GetStatusChange y ReleaseContext; no llama Connect/Reconnect/Transmit. Sus eventos WPP no demuestran envío de APDU por la herramienta. No se modificaron servicios, driver ni motor Android durante este análisis.

## Próximo control propuesto

Comparar una inserción con CertPropSvc temporalmente detenido y restaurado al final, conservando SCardSvr, driver y PACE Android. Objetivo: comprobar si desaparece la secuencia automática y el SELECT con SSC1. No es solución de producción ni deshabilitación permanente. Antes registrar estado inicial y comprobar que se puede detener sin afectar servicios dependientes; si no, diseñar aislamiento del consumidor. No se ha ejecutado este control ni armado un monitor nuevo en esta revisión.

Si desaparece el flujo, confirmar por repetición al restaurar CertPropSvc; después probar un cliente PC/SC controlado y middleware por separado. Si persiste, atribuir el otro productor con instrumentación de llamadas o contexto cliente, manteniendo MAC/SSC y sin recuperación automática con claves/contador reutilizados. El código5 de limpieza se mantiene como deuda aparte.
