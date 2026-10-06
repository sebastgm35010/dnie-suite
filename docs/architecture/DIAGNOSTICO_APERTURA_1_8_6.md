# Fallo previo a la publicación de la tarjeta

Fecha de cliente: 2026-10-05. Versión 1.8.6-bridge-diagnostics, versionCode 39.

El intento con emparejamiento 85e4ec711cfc461c9fa8207bb3cbb5fb falló antes de publicar el ATR al lector Windows. TLS se autorizó; Android registró reset_started y reset_failed 5012 ms después. El host recibió EOF, sin pcsc_card_present ni APDU. La captura APDU estaba vacía porque el decorador se construía después de abrir PACE. Este intento no evaluó el CAN de la ventana del middleware. La duración no identifica por sí sola microdesconexión, timeout, CAN incorrecto en Android o error de protocolo.

Se conserva la causa al envolver los errores en PaceBridgeChannel y RestartablePaceCard. BridgeViewModel registra DiagnosticLog.failure sin mensajes de excepción, y persiste opening_failed en el archivo APDU si la sesión aún no alcanzó PACE_ACTIVE. Ese evento contiene una sesión propia, hora y cadena de clases de excepción (máximo cuatro); no contiene CAN/PIN, claves ni payload de apertura. Los errores después de abrir siguen usando transport_failed del decorador y el diagnóstico general.

La nueva prueba del registro verifica que un fallo anterior a construir la tarjeta sea exportable y que no se copie el mensaje privado de la excepción. Se mantienen TLS, PACE, adaptación UID, integridad MAC/SSC y comandos lógicos abiertos. No se añade un reintento automático ni se relajan comprobaciones para encubrir el fallo.

Pendiente: repetir y leer la cadena de causas, o confirmar que la apertura funciona y continuar la prueba de CAN correcto en el middleware. La hipótesis de conflicto de dos PACE todavía no se confirma con ese CAN correcto.

Validación: 64 pruebas de protocolo, 17 bridge-android ejecutadas (dos optativas omitidas) y tres del registro de app, sin fallos. Arquitectura comprobada, APK compilado/exportado e instalación ADB confirmada como versionCode 39 / 1.8.6-bridge-diagnostics. Captura preparada para repetir; la causa física del fallo anterior aún no se ha identificado.
