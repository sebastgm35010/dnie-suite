# Corrección de validación de passkeys — 1.6.1

2026-10-05. El usuario informa que varios sitios muestran «No se pudo validar la solicitud o el sitio no autoriza esta aplicación». Ese texto provenía de nuestro catch genérico: no demostraba que Facebook u otro sitio rechazase al proveedor por falta de certificación.

Se encontró y corrigió una incompatibilidad reproducible: el parser rechazaba cualquier extensión distinta de credProps. [WebAuthn define las extensiones como opcionales](https://www.w3.org/TR/webauthn-3/#sctn-extensions); una extensión ignorada no constituye por sí sola un fallo de la ceremonia. Ahora PRF, hmacCreateSecret, uvm, largeBlob preferido y otras extensiones no implementadas se ignoran. No se inventan resultados de PRF/largeBlob ni se declaran como soportadas. Una exigencia explícita de largeBlob, política de protección obligatoria o Secure Payment Confirmation continúa fallando.

Se admite el origen HTTPS con ruta raíz `/`, además de la forma sin barra final. No se admiten otras rutas, query, fragmento o credenciales en la URL. El hash delegado del navegador sigue usándose exactamente como llegó.

La validación Android produce códigos estables: `PK_CREATE_OPTIONS`, `PK_GET_OPTIONS`, `PK_DISCOVERY_OPTIONS`, `PK_BROWSER_IDENTITY`, `PK_BROWSER_HASH`, `PK_ORIGIN_INVALID`, `PK_NATIVE_HASH`, `PK_NATIVE_SIGNER`, `PK_DAL_NETWORK`, `PK_DAL_DENIED`, `PK_CALLER_MISSING`, `PK_REQUEST_MISSING` y `PK_SELECTION`. Los errores inesperados se muestran como `PK_UNKNOWN` y no se atribuyen al sitio. La UI traduce los códigos; el adaptador no incluye textos de presentación. No se vuelcan solicitudes JSON, desafíos, cuentas ni credenciales en logs o mensajes.

Se mantienen comprobaciones de identidad/firmas del navegador, delegación DAL para aplicaciones nativas, RP/allowCredentials, algoritmo ES256 y la autorización DNI/PIN antes de usar claves. No se agrega una excepción para Facebook ni se permite a una app suplantar un origen web.

Pruebas nuevas: registro con extensiones opcionales y salidas honestas, rechazo de políticas obligatorias, autenticación con extensiones ignoradas y aislamiento RP, credProps inválido, origen con barra raíz, códigos distintos sin datos de causas privadas y propagación de cancelación.

ADB no detectó dispositivos durante esta corrección. La causa exacta de la solicitud del teléfono sigue pendiente: el defecto de extensiones está comprobado con pruebas reproducibles, pero no se dispone de una captura que pruebe que fue el único motivo del rechazo informado. Probar 1.6.1 en Chrome directamente con Passkeys.io/Passkeys Playground; si falla, registrar el código PK mostrado para identificar la etapa precisa.

Resultado: 89 pruebas de los módulos afectados aprobadas (passkeys-core 24, passkeys-android 11, app 54), 12 comprobaciones del verificador WebAuthn independiente aprobadas, lint del proveedor sin errores (3 avisos previos) y pruebas instrumentadas compiladas, sin ejecutarlas. Un fallo del archivo DEX incremental en la carpeta sincronizada se resolvió regenerando los artefactos del módulo Android de passkeys; la compilación final terminó correctamente.

APK exportado y firma verificada: `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`, paquete `pe.gob.reniec.dnie.evolution.debug`, versionCode 25, `1.6.1-passkeys-validation`. SHA-256 `FE8BCAFDC91A55F317230A3FB2F949944D49ED1285144AF0D1BB60E7351D7C51`. No se instaló por ausencia de dispositivo ADB.

## Validación del flujo informada por el usuario

El 2026-10-05 el usuario confirmó que funciona el flujo y compartió los resultados del playground de webauthn.passwordless.id. La salida incluye registro ES256/attestation none aceptado por su verificador y autenticación discoverable con allowCredentials vacío, userVerification required y userVerified true. La credencial utilizada en autenticación coincide con la creada en registro. Los resultados indican synced false, transporte internal y contador cero, coherentes con esta implementación.

Se verificó además la firma de esa autenticación de forma independiente con node:crypto, usando la pública SPKI del registro y los bytes originales de authenticatorData + SHA-256(clientDataJSON): firma válida, hash RP correcto, flags 0x05 (UP/UV), 37 bytes de authenticatorData y contador 0. No se incorporan nombres, handles, identificadores ni vectores de la credencial real al repositorio.

El playground declara que ejecuta todo localmente, sin enviar a un servidor: queda validado el flujo real navegador/proveedor y la comprobación criptográfica local, no una sesión remota de Facebook ni un transporte QR/hybrid. La sección final con localhost, otro desafío y contador 1 corresponde a otros valores del panel de depuración; no se mezcla con la respuesta real verificada del sitio. Continúan pendientes compatibilidad específica con Facebook y el flujo entre dispositivos mediante QR.
