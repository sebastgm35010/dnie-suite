# Investigación técnica: PAdES, confianza Perú y conformidad europea

Fecha: 2026-10-07. Investigación documental y lectura de código; no cambia motores, sesiones ni software instalado. Las fichas del validador son evidencia aportada por el usuario, no una validación independiente nueva.

## Resultado y causa probable

El usuario reporta nuestro PDF como válido y con raíz del firmante en TSL, pero `PDF_NOT_ETSI`; Firma Perú devuelve `PAdES_BASELINE_B` y ReFirma `PAdES_BASELINE_T`. Son dimensiones distintas: la firma RSA puede ser íntegra y confiable para ese validador sin satisfacer un perfil estructural PAdES.

Hay dos incumplimientos concretos que investigar primero, sin alterar NFC/PACE:

1. `dnie-core/.../crypto/CmsSignatureBuilder.kt` conserva y añade `CMSAttributes.signingTime`. Su test actual exige ese atributo. Para PAdES Baseline está prohibido; la hora declarada pertenece a `/M` del PDF.
2. El builder devuelve `signedData.encoded` sin fijar DER. El agente principal verificó el PDF físico H4 del trabajo `b9b8c2f6-18c2-4a86-bab4-3e9dc173eb65`: `/Contents` comienza con `30 80`, secuencia de longitud indefinida BER. PAdES exige `SignedData` DER.

`JvmPdfSigner` ya configura `/SubFilter /ETSI.CAdES.detached` y `/M`. El builder ya incorpora `signingCertificateV2` con hash SHA-256 del certificado y certificado del firmante: no atribuir el problema a ausencia de ESS. La causa exacta de la etiqueta del validador todavía requiere comparar un PDF nuevo corregido. No basta cambiar el texto del perfil ni el SubFilter.

La cabecera del builder afirma conformidad PAdES y legal peruana sin evidencia suficiente; debe sustituirse por una descripción verificable. Su cadena CA es opcional y escritorio actualmente no la suministra, pese al comentario de cadena embebida.

## Base normativa técnica

Referencia publicada comprobada: [ETSI EN 319 142-1 V1.2.1, enero 2024](https://www.etsi.org/deliver/etsi_en/319100_319199/31914201/01.02.01_60/en_31914201v010201p.pdf), cláusulas 4.2 y 6.3, tabla 1 y notas e–q. Requiere DER, `content-type=id-data`, `message-digest`, protección del certificado, `/M`, `/ByteRange` completo salvo `/Contents`, y `ETSI.CAdES.detached`; prohíbe CMS `signing-time`. Recomienda ESS v2. La hora declarada es UTC según representación ISO; un offset local no demuestra por sí solo un instante incorrecto. Hay incompatibilidades entre `/Reason`, política explícita y compromiso que deben revisarse antes de añadir esos atributos. B-T agrega tiempo confiable; LT añade material de validación y LTA protección temporal de ese material. La [página ESI](https://www.etsi.org/technical-groups/esi/) muestra V1.3.0 (2026-08) en aprobación; no se utilizó un borrador como referencia publicada.

Complementos primarios:

- [ETSI EN 319 122-1 V1.3.1](https://www.etsi.org/deliver/etsi_en/319100_319199/31912201/01.03.01_60/en_31912201v010301p.pdf): CAdES, atributos firmados/no firmados, protección del certificado y sello de firma.
- [RFC 5035](https://www.rfc-editor.org/rfc/rfc5035.html): `ESSCertIDv2` y `signingCertificateV2`. SHA-256 es algoritmo por defecto; el identificador vincula el certificado DER. `issuerSerial` es opcional en la estructura: no marcar su ausencia automáticamente como fallo.
- [RFC 3161](https://www.rfc-editor.org/rfc/rfc3161.html), actualizado por [RFC 5816](https://www.rfc-editor.org/rfc/rfc5816.html): petición/respuesta TSA, hash, nonce, política, token y certificado específico de timestamping. El cliente verifica firma, identidad TSA, imprint/algoritmo y correlación antes de aceptar el token; la TSA debe usar EKU crítico de sellado de tiempo.
- [ETSI EN 319 102-1 V1.4.1](https://www.etsi.org/deliver/etsi_en/319100_319199/31910201/01.04.01_60/en_31910201v010401p.pdf): procedimientos de creación/validación, política y evidencia temporal.
- [DSS, documentación oficial](https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/doc/dss-documentation.html): implementación de referencia y validador independiente. Describe fecha declarada frente a prueba de existencia, `/DSS`, sellado de firma/documento y extensión de perfiles. Recomienda obtener T cerca de la firma y construir LT cuando existe evidencia de revocación suficientemente posterior, según política. No asumir que incrustar una CRL cualquiera produce LT. DSS sirve para contraste local; evaluar su licencia y dependencias antes de incorporarlo como runtime.

## Perú: acreditación y confianza separadas del formato

[INDECOPI publica la guía y anexos de acreditación de software](https://www.gob.pe/institucion/indecopi/informes-publicaciones/5449761-guia-de-acreditacion-de-aplicaciones-de-software-de-firma-digital-sfd). Se leyó el [PDF oficial descargable](https://cdn.www.gob.pe/uploads/document/file/6177692/5449761-guia-de-acreditacion-de-software.pdf): contiene versión 4.0/revisión 2016, aunque la ficha del portal indica mayo de 2026. No llamar a ese contenido «norma técnica nueva de 2026». Para usuario final, §4.1 contempla revocación, expiración, propósito del certificado, módulo criptográfico, algoritmos, autenticidad del software, visor, registros de validación y manuales; §4.4 exige verificar procedencia, firma y estado del certificado TSA. Los niveles de seguridad/acreditación no son los niveles B/T/LT/LTA. Pendiente una matriz completa aplicable y confirmar con AAC versión/alcance vigentes al acreditar.

El [ROPS oficial](https://www.gob.pe/59924-registro-oficial-de-prestadores-de-servicios-de-certificacion-oficial-rops) identifica prestadores acreditados y publica TSL. La [TSL peruana](https://iofe.indecopi.gob.pe/TSL/tsl-pe.xml) debe tratarse como lista firmada con historia/estado de servicios; no como colección de raíces a instalar indiscriminadamente. Estar encadenado a una raíz no acredita esta suite.

Referencias regulatorias para el expediente: [Ley 27269 y reglamento, compilación oficial actualizada a agosto 2024](https://diariooficial.elperuano.pe/Normas/obtenerDocumento?idNorma=21), [información legal RENIEC](https://identidad.reniec.gob.pe/preguntas-frecuentes-identidad-y-certificados-digitales), y [resolución que aprueba Directiva 002-2024-PCM/SGTD](https://www.gob.pe/institucion/pcm/normas-legales/6302622-007-2024-pcm-sgtd) para entidades públicas. Verificar modificaciones posteriores y ámbito antes de declarar cumplimiento normativo integral.

[Firma Perú](https://www.gob.pe/22271-acceder-a-la-plataforma-nacional-de-firma-digital-firma-peru) cuenta con acreditación propia; esa condición no se transmite por usar PDFBox, DSS o el mismo DNI.

## TSA RENIEC: acceso pendiente, no asumir servicio libre

El [repositorio RENIEC](https://pki.reniec.gob.pe/repositorio/) distingue documentos históricos y vigentes: política TSA v2.3, aplicable 17/12/2025, y CPS ECEP v5.1, aplicable 04/06/2026. Consultar la [política TSA v2.3](https://pki.reniec.gob.pe/docs/docsrepo/20251203_DECLARACION_PRACTICAS_PSVA_TSA_RENIEC_v2.3RRR.pdf), no basarse solo en manuales antiguos.

El [trámite oficial de sellos de tiempo](https://www.gob.pe/31148-solicitar-certificacion-digital-solicitar-generacion-de-sellos-de-tiempo) exige convenio. El [modelo oficial de convenio](https://identidad.reniec.gob.pe/documents/d/guest/convenio-psva-tsa-sello-de-tiempo) contempla registro de IP, pagos y servicio RFC 3161. La [guía de representante TSA](https://pki.reniec.gob.pe/wp-content/uploads/2021/07/DT03-GUIA-DE-USUARIO-DEL-REPRESENTANTE-ENTIDAD-TSA.pdf) documenta altas/bajas de IP. Esto no demuestra que el endpoint utilizado por ReFirma sea público o reutilizable por terceros. No extraer credenciales ni copiar autorizaciones de esa aplicación. Confirmar endpoint HTTPS, política OID, autenticación, cuotas, condiciones, acceso para esta suite y confianza TSL; admitir proveedores configurables acreditados si procede.

El [manual oficial ReFirma PDF 1.5.4](https://sp.reniec.gob.pe/app/refirma_suite/pdf/manual-1.5.4.pdf), p.19, diferencia fecha de equipo, TSA y conservación extendida. La comparación aportada por el usuario demuestra que puede existir B válido sin TSA; no prueba obligación universal de T.

## Europa: formato interoperable no equivale a firma cualificada

El [eIDAS consolidado](https://eur-lex.europa.eu/legal-content/EN/ALL/?uri=CELEX%3A02014R0910-20240520), definiciones y art.25, distingue firma avanzada/cualificada. Esta última requiere certificado cualificado y dispositivo cualificado; producir PAdES Baseline con DNI peruano no prueba esos requisitos ni reconocimiento automático de su prestador en la UE. La meta inmediata puede ser conformidad técnica ETSI; acreditación peruana y condición jurídica europea requieren expedientes distintos.

## Plan modular propuesto y evidencia de cierre

1. **Perfil B:** introducir política explícita CMS/CAdES frente a PAdES para conservar `.p7s`; eliminar CMS signingTime solo en ruta PDF, exigir DER, corregir comentarios y tests que perpetúan la infracción. Compartir política entre PDF Android/escritorio. Revisar atributos añadidos por defecto en Bouncy Castle, no solo los escritos manualmente.
2. **Inspector de perfil:** verificar estructura ASN.1, ESS hash, ausencia de atributos prohibidos, rangos PDF, revisión incremental y CMS/RSA. Añadir regresión de firma visible/invisible y firmas sucesivas. Conservar pruebas de alteración; comprobar perfil B en validador independiente local y después con documento físico nuevo en validador peruano. No subir documentos personales a demos públicas.
3. **Confianza:** adaptadores separados de TSL, cadena y OCSP/CRL con límites de descarga, firma/fechas verificadas y política de fallo explícita. Distinguir válido, inválido e indeterminado. No confiar automáticamente en una raíz incluida en el PDF. Generar reporte sin PIN/CAN ni identificadores de equipo innecesarios.
4. **T:** `TimestampClient` independiente de UI/NFC; sello sobre el valor de firma, validación completa del token y atributo no firmado. Gestionar reserva PDF antes de firmar. Si TSA falla, no repetir operación privada: conservar estado real y decidir según política explícita si se permite publicar B; nunca etiquetar como T sin token válido.
5. **LT/LTA:** recolector de evidencia y ampliador incremental separados; datos de certificado/revocación del firmante y TSA, evidencia temporal y renovación. Validar múltiples firmas sin destruir las anteriores. La renovación requiere planificación de algoritmos/tiempo, no solo añadir una etiqueta LTA.
6. **Expediente:** matriz requisitos→código→tests→manual/evidencia, alcance real de aplicación final y proceso de acreditación. Solo declarar lo demostrado por cada ensayo y por la resolución formal correspondiente.

Pendientes concretos: nueva prueba física B corregida; revisión completa de atributos DER/PDF; cadena y revocación; accesos autorizados TSA; pruebas negativas de nonce/hash/política/EKU/expiración; evaluación independiente de perfiles y trámite de acreditación. No hay implementación ni declaración de conformidad final en este informe.
