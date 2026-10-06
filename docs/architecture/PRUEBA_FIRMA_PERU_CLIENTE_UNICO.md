# Siguiente prueba: Firma Perú como cliente único

Preparada el 6 octubre 2026. Captura privada: D:\DNIeToolchain\LiveCaptures\tailnet-firma-peru-aa0d8a82c89d45668703e7083fe6ebeb.

Objetivo: comprobar firma real de un PDF sintético desde Firma Perú, separando el flujo de Ciudadano Digital y la sesión previa de IDplug. Mantener APK1.8.14, traducción PACE Android y minidriver DNI aislado. CertPropSvc/SCardSvr activos; sin cambios adicionales del sistema ni del motor.

Preparación: PDF PRUEBA-FIRMA-PERU.pdf creado sin datos personales. Cierre normal de IDplug solicitado. Firma Perú lanzada mediante su acceso ClickOnce. Monitor administrativo armado para próxima solicitud Tailscale, espera máxima2h, captura máxima300s; recolectores se detienen al cerrar la sesión. No se lanza Java de lectura paralelo. Límite de captura no desconecta por sí solo el DNI.

Usuario: detener la sesión anterior, conectar Tailscale con Traducir PACE y nueva detección NFC, esperar estado activo. Abrir el PDF en Firma Perú, elegir certificado de firma y realizar un solo intento con el PIN correcto. Registrar aviso exacto y exportar diagnósticos/APDU. Si existe PDF firmado, conservarlo para verificar la firma antes de declarar éxito. Evitar abrir IDplug/Ciudadano Digital/ReFirma en esta misma sesión.

Criterios: distinguir lector/certificado encontrado, consulta de PIN, VERIFY real, preparación criptográfica y operación de firma. Resultado esperado es documento firmado verificable, no solo certificado visible. El error conocido SM tras reset sigue sin corrección en esta versión; si se reproduce, identificar reset/comando/estado y no reiterar PIN para compensarlo. El aviso previo de bloqueo no acredita bloqueo de tarjeta.
