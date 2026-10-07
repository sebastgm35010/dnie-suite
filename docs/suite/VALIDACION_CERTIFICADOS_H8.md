# Validación de certificados y revocación: H8

2026-10-07. Checkpoint previo checkpoint/pre-certificate-validation-h8 sobre 38863f3; source.zip en D:/DNIeToolchain/Checkpoints/pre-certificate-validation-h8. Android mantiene perfil PAdES B ya confirmado y recibe un módulo JVM independiente dnie-validation-core, sin UI/NFC/almacenamiento Android.

## Funciones implementadas

- CertificateChecks: vigencia según reloj del equipo, propósito de firma cuando aplica, rechazo de certificado CA como titular y claves RSA <2048 / EC <256. Constructor CMS comprueba antes de solicitar RSA; test de certificado vencido confirma cero operaciones de tarjeta. No altera PACE/SSC ni protocolo privado.
- CertificateLocations: AIA emisor/OCSP y puntos CRL completos. DirectCrlValidator verifica emisor, certificado firmado por emisor, firma CRL, CA/cRLSign, AKI/SKI cuando ambos están disponibles, fechas y alcance. Solo CRL completas/directas; deltas/IDP/indirectas, freshestCRL y extensiones críticas no interpretables son UNKNOWN, nunca GOOD.
- OcspValidator: CertID exacto, firma, ResponderID, emisor o delegado directo autorizado con EKU OCSP, uso/fortaleza de clave y OCSP no-check para delegado; si no-check falta requiere revocación del respondedor aún no implementada y devuelve UNKNOWN. Respuestas de error/malformadas, certificado distinto, firma falsa, fechas incoherentes o viejas son UNKNOWN. Estados GOOD/REVOKED/UNKNOWN separados. SHA1 se usa solo como identificador CertID/ResponderID, no como algoritmo de firma admitido. Firmas admitidas SHA256/384/512 con RSA PKCS#1 o ECDSA; PSS/otros no soportados aún.
- Tiempo OCSP: margen futuro máximo 5 min; con nextUpdate exige vigencia y antigüedad de thisUpdate <=24 h; sin nextUpdate admite máximo 15 min. No usa nonce y no promete respuesta generada en ese mismo instante ni tiempo confiable TSA.
- CertificatePathValidator construye PKIX con anclas explícitas y revocación automática desactivada, porque el estado se evalúa por separado. No convierte una raíz descargada en confiable. Android aún no suministra anclas autenticadas: confianza UNKNOWN.
- KnownRevocationPolicy: memoria de proceso acotada a 256 huellas de certificados. Una consulta verificada REVOKED bloquea el constructor CMS; UNKNOWN no reemplaza la evidencia. GOOD autenticado permite actualizar estado. No es caché durable de confianza ni sustituye preflight automático; al reiniciar/evictar no hay evidencia retenida.

## Red y Android

ReniecPublicSource restringe dominios exactos y rutas públicas CA/CRL; OCSP solo ocsp.reniec.gob.pe. Sin redirecciones, credenciales URL, puertos arbitrarios ni consulta libre a direcciones locales. Descargas limitadas: CA/CRL 8 MiB; OCSP 1 MiB; solicitud OCSP 4 KiB. Conexión/lectura 5s, límites de lectura y presupuesto de consulta. Cancellation del repositorio interrumpe trabajo; ViewModel usa generación para no publicar resultados de otro certificado.

Descargas CA/CRL no envían identidad personal. OCSP sí envía el CertID estándar (incluye serie del certificado y hashes del emisor) exclusivamente al respondedor de RENIEC; nunca PIN/CAN/documentos. Permiso de HTTP limitado a esos cuatro dominios para los endpoints publicados por el certificado, sin habilitar HTTP general. La evidencia se verifica criptográficamente, independiente del transporte. Puentes existentes de sockets no se modifican; regresión física de sus conexiones pendiente.

Se descubrió que CRL titular RENIEC pesa 30.871.296 bytes. Se mantiene límite móvil de 8 MiB para evitar presión de memoria: OCSP se consulta primero; si falla y la CRL excede límite, resultado UNKNOWN. No descargar/parser 30 MB repetidamente en el teléfono ni convertir esa limitación en no revocado.

Repositorio app/data/certificates ejecuta motor en IO interrumpible. ViewModel de validación separado del lector y panel independiente en Certificados → Comprobar revocación. Consulta no requiere volver a acercar DNI; usa certificado público ya leído. Se muestra vigencia/uso, estado y fecha límite de evidencia, aclarando confianza de cadena pendiente. Cambio de certificado o cierre limpia estado visible/cancela resultados pendientes.

## Evidencia y límites

Prueba real del ejecutable JVM sobre certificado público del trabajo H4: certificado apto, GOOD por OCSP firmado/vigente, confianza UNKNOWN. Evidencia sin datos personales en consola guardada fuera de Git: D:/DNIeToolchain/CertificateAudit/2026-10-07/h8-engine/ocsp-result-final.txt. No equivale a prueba física de APK/UI ni valida toda cadena/revocación de CA.

Pendientes: anclas oficiales y TSL XML autenticada/estado histórico; revocación de toda ruta y del delegado cuando no-check falta; caché durable verificada; política de preflight automático por operación antes de NFC. En este incremento la consulta online es manual y UNKNOWN no bloquea automáticamente toda firma: se bloquean invalidez local y revocación ya comprobada en proceso. No presentar esto como validación completa IOFE, TSA T, LT o acreditación del producto. El reloj del teléfono sigue siendo una entrada local no confiable para fecha jurídica.

Referencias: RFC5280 https://www.rfc-editor.org/rfc/rfc5280 ; RFC6960 https://www.rfc-editor.org/rfc/rfc6960 ; plan general PADES_CONFORMIDAD_PLAN.md.
## Entrega y verificación final

215 pruebas sin fallos: validation core 14, core 51, PDF Android 20, app 76, link 23, PDF desktop 9, desktop 22. Nuevas pruebas verifican certificados vencidos/no vigentes/usos incorrectos, CRL auténtica/revocada/caducada/falsa/delta/alcance desconocido/emisor distinto, OCSP correcto/revocado/desconocido/caducado/falso/certificado distinto/respondedor no autorizado/error, anclas explícitas y host restringido. Dos pruebas app cubren cambio de certificado/cancelación y limpieza; una prueba core verifica cero operaciones RSA con certificado vencido.

Primeros intentos de desarrollo fallaron por imports ambiguos en fixtures y límite temporal de 1 ms que ASN.1 redondea a segundos; corregidos en pruebas sin relajar política. Ejecución final log D:/DNIeToolchain/Temp/certificate-validation-final.log, BUILD SUCCESSFUL. Regresión PAdES y consumidores pasa; Windows instalado no se recompiló/distribuyó en este incremento.

APK 59 / 2.0.0-rc11-certificates:
D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc11-certificates-debug.apk
SHA256 0F73146A9D59E1F7E0AFFB14F6C27173C38DDE77FA6FD65B9395CADA6C535C0B.

Prueba UI física pendiente. Solicitud de envío al mensajero Telegram autorizada por el flujo vigente; confirmar entrega por separado. No se declara APK instalado por ADB. Después de leer el DNI, seleccionar Firma en Certificados y pulsar Comprobar revocación; debe mostrar resultado independiente de confianza de cadena. Prueba de firma nueva debe conservar clasificación PAdES_BASELINE_B.
2026-10-07: usuario confirma funcionamiento de la consulta H8 en teléfono. Entrega única Telegram confirmada, mensaje 3728. No implica prueba del nuevo panel TSL H9 ni firma con TSA.
