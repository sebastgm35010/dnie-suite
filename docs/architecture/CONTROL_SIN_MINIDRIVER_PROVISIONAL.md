# Observación provisional: canal estable sin minidriver

Usuario informa conexión mantenida y rechazo de Ciudadano Digital «Al parecer no has conectado tu DNIe de la manera correcta». Análisis durante sesión activa, antes de recibir APDU móvil/exportación final.

Captura privada: D:\DNIeToolchain\LiveCaptures\tailnet-without-minidriver-70314f9ef4274ba2830b35fa445ada8d\a51f5190206b49c9ba51586da5839c82. Estado observado22 solicitudes/22 respuestas, sin session_failed ni EOF. Dos power-off y un reset completados sin cierre de conexión. Secuencia última21:36:09–21:36:10UTC: reset, SELECT00A4040C con6A82, otro SELECT00A4040C con9000, power-off. No atribuir estos dos SELECT definitivamente a Ciudadano Digital sin PID/APDU exacta.

CertPropSvc/SCardSvr siguen activos. Asociación contactless ausente. ETW activo sin módulos PeruMinidriver/idplug-peru-minidriver observados en muestras, aunque certprop.dll/winscard/scksp permanecen. No aparece la negociación PACE PC y cadena de77 comandos que causaba el rechazo SSC119→1. Por tanto el fallo de contador no se reprodujo bajo este control; no implica que el motor acepte el SSC reiniciado ni que se haya corregido el cliente que lo produce.

Hay comunicación bidireccional real y respuesta9000 a un SELECT. El rechazo funcional de Ciudadano Digital es un problema distinto en esta ventana; faltan APDU completas para saber qué applet selecciona y qué respuesta espera. No afirmar autenticación, lectura completa de certificados o compatibilidad con RENIEC. Comparar reset/SELECT/status con móvil y registro oficial antes de modificar emulación/FCI/ATR.

Límite300s detiene recolectores, no desconecta por sí mismo Android/host. Usuario puede detener tras control90s y exportar APDU/diagnóstico general. Resultados todavía provisionales hasta fin de captura.
