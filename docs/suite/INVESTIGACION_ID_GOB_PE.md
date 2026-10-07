# Investigación ID Gob.pe: restos útiles y descartes

Fecha: 2026-10-07, America/Bogota. Subagente investigador, solo lectura; contrastado con motores/documentación del repositorio. No se incorporó código ni dependencias, no se ejecutó middleware y no se modificaron servicios.

## Resultado

No se encontró un motor nuevo de DNI, PACE, PC/SC o firma que mejore el núcleo existente. [info.id.gob.pe](https://info.id.gob.pe/) sigue sirviendo contenido y enlaza diez repos: cinco SDK y cinco ejemplos de integración web/OIDC. No son el código del middleware que accede al DNI. Evitar abrir una línea de adaptación de tarjeta basada en estos clientes HTTP.

## Referencias nuevas con alcance limitado

- [Perfiles ACR Java](https://github.com/idgobpe-sdk/idgobpe_sdk_java/blob/a14d63f4ad12d6addc4a8057ae8300db1700a63c/src/main/java/pe/gob/id/sdk2/common/Acr.java): `certificate_dnie` y `certificate_dnie_legacy` permiten solicitar perfiles distintos al proveedor OIDC. No demuestran V2/V3 ni describen operaciones físicas. Solo serían útiles para futura identidad web con proveedor operativo y cliente autorizado; no para la firma privada local.
- [Cliente de popup JavaScript](https://github.com/idgobpe-sdk/idgobpe_sdk_js/blob/f22dc15af0123d1f00311d7fe2d4c670db76fc8a/app/auth.js): eventos de apertura, conexión, autenticación y cancelación; correlación state/nonce y origen. Referencia conceptual para un futuro flujo navegador-suite. No contiene puente al middleware. No copiar: usa Math.random y destinos postMessage amplios; decodificar JWT no verifica su firma. Ensayo futuro sin DNI: ventanas simuladas, rechazo de origen/emisor incorrecto, nonce y repetición.
- [Adaptador TLS Java](https://github.com/idgobpe-sdk/idgobpe_sdk_java/blob/a14d63f4ad12d6addc4a8057ae8300db1700a63c/src/main/java/pe/gob/id/sdk2/utils/MySSLConnectionSocketFactory.java): acepta certificados sin validación y desactiva verificación hostname. Descartado como dependencia o modelo de seguridad para el transporte privado.

## Soporte y licencias

El investigador verificó commits de SDK de abril/mayo 2023 y ausencia de archivado formal: inactividad visible no equivale a anuncio oficial de fin de soporte. [Inventario primario](https://api.github.com/users/idgobpe-sdk/repos?per_page=100). En los diez árboles revisados no se localizó LICENSE; manifests declaran Apache-2.0 (Java/C#), MIT (PHP) e ISC (JS/Node). Son declaraciones de empaquetado, no un texto incluido que permita afirmar revisión completa de licencia/procedencia. No adoptar código en este incremento.

Comprobación puntual del investigador: info respondió 200; micuenta/apps 503; sgd sirvió estructura de aplicación. No prueba descontinuación definitiva ni disponibilidad de autenticación de extremo a extremo.

## Decisión para el proyecto

Conservar las referencias de identidad web; continuar suite privada con nuestro protocolo semántico y transporte autenticado. No sustituir CMS/PACE ni reabrir soluciones ya probadas. Estos clientes no explican el lector ausente de PC/SC: no administran PnP, UMDF ni SCardSvr. La recuperación tras reinicio está documentada por separado y la causa raíz sigue pendiente.
