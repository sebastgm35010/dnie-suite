# Plan de conformidad PAdES — diagnóstico inicial

2026-10-07. Investigación solicitada tras resultados del validador aportados por el usuario. No se modificó el motor en este incremento ni se declara conformidad/acreditación alcanzada.

## Evidencia disponible

El usuario aporta Firma Perú: PAdES_BASELINE_B; ReFirmaPCX: PAdES_BASELINE_T con TSA; PDF de la suite: PDF_NOT_ETSI, aunque íntegro, RSA/SHA256 válido y raíz del firmante reconocida por la TSL del validador. Estos son resultados reportados, no ejecución independiente del portal en este incremento.

Inspección local de la suite:

- `dnie-core/.../CmsSignatureBuilder.kt` incorpora CMS signed signingTime. EN 319 142-1 V1.2.1, 6.3 tabla 1, exige su ausencia en PAdES. El test legado exige que exista y por tanto no comprueba conformidad PAdES.
- PDF real del trabajo b9b8c2f6-18c2-4a86-bab4-3e9dc173eb65: primer identificador del /Contents es SEQUENCE con longitud indefinida (3080), BER. La norma exige SignedData DER. El builder devuelve signedData.encoded sin exigir DER.
- Ya se implementan ESSCertIDv2 / signingCertificateV2 SHA256 y SubFilter ETSI.CAdES.detached. No atribuir el problema a su ausencia sin inspeccionar cada artefacto.
- Ambos adaptadores PDF escriben /M con Calendar America/Lima. Revisar representación UTC exigida por la norma sin confundir fecha visible local y tiempo absoluto.
- El constructor admite cadena opcional, pero el adaptador PDF desktop solo pasa certificado titular. La raíz reconocida por un validador no demuestra que todos los verificadores reconstruyan la cadena ni que haya comprobación de revocación propia.
- El comentario del constructor declara conformidad PAdES y ley peruana sin prueba suficiente. Debe acotarse junto con la corrección técnica.

## Secuencia de implementación propuesta

1. Checkpoint del motor probado. Introducir perfil explícito CMS/CAdES frente a PAdES, preservando comportamiento de firmas separadas .p7s cuando corresponda. Corregir ambas plataformas mediante el núcleo compartido, no mediante parches en UI.
2. PAdES-B-B: eliminar signingTime CMS en ese perfil, exigir DER, revisar /M, ByteRange completo de la revisión firmada, Contents y certificado/ESS; resolver cadena intermedia con límites y confianza explícitos.
3. Pruebas independientes: atributos ASN.1, reconstrucción DER, hash ESS contra certificado, CMS criptográfico, PDF incremental y firmas sucesivas; validación de perfil con DSS local/sintético y prueba física en validador peruano. No subir documentos personales a un servicio externo automáticamente.
4. PAdES-B-T: adaptador TimestampAuthority separado, RFC3161 sobre valor de firma o timestamp documental permitido. Comprobar imprint, nonce cuando se use, firma TSA, EKU/política, cadena, tiempo y confianza. Resolver acceso/autorización del proveedor antes de prometer TSA RENIEC. Error TSA debe reflejar nivel realmente obtenido, nunca etiquetar B como T ni repetir firma privada automáticamente.
5. PAdES-B-LT: recuperar y validar certificados, OCSP/CRL; incorporar evidencia al DSS del PDF de forma incremental con controles de red/tamaño/tiempo.
6. PAdES-B-LTA: timestamp documental que cubra evidencia, estrategia de renovación y validación histórica.
7. Separar clasificación técnica del perfil, integridad, confianza TSL, estado de revocación y requisitos legales/acreditación IOFE/eIDAS. Usar etiquetas de nivel solo con verificación objetiva.

El sello visible permanece independiente del perfil: puede haber firma visible/invisible B o T. No trasladar PIN/CAN al módulo PDF ni alterar PACE/SSC para cumplir el formato documental.

## Fuentes y ampliación

- ETSI EN 319 142-1 V1.2.1 publicada: https://www.etsi.org/deliver/etsi_en/319100_319199/31914201/01.02.01_60/en_31914201v010201p.pdf
- DSS, documentación de Comisión Europea: https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/doc/dss-documentation.html
- Investigación delegada de normas/manuales peruanos, TSA y perfiles: [INVESTIGACION_PADES_PERU_ETSI.md](INVESTIGACION_PADES_PERU_ETSI.md).
Consulta CRL posterior: titular y dos CA sin entrada de revocación en CRL vigentes y firmadas verificadas. TSL descargada y coincidencias exactas; firma XML no verificada. [Evidencia y límites](VERIFICACION_CERTIFICADO_CRL_2026_10_07.md).
