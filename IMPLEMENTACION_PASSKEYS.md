# Passkeys con DNIe — 1.6.0-preview

Implementación inicial en el fork de evolución. La versión 1.5.1 confirmada por el usuario se conserva en `build/checkpoints/1.5.1/dnie-evolution-debug.apk`, SHA-256 `D3CB59B9E82D2795AFF42D5BE1C6A0A44841E13DD933EEB62D2F6FDB641B17A6`. La base clausurada y Antigravity no se modificaron.

## Uso

1. Abrir **Bóveda → Configurar passkeys → Activar proveedor en Android**. El usuario habilita DNIe en los ajustes del sistema; no se modifica esa preferencia por ADB.
2. En un sitio o aplicación compatible, solicitar crear una passkey y seleccionar DNIe. Revisar sitio/cuenta, escribir CAN/PIN y acercar el DNI. El CAN se precarga desde la misma preferencia de la pantalla principal; el PIN no se guarda.
3. Para iniciar sesión, seleccionar **Desbloquear passkeys con DNIe**, autorizar por NFC, elegir la cuenta en el selector Android y autorizar nuevamente con DNI/PIN. Son dos lecturas deliberadas: descubrimiento privado y firma final, sin sesión desbloqueada entre actividades.
4. **Ver y gestionar con DNI** permite listar cuentas y eliminar una, con confirmación y otra autorización por DNI. No elimina el registro público del sitio: gestionar los métodos de acceso también en la cuenta del sitio.

El proveedor requiere [Android 14 o superior](https://developer.android.com/identity/sign-in/credential-provider). Se deshabilita por recurso en versiones anteriores; la bóveda y las otras funciones siguen disponibles. Los navegadores deben permitir proveedores de terceros y estar en la lista de confianza. La habilitación y el flujo del selector dependen también del fabricante/navegador.

## Separación del código

| Capa | Responsabilidad |
|---|---|
| `dnie-passkeys-core` | Validación JSON/base64url/RP, delegación DAL, catálogo serializado, P-256, CBOR/COSE, respuestas WebAuthn y firma ES256. JVM sin Android. |
| `dnie-passkeys-android` | CredentialProviderService, PendingIntents explícitos, identidad del solicitante, HTTPS/DAL, almacenamiento cifrado atómico y casos de uso con DNI. Sin Compose ni dependencia de `app`. |
| `app/ui/passkeys` | Pantallas, estado, ciclo de vida y adaptación del transporte NFC al caso de uso. El ViewModel no implementa cifrado ni firma. |
| `dnie-protocol` | Se reutiliza la secuencia IAS/PKI/PACE y CE83. Logging opcional deshabilitado solo para estas ceremonias. |

`PasskeyStore` permite probar autorización/cancelación/concurrencia sin almacenamiento Android. Un mutex común mantiene cada lectura-modificación-escritura serializada hasta cerrar la sesión; no se limita únicamente a bloquear el acceso al archivo.

## Claves y almacenamiento

- Cada credencial tiene clave P-256 independiente, identificador aleatorio de 32 bytes y handle de usuario proporcionado por el sitio. La clave de firma RENIEC no se usa para WebAuthn.
- `filesDir/passkeys/catalog.dnievault` contiene nombres, RP IDs, handles, claves privadas y públicas dentro de un contenedor autenticado. No existe índice público de cuentas. Es independiente de los objetos/backup/importación de la bóveda de archivos.
- Se reutiliza `VaultContainer`: secreto aleatorio envuelto RSA-2048/PKCS#1 v1.5 para la pública CE83, keyset cifrado con AEAD y contenido Streaming AEAD de Tink Android 1.23.0. El formato no añade un endpoint de descifrado externo ni cambia el protocolo de tarjeta.
- La creación inicial exige PIN correcto y prueba privada de recuperación de un secreto sintético. Con catálogo existente, exige PIN correcto y recuperación de la envoltura con el mismo DNI. Una clave pública/certificado leídos por NFC no bastan para autorizar.
- Una tarjeta distinta se rechaza antes de consumir un intento de PIN. No se reintenta automáticamente un PIN fallido. Siempre se cierra NFC y se limpian secretos/buffers privados disponibles al código.
- El PIN y las cuentas visibles se eliminan del estado al pasar a segundo plano. Las claves descifradas no sobreviven a la operación. La pantalla de autorización lleva FLAG_SECURE y su estado textual omite datos privados. La JVM/JCA no permite garantizar borrado perfecto de todas las copias internas.
- No se incorpora biometría, contraseña de recuperación, sincronización ni exportación de passkeys. Perder el teléfono, borrar los datos de la app o cambiar/perder la clave de este DNI puede hacerlas irrecuperables. Mantener otro método de acceso en los sitios.

## Identidad y compatibilidad WebAuthn

Se autentica el solicitante antes de pedir PIN o mostrar cuentas. Los PendingIntents son mutables por requisito del sistema, con componente privado explícito y URI única; se vuelve a validar RP, origen, algoritmo y allowCredentials en la solicitud final.

**Navegadores:** AndroidX verifica paquete y firmas contra la lista pública distribuida por Google. Se usa su `clientDataHash` de 32 bytes tal como llega; el proveedor devuelve clientDataJSON `{}` y el navegador lo sustituye por sus bytes originales. No se calcula un hash alternativo. El navegador verifica las relaciones entre origen/RP, incluidas Related Origin Requests; el proveedor valida su identidad privilegiada y el RP ID.

**Apps nativas:** origen `android:apk-key-hash:<SHA-256 de firma actual en base64url>` y delegación obligatoria `delegate_permission/common.get_login_creds` en `https://<rp>/.well-known/assetlinks.json`. Paquete y firma actual deben coincidir. HTTPS con tiempos/límites, sin redirecciones; ninguna clave del DNI, PIN, CAN o catálogo se envía por esa consulta. Sin delegación válida se rechaza. No se admite clientDataHash delegado por una app nativa ni firmantes múltiples ambiguos.

- ES256 (`alg=-7`), COSE EC2/P-256, ECDSA DER, attestation `none`, AAGUID cero, credencial discoverable y transporte `internal`/attachment `platform`.
- UP y UV se afirman solo después del consentimiento explícito, PIN correcto y operación privada de tarjeta. BE/BS = 0: almacén de un dispositivo sin backup de passkeys. Contador 0, permitido para autenticadores que no ofrecen contador.
- `credProps.rk=true` cuando se solicita correctamente. Desde 1.6.1, las extensiones opcionales no implementadas se ignoran sin devolver resultados inventados. Las solicitudes de largeBlob obligatorio, política credProtect obligatoria y Secure Payment Confirmation siguen rechazándose. No se declara FIDO certificado ni autenticador CTAP2 externo.
- Se respetan RP exacto, allowCredentials y excludeCredentials. Los desafíos se validan en formato/tamaño; el servidor sigue siendo responsable de frescura y prevención de replay.
- RP IDs ASCII/punycode canónicos bajo un sufijo público; sin IP, localhost o sufijo público aislado en este perfil. Máximo 1000 registros/1 MiB de catálogo; hasta 16 opciones de consulta y 50 entradas de selector por respuesta para acotar Binder. Gestión local muestra el catálogo completo.

## Validación y límites

Pruebas JVM de criptografía real, aislamiento por RP, exclusión/allowlist, parseo limitado, catálogo, delegación DAL y flujo de tarjeta con certificado/RSA sintéticos. Incluyen fallo PIN, otra tarjeta, falta de catálogo, cancelación, secreto incorrecto, limpieza y serialización de actualizaciones.

`tools/passkey-verifier` usa [SimpleWebAuthn server](https://simplewebauthn.dev/docs/packages/server), versión 14.0.3 MIT, como verificador independiente. Sus 12 comprobaciones aceptan registro/autenticación nativos y de navegador delegado, y rechazan RP/origen/desafío/firma alterados. Los vectores contienen respuestas públicas sintéticas, nunca privadas, y se generan en `dnie-passkeys-core/build`.

Comandos desde el fork:

```powershell
.\gradlew.bat :dnie-core:test :dnie-protocol:test :dnie-vault-core:test :dnie-icao:test :dnie-pdf:testDebugUnitTest :dnie-passkeys-core:test :dnie-passkeys-android:testDebugUnitTest :app:testDebugUnitTest :app:exportBranchApk :app:compileDebugAndroidTestKotlin --console=plain
```

Desde `tools/passkey-verifier`, `npm ci --ignore-scripts` y `npm test`. En Google Drive, npm puede fallar al escribir dependencias: copiar los dos archivos fuente y el lock a una carpeta local, usar `npm ci` allí y pasar a `node verify.mjs` la ruta absoluta del JSON de vectores.

Las pruebas instrumentadas incluyen roundtrip y alteración del catálogo real Android en una carpeta temporal aislada. Su compilación no prueba su ejecución. ADB no muestra dispositivos conectados durante esta implementación: siguen pendientes la ejecución instrumentada y la prueba completa navegador → selector Android → NFC con este APK. No se activó el proveedor ni se instaló esta versión en el teléfono.

### Resultado de compilación del 2026-10-05

261 pruebas locales aprobadas, sin fallos/errores/omisiones: core 49, protocolo 62, bóveda 24, ICAO 27, PDF 20, passkeys-core 19, passkeys-android 8 y app 52. Además, 12 comprobaciones del verificador WebAuthn independiente y compilación de pruebas instrumentadas completadas.

Lint del módulo del proveedor: 0 errores y 3 avisos. Los avisos corresponden a soporte cliente Play Services en Android anteriores a 14 (no aplicable a este proveedor), existencia de una versión Credentials más nueva (se fija 1.3.0 para conservar compileSdk 34) y un TrustManager interno en BouncyCastle existente. Las consultas DAL usan HttpsURLConnection con validación TLS/hostname del sistema, sin instalar ese TrustManager ni aceptar certificados arbitrarios.

APK: `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`, paquete `pe.gob.reniec.dnie.evolution.debug`, versionCode 24 / `1.6.0-passkeys-preview`. Firma APK verificada con apksigner y la misma identidad de desarrollo del fork. SHA-256 `0A522912D03633034FB37D61D1FEFB9514795A876A8A32B6166A4E31CD8E255F`.
