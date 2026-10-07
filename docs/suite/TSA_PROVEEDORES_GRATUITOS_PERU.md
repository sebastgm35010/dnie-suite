# TSA gratuitas · búsqueda previa a integración Android

2026-10-07. Solicitud: comprobar primero si algún proveedor de la TSL Perú ofrece servicio gratuito; integrar Android si no existe alternativa. APK62 y motores sin modificaciones en esta investigación.

## Hallazgo: LLAMA.PE / Watana

En https://iofe.indecopi.gob.pe/TSL/tsl-pe.xml, lista consultada secuencia25, aparece LLAMA.PE S.A., servicio TIME STAMPING AUTHORITY LLAMA.PE S.A., con estado undersupervision. Su tipo se codifica como unspecified en esta lista, no TSA/QTST. No equiparar automáticamente esa codificación peruana a clasificación europea ni cambiar el filtro CA/QC para aceptar cualquier servicio.

El proveedor anuncia explícitamente sellado gratuito mediante https://watana.pe/sellar, sin registro, para PDF ya firmado hasta 50 MB. Fuente primaria: https://blog.llama.pe/firma-digital/sellado-de-tiempo-como-darle-mayor-respaldo-legal-a-tus-documentos-electronicos (publicación 2023-07-13). La página de sellado sigue accesible al consultar hoy y mantiene el límite 50 MB. No se ejecutó un nuevo sellado en Watana ni se confirmó oferta contractual actual con el proveedor; se distingue anuncio publicado de prueba operacional.

La API oficial https://ayuda.llama.pe/integracion/watana-api documenta cuenta de proveedor, ruta y token. La operación sellar_pdf exige PDF ZIP/base64 y se indica SOLO DISPONIBLE EN PRODUCCIÓN. La oferta de acceso gratuito a la plataforma/pruebas no demuestra consumo ilimitado gratuito en producción ni un endpoint RFC3161 anónimo para integrar en esta app. El flujo web descrito requiere cargar el PDF; nuestra integración RFC3161 puede enviar solo el hash del valor de firma.

## Otras alternativas consultadas

- Camerfirma Perú aparece en la TSL como TSU CAMERFIRMA PERU SAC. https://www.camerfirma.com.pe/sellado-de-tiempo/ describe servicio contratado con usuario/contraseña; no se encontró acceso general gratuito. Certificados, CRL y documentos de política gratuitos no implican emisión gratuita de sellos.
- BPO ADVISORS aparece con servicio TSA en TSL. https://idok.pe/nosotros y https://bpoperu.idok.cl/faq/ acreditan actividad TSA, sin oferta pública de endpoint gratuito identificada en esta búsqueda.
- FreeTSA es gratuito, pero la prueba ya aportada de Firma Perú reporta raíz TSA fuera de la TSL peruana.
- RENIEC respondió técnicamente a la prueba HTTPS /service sin credenciales desde esta PC. Sus condiciones oficiales de ReFirma/convenio siguen separadas de esa respuesta; no se declara proveedor universal gratuito por esa prueba.

## Decisión de esta iteración

Hay una oferta gratuita publicada de un proveedor presente en TSL: Watana/LLAMA.PE. Se informa este hallazgo primero, conforme a la condición del usuario; no se inició integración Android en esta iteración. Pendiente determinar si se elige servicio web gratuito, contratación/API autorizada de LLAMA.PE o integración RFC3161 RENIEC ya demostrada en sintético. No se solicitan cuentas, contactos ni credenciales por iniciativa del agente.

Inventario local de nombres/tipos/estados (solo información pública) en D:/DNIeToolchain/TsaExperiment/tsl-provider-inventory.json. No se incorporan HTML de sesión, tokens ni datos privados a Git.
