# H11 · Sello RENIEC opcional al firmar PDF en Android

2026-10-07. Autorización explícita del usuario para integrar RENIEC como opción. Base 1f7bddd / APK62 conservada; checkpoint previo checkpoint/pre-android-tsa-h11 y D:/DNIeToolchain/Checkpoints/pre-android-tsa-h11/source.zip.

## Arquitectura

- dnie-validation-core/pe.gob.reniec.dnie.timestamp: TimestampClient, ReniecTimestampClient, TimestampTokenValidator, TimestampCertificateTrust, ReniecTimestampTrust y CmsTimestampService. Sin dependencias Android, UI o NFC.
- HTTP RFC3161 fijo HTTPS https://tsa.reniec.gob.pe/service, sin barra final. SHA256 del valor de firma, nonce aleatorio128bit, certReq y política RENIEC explícita. No PDF ni PIN/CAN en la solicitud; no redirecciones ni endpoints configurables arbitrarios. Respuesta limitada a2MiB, tiempos de conexión/lectura y plazo total.
- TokenValidator verifica correspondencia de petición, nonce/hash/política, certificado TSA exclusivo con EKU crítico, algoritmos fuertes, firma/ESS, vigencia y hora de respuesta próxima a la consulta. La política admitida es la observada en el experimento real, no aprendida de cualquier respuesta.
- ReniecTimestampTrust usa TSL Perú autenticada actual y restringe el ancla a ECERNEP PERU CA ROOT5 revisada (SHA256 e33d6a09352f98ba5cc9e358d44f3ed737e8c73f29e56656f96aa05a9d34da60). Exige PKIX y revocación GOOD del certificado TSA y sus CA antes de aceptar el token. Esto no clasifica eIDAS ni declara una acreditación del software.
- El descargador permite cuatro objetos públicos ROOT5 exactos en cdn.www.gob.pe; las ubicaciones HTTP publicadas en los certificados se convierten a HTTPS. Sigue rechazando otras rutas, credenciales, puertos, redirecciones y destinos arbitrarios.
- AndroidTimestampServices incorpora estado persistente de rollback TSL y cachés públicas fuera del ViewModel. Compose solo presenta y cambia PdfSigningOptions.reniecTimestamp, false por defecto.
- PadesSignerEngine incorpora el atributo no firmado signature-time-stamp tras una única operación privada del DNI y antes de publicar el resultado. Mantiene DER, perfil PAdES y reserva PDF32KiB. Firma invisible/visible independientes del sellado temporal. No se toca la secuencia PACE/SSC/APDU ni CmsSignatureBuilder.

## Uso y errores

En Firma PDF, o un PDF integrado en Lotes, activar «Sello de tiempo RENIEC · PAdES-T» antes de autorizar. Requiere conexión a internet. Copiar configuración al lote propaga la opción a los PDF pendientes. Los archivos separados .p7s y la suite remota Windows conservan su comportamiento anterior.

La opción exige sello verificado. Ante fallo TSA no se publica silenciosamente un PDF-B ni se repite automáticamente la firma privada; el documento aparece fallido y el lote puede continuar con los siguientes. Si el DNI ya firmó antes del fallo, esa operación ocurrió aunque no se exporte un resultado. La recuperación de CMS/PDF pendiente para reintentar solo sellado sin una nueva autorización manual queda pendiente; en esta versión se limpian temporales y el usuario tendría que iniciar expresamente otro intento. Cancelación general conserva los artefactos completos según el motor de lotes existente.

Diagnóstico de desarrollo registra solicitud/verificación/fallo TSA y trazas acotadas; sin contenido documental, nonce/hash, PIN/CAN ni números de certificado. No se añaden cambios de firewall, controladores, depuración USB ni permisos nuevos.

## Evidencia inicial

LiveEngineTsaProbe, usando el módulo nuevo compilado y BC prioritario, consulta el certificado real TSU-2026-01: TRUSTED / titular GOOD / cadena GOOD, CA intermedia GOOD. Solicitud posterior con ReniecCmsTimestampService de producción completa: PASS, CMS sellado4346bytes. Evidencia D:/DNIeToolchain/TsaExperiment/h11-live.log y h11-real-stamped.cms; el firmante documental sigue siendo sintético. No equivale a teléfono físico ni modifica certificados del DNI.

Pruebas nuevas: preservación del valor de firma al incorporar token, nonce/imprint incorrectos, token falsificado/no confiable, tiempo/política inválidos y rechazo de sellado duplicado. En PDF Android: token sintético incorporado con firma documental válida, TSA desactivada sin red, y fallo TSA sin publicación ni repetición de operación privada. Validación real externa previa H10 confirmó PAdES_BASELINE_T y raíz TSA RENIEC en TSL; la integración física APK63 requiere prueba propia.

## Pendientes

Instalar APK63, activar TSA, firmar un PDF real con DNI y validar en Firma Perú. Recoger estado global, PAdES_BASELINE_T, raíz del firmante/TSA en TSL y hora TSA. Disponibilidad y condiciones generales del proveedor, recuperación/reintento de solo sellado, preflight online del titular, LT/LTA e integración equivalente Windows siguen pendientes. No confundir que el endpoint responda desde la PC con una oferta pública gratuita ilimitada.

## Verificación de compilación

254 pruebas pasan: validation-core40, core51, signing-core10, PDF Android23, app Debug76, signing-link23, signing-desktop22, PDF desktop9. Primera compilación Debug completa correcta 3m28s, D:/DNIeToolchain/Temp/tsa-h11-tests.log. Android comparte una instancia serializada de TSL entre certificados y TSA para evitar escrituras concurrentes del estado de rollback; la UI muestra fase TSA y resultado sellado. Comprobación final app/APK tras estos ajustes en tsa-h11-final.log. Release63 no compilado; no hay dependencias externas nuevas.

Compilación final app/Debug correcta 1m25s y 76 pruebas app pasan. Artefacto APK63: D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc15-tsa-reniec-debug.apk. ADB sin dispositivos; distribución por mensajero Telegram autorizado, entrega se confirma por separado.

SHA256 APK63: 6A00B98B1CECE026307492413B2E866BEA1F92C99253819BD7D7E6F760558D92.
