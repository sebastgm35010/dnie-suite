# Confianza TSL Perú y cadena completa · H9

2026-10-07. Checkpoint previo `checkpoint/pre-trust-tsl-h9` sobre 8b8442a, source.zip en D:/DNIeToolchain/Checkpoints/pre-trust-tsl-h9. Usuario confirma funcionamiento de consulta H8 en Android; no implica prueba física H9.

## Motor y política de confianza

Extensión del módulo JVM independiente `dnie-validation-core`; no cambia PACE, SSC, APDU, firma PDF/CMS ni puentes. Android adapta preferencias/red mediante repositorio, ViewModel coordina y Compose presenta resultados. Windows instalado no se redistribuye en este incremento.

1. Ancla AAC independiente de la lista: certificado público publicado por INDECOPI en https://recursos.indecopi.gob.pe/iofe/crt/ca_root_indecopi.crt, descargado por HTTPS y convertido de PEM a DER sin cambiar certificado. DER SHA256 `6cf85e75ca1cf9d560fdc5ebaff30cea157cde223a068c3e1e051d123d5f9375`, incluido como recurso público. No usa almacén Windows ni raíces aprendidas del documento.
2. TrustedListVerifier verifica el perfil PE v3 publicado: XML UTF-8 acotado a 2 MiB/10.000 elementos/profundidad 40, sin DTD/entidades externas; una firma directamente en raíz; IDs únicos; referencias solo a raíz/KeyInfo/SignedProperties internos; C14N 1.0 mediante Santuario 2.3.5 y RSA SHA256 mediante JCA; digest raíz SHA256 y todos los digests firmados verificados. No ejecuta XPath/XSLT ni resuelve URIs externas. Perfil distinto/algoritmos no soportados falla sin confianza.
3. Excepción explícita de interoperabilidad: TSL oficial usa SHA1 exclusivamente en referencia auxiliar a KeyInfo. Se permite únicamente allí dentro de SignedInfo firmado RSA SHA256; no se habilita SHA1 para firma del XML, digest del contenido o firma de documentos.
4. Firmante debe encadenar por PKIX a AAC fijada y ser apto/vigente. Se autoriza además el certificado actual del rol TSL mediante SHA256 `bc32323c7ba0af62bdef9ca7e7f499bb71eeb4fa092b9017e15acc4d0bf612ed`, obtenido del certificado cuya firma verificó la AAC independiente. No se autoriza a cualquier certificado emitido por AAC. La rotación del firmante/ancla requiere revisión y actualización de política en una nueva versión, no aceptación automática de claves incluidas en XML.
5. Perú/versión 3, fecha de emisión, NextUpdate y secuencia verificados. Piso distribuido 25. Android persiste secuencia/hash aceptados solo DESPUÉS de firma y CRL del firmante verificadas. Menor secuencia o mismos número/diferente XML se rechazan. Esta protección depende del estado de instalación; borrar datos/restaurar respaldo antiguo vuelve al piso distribuido. No es contador de hardware ni protege un dispositivo comprometido.
6. CRL del firmante: https://recursos.indecopi.gob.pe/iofe/crl/indecopi_tsl.crl; URL debe figurar en su certificado. Firma, emisor, alcance y fechas verificados por motor CRL. Si estado no es GOOD, lista no se usa. Fuente de confianza descarga SOLO estos dos URLs HTTPS exactos, sin redirecciones, con límites y cancelación.
7. Solo identidades X509 de servicios CA/QC actualmente `undersupervision` y StatusStartingTime <= ahora. Servicios RA/software, retirados o con extensiones de alcance no soportadas no dan anclas. No se interpreta ServiceHistory: validación ACTUAL de certificados, no fecha histórica de una firma. `CA/QC` en TSL peruana no se convierte en declaración de cualificación europea.
8. Raíces autofirmadas admitidas por esa lista permiten construir PKIX completo; resto de cadena viene de AIA restringida o identidades TSL. Toda CA de ruta que esté explícitamente listada debe tener estado actual admisible. Vigencia/fortaleza y algoritmos SHA256/384/512 comprobados, incluidas restricciones PKIX. Se consulta revocación del titular y cada CA intermedia; la raíz es ancla, no se consulta como si fuese un certificado subordinado. Algoritmos/CRL parciales no soportados mantienen estado indeterminado.

## Resultados y rendimiento

Confianza de ruta y revocación son dimensiones distintas. Ruta TRUSTED con revocación UNKNOWN no se presenta como validación completa. Se preserva evidencia de revocación del titular aun si TSL no está disponible. Fecha límite conjunta usa mínimo de certificados/lista/CRL de firmante/OCSP y CRL intermedias; si evidencia caduca antes de terminar consulta, no devuelve cadena GOOD.

TSL autenticada cacheada en memoria hasta mínimo de una hora/NextUpdate/CRL de firmante/vigencia del firmante. Caché de payloads CA/CRL restringida a 16 entradas y 4 MiB, entradas <=2 MiB, cinco minutos con reloj monotónico; siempre se vuelven a verificar firmas, alcance y fechas, no se cachean decisiones confiables indefinidamente. OCSP sigue consultándose. No se amplía límite móvil de CRL de 8 MiB: la CRL titular de unos 30 MiB requiere OCSP o se informa UNKNOWN. Red corre en IO interrumpible, fuera de NFC y UI.

Pantalla Certificados -> Comprobar confianza y revocación: resultado de titular, cadena TSL, revocación de CA y límite de evidencias. No instala certificados en el sistema ni vuelve a tocar el DNI para consultar red. Registro persistido contiene solo secuencia/hash públicos, sin PIN/CAN/documentos. OCSP conserva envío del CertID estándar al emisor.

## Evidencia

Prueba real JVM con certificado público del DNI: TSL secuencia 25 autenticada y firmante sin revocación; ruta TRUSTED; titular GOOD; cadena GOOD. Resultado privado sin nombre/serie en D:/DNIeToolchain/CertificateAudit/2026-10-07/h9-engine/real-result-final.txt. Consulta completa, no sustituto de prueba física APK.

14 pruebas nuevas: listas auténticas/adulteradas/caducadas/futuras, falta de ancla, territorio incorrecto, referencias externas, IDs duplicados, DTD/entidades/profundidad, wrapping, servicios retirados/tipo incorrecto/alcance no soportado; cadena completa, CA revocada, CA sin evidencia, TSL fuera de línea, CA retirada pese a raíz activa y rollback/reemplazo de igual secuencia. Regresión: 229 pruebas, incluidos PDF Android/desktop y CMS. Log D:/DNIeToolchain/Temp/trust-h9-final.log; recompilación final de pin/fechas/licencias y variantes Debug/Release en D:/DNIeToolchain/Temp/trust-h9-final-release.log. Primer Release falló por binder SLF4J opcional ausente; se incorporó backend NOP MIT y regla acotada para constructores de canonicalización, sin suprimir advertencias de seguridad genéricas.

## Pendientes delimitados

- H9 APK60 prueba física de panel/confianza y nueva firma PAdES B; motor real JVM sí comprobado.
- Preflight online automático antes de firma, política explícita para offline/UNKNOWN, evidencia durable verificada. H9 sigue consulta manual: constructor CMS conserva bloqueo local y revocación titular conocida H8; no afirmar bloqueo automático de CA revocada/TSL retirada en toda firma.
- Validación histórica ServiceHistory con tiempo confiable, calificadores/políticas de otros servicios, cambios de firmante/ancla y nuevos perfiles TSL; XML desconocido no se confía.
- TSA RFC3161/PAdES T, luego incorporación DSS de certificados y evidencias para LT, sellos/renovación LTA. Ninguno implementado H9; PAdES B permanece.
- Acreditación de producto IOFE y cualificación eIDAS son procesos separados; no se declaran por esta validación técnica.

Fuentes: [ROPS oficial](https://www.gob.pe/59924-registro-oficial-de-prestadores-de-servicios-de-certificacion-oficial-rops), [TSL](https://iofe.indecopi.gob.pe/TSL/tsl-pe.xml), [ancla AAC](https://recursos.indecopi.gob.pe/iofe/crt/ca_root_indecopi.crt), [DSS](https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/doc/dss-documentation.html), RFC5280/6960 y ETSI TS102231. Canonicalización usa Apache Santuario 2.3.5 (Apache-2.0), sin JSR105/StAX en producción Android; licencias/avisos y dependencias en THIRD_PARTY_VALIDATION.md y assets.

## Artefactos finales

APK 60 / 2.0.0-rc12-trust Debug: D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc12-trust-debug.apk
SHA256: F40053F575E00E7019D0265200C358034EA938C49063A582E160150F4DD2C512.
229 pruebas finales sin fallas; Debug y Release compilados, BUILD SUCCESSFUL 4m28s en trust-h9-final-release.log. Root público y cinco archivos de licencias/avisos presentes en APK Debug. Release se conserva como candidato de compilación, no instalado ni sustituye Debug.

Solicitado envío único mediante mensajero vigente; confirmar entrega por separado. No se instaló por ADB. Prueba: leer DNI, seleccionar certificado Firma y pulsar Comprobar confianza y revocación. Esperado con red disponible: cadena verificada con TSL Perú y titular/CA intermedias sin revocación en evidencia verificada. Si no se obtiene evidencia verificable se informa indeterminado.
