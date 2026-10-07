# Verificación puntual de certificado y CRL

2026-10-07, consulta aproximadamente 12:49 America/Bogota. Certificado extraído del PDF real de la suite H4, coincidente con el certificado de firma aportado por el usuario. Los identificadores personales, certificados y resultados detallados se conservan fuera de Git en D:/DNIeToolchain/CertificateAudit/2026-10-07/.

## Resultado acotado

- Certificado titular vigente: 16/12/2025 14:22:55 a 15/12/2029 14:22:55, UTC−05. Emisor ECEP-RENIEC CA Class 2; firma del certificado verificada con el emisor descargado por AIA. KeyUsage contentCommitment/no repudio; EKU emailProtection. No interpretar por sí solo ese EKU como rechazo de PAdES; revisar políticas del validador.
- CRL titular obtenida de http://crl.reniec.gob.pe/crl/sha2/caclass2.crl, emisor coincidente y firma RSA verificable con CA Class 2. thisUpdate 06/10/2026 17:20:21 y nextUpdate 07/10/2026 17:20:21, UTC−05. Vigente al consultar; serie del titular ausente.
- CA Class 2: certificado vigente, firma verificada con ECEP-RENIEC; CRL del emisor vigente, firma verificada, serie ausente. CRL thisUpdate 20/07/2026 16:15:09Z, nextUpdate 16/01/2027 16:15:09Z.
- ECEP-RENIEC: certificado vigente, firma verificada con ECERNEP PERU CA ROOT 3; CRL del emisor vigente, firma verificada, serie ausente. CRL thisUpdate 01/06/2026 20:34:26Z, nextUpdate 01/06/2027 20:34:26Z.
- Las tres CRL inspeccionadas tienen AKI y CRLNumber, sin deltaCRLIndicator ni IssuingDistributionPoint. Resultado puntual: no se encontró revocación en las listas verificadas actuales, no una garantía indefinida ni prueba histórica de toda fecha anterior.
- TSL descargada por HTTPS desde https://iofe.indecopi.gob.pe/TSL/tsl-pe.xml: secuencia 25, emitida 10/08/2026 16:10:31.312Z, próxima actualización 10/02/2027 11:10:31.312Z. Certificados ECEP-RENIEC y ROOT 3 coinciden exactamente en DER/hash con identidades de servicios en estado undersupervision. Se examinó ServiceInformation actual; no se evaluó ServiceHistory completo.

## Límites y siguiente implementación

No se verificó la firma XML de la TSL ni su ancla oficial; por ello las coincidencias no establecen automáticamente confianza TSL. La raíz usada para verificar firmas tampoco se vuelve confiable por estar incluida en la lista sin autenticar. No se hizo validación completa de políticas/ruta RFC5280 ni OCSP, acreditación IOFE o clasificación cualificada UE. Las descargas CRL/CA fueron HTTP según las extensiones del certificado; la integridad criptográfica de cada firma se comprobó independientemente del transporte. No se cambió el almacén Windows ni el motor.

Implementar un CertificateValidationService fuera de UI: separar identidad/propiedades del certificado, construcción y validación de ruta, caché CRL/OCSP con nextUpdate y firma/scope verificados, proveedor de confianza TSL autenticado y resultado de perfil PAdES. Estados explícitos GOOD / REVOKED / UNKNOWN, fuente/fecha, fallo por datos caducados o red no disponible. Nunca traducir no disponible a no revocado. Para LT/LTA guardar evidencias verificadas que realmente se usaron, sin tratar la raíz embebida como trust anchor.

Evidencia local: audit-result.json, chain-audit.json, certificados CA, CRL y tsl-pe.xml en la carpeta D: indicada; no se incluye el certificado personal ni serial en este documento. Scripts puntuales de auditoría no son un servicio de validación de producción.

Fuentes: [TSL oficial](https://iofe.indecopi.gob.pe/TSL/tsl-pe.xml), extensiones AIA/CDP del certificado, [investigación técnica](INVESTIGACION_PADES_PERU_ETSI.md) y [plan PAdES](PADES_CONFORMIDAD_PLAN.md).