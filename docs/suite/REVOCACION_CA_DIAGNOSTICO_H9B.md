# H9b · Evidencias de revocación por CA

2026-10-07. Base b40fc9c / APK61. Checkpoint previo: checkpoint/pre-chain-revocation-detail-h9b y D:/DNIeToolchain/Checkpoints/pre-chain-revocation-detail-h9b/source.zip.

## Resultado físico y diagnóstico

El usuario confirma TSL Perú autenticada secuencia25 y ruta PKIX verificada en Android61. Vigencia/uso aptos y OCSP del titular firmado/vigente sin revocación. La revocación agregada de la cadena sigue indeterminada. Eso no significa revocación: falta completar evidencia admisible de todas las CA o mantenerla vigente al finalizar.

En PC, Bouncy Castle prioritario verifica las CRL guardadas y recién descargadas de las dos CA de la ruta real. Tamaños 735 y 752 bytes; vencimientos 2027-01-16 y 2027-06-01 respectivamente. No es el límite de descarga de 8 MiB. La ruta construida tiene titular, CA Class 2, ECEP-RENIEC y ROOT 3. Consulta completa en PC: TRUSTED / titular GOOD / cadena GOOD.

Evidencia local fuera de Git: D:/DNIeToolchain/CertificateAudit/2026-10-07/h9-engine/ca-revocation-bc.txt y path-ca-result.txt. No se incorporan certificados personales ni registros del titular a Git. No hay teléfono conectado a ADB; estas pruebas no identifican por sí mismas el fallo móvil.

## Cambio modular

- CaRevocationInspector en dnie-validation-core evalúa evidencia vinculada al emisor por OCSP/CRL; devuelve profundidad, estado, método y código seguro.
- CertificateTrustInspector agrega los resultados y sigue manteniendo UNKNOWN ante evidencia ausente/inválida, o REVOKED ante evidencia verificada de revocación. Distingue cuando una evidencia caduca durante la consulta.
- CertificateValidationReport incorpora resultados por CA y explicación de estado agregado; no cambia la generación de firmas.
- Compose presenta el resultado de cada CA. El repositorio registra únicamente etapa, estado, método y código en el diagnóstico de desarrollo; sin nombres, series, URLs ni mensajes de excepción. Los códigos no aparecen en la UI de producción.
- Pruebas comprueban revocación de CA, fallo de red seguro, CRL malformada y plazo agotado sin descargar. No se relajan políticas de firmas, vigencia, TSL ni revocación; no se tocan PACE/SSC/APDU.

## Verificación de compilación

236 pruebas pasan sin fallos: validation-core 35, core 51, app Debug 76, PDF Android 20, signing-link 23, signing-desktop 22 y PDF desktop 9. Compilación Debug correcta; registro D:/DNIeToolchain/Temp/chain-revocation-h9b.log. Release62 no compilado; no cambian dependencias. APK62 / 2.0.0-rc14-chain-revocation se distribuye en D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc14-chain-revocation-debug.apk. Prueba física por CA pendiente.

## Próxima prueba y límites

Repetir consulta en APK62 y recoger el resultado de CA intermedia 1 y 2, incluyendo código de diagnóstico si aparece. Si ambas son GOOD pero el agregado es UNKNOWN, revisar el mensaje de caducidad y vigencia del OCSP al terminar. Fallo de red, plazo o proveedor criptográfico solo se considerará causa tras observar evidencia en el teléfono.

La consulta sigue siendo manual. Preflight online automático, validación histórica, TSA y niveles PAdES T/LT/LTA siguen pendientes. La raíz de confianza se admite mediante la TSL autenticada; no se trata como certificado del titular. No se declara clasificación eIDAS ni acreditación IOFE. Las distribuciones Windows existentes no se actualizan con este APK.

SHA256 APK62: E765F2E9D72D94B529ABB368C3937BEE59A9FFBC147C29C0ED280D15900223C6. Segunda compilación y prueba app tras ajuste de texto: BUILD SUCCESSFUL 36s, chain-revocation-h9b-final.log.
