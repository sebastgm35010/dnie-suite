# H10 · PDF sintético con TSA real

2026-10-07. Experimento aislado solicitado por el usuario, sin cambios del APK ni motores de producción. Firma documental RSA/SHA256 con certificado autofirmado sintético; ninguna clave privada persistida ni uso del DNI/PIN/CAN. El sellado usa servicios RFC3161 reales. No es prueba física de firma con DNI ni acreditación del software.

## Servicios y fuentes

- FreeTSA ofrece servicio gratuito RFC3161, HTTPS https://freetsa.org/tsr. Documentación: https://freetsa.org/index_en.php. Descarga de CA PEM comprobada contra SHA256 publicado 2151b61137ffa86bf664691ba67e7da0b19f98c758e3d228d5d8ebf27e044438; esa huella corresponde al archivo PEM, no a su DER. Firma de token y PKIX a esa raíz verificadas; no se afirma pertenencia a TSL Perú.
- DigiCert publica un servicio RFC3161: https://knowledge.digicert.com/general-information/rfc3161-compliant-time-stamp-authority-server. No se probó ni se presume licencia de uso general/gratuito para documentos.
- Manual ReFirma publica http://tsa.reniec.gob.pe/service: https://sp.reniec.gob.pe/app/refirma_suite/pdf/manual-1.6.pdf. La web actual https://dsp.reniec.gob.pe/refirma_suite/pcx/web/main.jsf señala T/LTA para entidades con convenio; una respuesta técnica no sustituye condiciones de acceso/distribución.
- Solicitud estándar al endpoint RENIEC: con barra final /service/ devuelve HTTP400; sin barra /service devuelve HTTP200 application/timestamp-reply, estado RFC3161 granted/Operation Okay. HTTPS también funciona desde esta PC. No se suministraron credenciales, se copiaron autorizaciones de ReFirma ni se eludió autenticación.

## Archivos para validación externa

1. D:/DNIeToolchain/TsaExperiment/RENIEC/prueba_sintetica_TSA_RENIEC.pdf
   - SHA256 ed7158c05ca27d10e53123fe89428a327a06666444fb09a2a3aaa35585979cbd.
   - TSA PSVA-TSA-RENIEC TSU-2026-01; genTime 2026-10-07T22:46:05Z (17:46:05 UTC-05).
   - Política 1.3.6.1.4.1.35300.2.1.3.2.0.105.1000.0; imprint SHA256; CMS DER 4343 bytes.
2. D:/DNIeToolchain/TsaExperiment/FreeTSA/prueba_sintetica_TSA_FreeTSA.pdf
   - SHA256 0b5c9d5663428d20d0d75f440535082f750eabefc1976840b816ff239e2977b7.
   - TSA FreeTSA; genTime 2026-10-07T22:41:47Z; política 1.2.3.4.1.

Ambos usan /ETSI.CAdES.detached, signingCertificateV2, DER y signature-time-stamp no firmado sobre SHA256 del valor de firma. Sin signingTime CMS. Solicitudes con nonce aleatorio, certReq=true, transporte HTTPS con validación normal. Verificados nonce/hash/ESS/firma TSA/EKU y vigencia mediante TimeStampToken.validate, CMS de PDF final y correspondencia del imprint. Pruebas negativas de nonce/hash y alteración del contenido fallan como corresponde. PDF renderizado con PDFBox e inspeccionado visualmente.

Verificaciones y solicitudes/respuestas están en las respectivas carpetas, fuera de Git. El script Java reproducible D:/DNIeToolchain/TsaExperiment/TsaPdfProbe.java usa PDFBox3.0.8 y BouncyCastle1.78.1 ya disponibles, sin nuevas instalaciones ni servidor simulado. Se conserva un primer experimento comparativo reemplazando solo el atributo TSA en el mismo contenido firmado, pero su texto visible menciona FreeTSA: NO distribuir ese archivo intermedio; los dos archivos indicados arriba tienen texto correcto.

## Confianza y siguiente paso

La integridad del sello RENIEC y su correspondencia se verificaron. No se declara aún confianza/revocación TSA completa. El adaptador actual de certificados devuelve PKIX_PATH:CertPathBuilderException para ese certificado: su AIA/CRL apunta a http://cdn.www.gob.pe/ecernep/root5/crt/ecpsva5.crt y /arl/ecpsva5.crl, fuera del origen RENIEC permitido por el descargador actual. El diagnóstico confirma esa incompatibilidad de ubicación; aún falta verificar la ruta completa con política específica TSA antes de afirmar su confianza. No abrir un descargador arbitrario para resolverlo.

Validador del Estado pendiente: recoger formato/nivel, fecha TSA, algoritmo, serie/ruta TSA y reconocimiento TSL. Firmante sintético sin confianza RENIEC; puede afectar el resultado global independientemente de la integridad/nivel y confianza TSA. No prometer que la interfaz mostrará todos los campos ante certificado desconocido.

Después de la validación externa: módulo TimestampClient/TokenValidator independiente de UI y NFC, política TSA propia (EKU exclusivo/crítico, políticas admitidas, confianza de servicio, ruta/revocación), orígenes públicos acotados para ROOT5 y decisión expresa de sello obligatorio/opcional. Un fallo TSA no repite la firma privada ni etiqueta B como T. Confirmar condiciones del proveedor y disponibilidad desde móvil antes de habilitarlo para producción.

## Confirmación externa aportada por el usuario

Firma Perú 1.1.0 / DSS5.11.1, validaciones del 2026-10-07 a las 17:48:07 y 17:49:19 UTC-05. Resultado reportado por el usuario, no ejecución local nueva del portal.

| Evidencia | FreeTSA | RENIEC |
| --- | --- | --- |
| Integridad PDF y firma | Se mantiene | Se mantiene |
| Formato reconocido | PAdES_BASELINE_T | PAdES_BASELINE_T |
| Raíz del firmante sintético en TSL | No | No |
| Raíz TSA en TSL | No | Sí |
| Fecha TSA UTC-05 | 17:41:47 | 17:46:05 |
| Algoritmo de firma TSA | ECDSA with SHA512 | RSA with SHA256 |
| Resultado global | INDETERMINADO | INDETERMINADO |

En ambos aparece NO_CERTIFICATE_CHAIN_FOUND / BBB_XCV_CCCBB_SIG_ANS referido a la cadena de la firma: no contiene un ancla de confianza. Es consistente con el certificado documental autofirmado sintético. El informe no muestra rechazo de integridad ni de formato TSA.

Ruta TSA RENIEC reconocida por el portal: PSVA-TSA-RENIEC TSU-2026-01 -> EC-PSVA CA ROOT 5 -> ECERNEP PERU CA ROOT 5. Certificado TSA válido hasta 2038-01-17 11:47:17 UTC-05. La prueba externa de estructura PAdES-T y reconocimiento de raíz TSA RENIEC queda superada. No se interpreta el reconocimiento de raíz como demostración de todos los controles de revocación TSA, autorización contractual, disponibilidad futura ni validación local completa.

Queda pendiente integrar el módulo TSA en Android con verificación propia de ruta/revocación ROOT5 y firmar con DNI real, esperando confirmar integridad, PAdES_BASELINE_T y confianza de ambas raíces en el portal. El APK62 y su motor no cambian en esta actualización documental.
