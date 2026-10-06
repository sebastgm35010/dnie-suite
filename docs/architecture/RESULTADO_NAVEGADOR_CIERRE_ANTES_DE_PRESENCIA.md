# Ensayo navegador: cierre antes de publicar tarjeta

6 de octubre de 2026. Captura tailnet-browser-2d7975212e814f4a85894b46e2303580.

La solicitud sí alcanzó al servidor: peer verificado 22:20:38.840 UTC, sincronización 22:20:38.904, autorización y backend abierto 22:20:38.958. A las 22:20:46.938 ocurre session_failed type=IOException (7,980 s después de abrir el backend); después backend_shutdown_failed Win32Exception code=5 y retorno a espera.

No aparece pcsc_card_present, reset ni solicitud APDU. Los muestreos Windows mantienen el lector puente sin tarjeta presente. La conexión de red y autenticación sí se completaron; la tarjeta no llegó a exponerse al PC/SC.

El código espera primero la respuesta ATR del teléfono antes de llamar device.Attach. El registro actual no distingue fallo de ese intercambio de un fallo posterior de attach antes del evento de presencia. Win32 code5 se registra durante limpieza y no demuestra por sí solo la causa inicial; no atribuir el cierre a navegador, PACE, microcorte NFC o permisos sin más evidencia.

La captura del navegador muestra carga inicial del sitio pero ninguna nueva operación registrada en la ventana del cierre. No se ejecutó un lector Java paralelo. ETW inició y terminó con código0; monitor session_completed y collectorsStopped=true. Servidor retorna a espera.

Pendiente correlacionar exportación diagnóstica y APDU del teléfono de 17:20:38–17:20:47 Bogotá (22:20 UTC), especialmente respuesta ATR, causas encadenadas de transporte y cancelación. Si no discriminan, añadir eventos de fase y causa originales antes de cleanup, evitando datos personales.

## Correlación confirmada con Android19 / diagnóstico19

El APDU19 contiene un único opening_failed, sin session_started ni comandos del PC. Su cadena es IllegalStateException > IllegalStateException > CardDisconnectedException > TagLostException.

Diagnóstico19 (48 eventos, separado del ensayo anterior):

- 22:20:30.517 UTC: nfc_authorized.
- 22:20:38.316–38.444: TCP Tailscale listo, sincronización y autorización.
- 22:20:38.445: reset_started, apertura inicial de RestartablePaceCard; no es una solicitud de warm reset emitida por Windows.
- 22:20:40.128: MSE físico 0022C1A4 enviado; respuesta 9000 a las 22:20:41.382.
- 22:20:41.383: primer GENERAL AUTHENTICATE físico 10860000.
- 22:20:45.213–45.219: reset_failed, cadena TagLostException desde IsoDepCardChannel.transceive → PaceHandler.performPace → PaceBridgeChannel.open y cierre transport_or_card_failed.
- Los stop_requested de las 22:22:31 y 22:22:37 ocurren con estado idle, mucho después del fallo. Este cierre no fue manual.

La causa inmediata queda identificada en la comunicación NFC física al abrir PACE, antes de entregar ATR al PC/SC. Android reportó TagLostException aproximadamente 3,830 segundos después del primer GENERAL AUTHENTICATE; no hubo respuesta registrada para ese comando. Esto no identifica por sí solo movimiento físico, interferencia, corte RF o comportamiento del stack NFC. No demuestra CAN incorrecto: no se llegó a completar la autenticación/token.

La IOException Windows es posterior al fallo móvil. Su Win32 code5 de limpieza sigue como incidencia independiente. No hubo lectura del navegador que provocara APDU en este intento.

Próxima prueba: nueva detección NFC (retirar y acercar de nuevo), misma versión funcional 1.8.14 y traducción PACE; confirmar pace_ready / tarjeta presente antes de ejecutar la web. No reutilizar el Tag descubierto de un intento fallido ni eliminar comprobaciones MAC/SSC. Si se reproduce con posición estable, instrumentar la apertura física y el ciclo de vida NFC, manteniendo el error original.
