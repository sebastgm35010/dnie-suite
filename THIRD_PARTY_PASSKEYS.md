# Dependencias de passkeys

Código del proveedor/motor escrito en este fork, sin copiar implementaciones de KeePass, Keyguard ni código de muestra de almacenes inseguros.

| Componente | Versión/licencia | Uso |
|---|---|---|
| AndroidX Credentials | 1.3.0, Apache-2.0 | API oficial de proveedor, identidad del caller y respuestas del sistema. |
| Gson | 2.11.0, Apache-2.0 | Modelo JSON; parseo estricto y límites definidos por el fork. |
| Guava Android | 33.3.1-android, Apache-2.0 | Reglas Public Suffix List para RP IDs. |
| Tink Android | 1.23.0, Apache-2.0 | Dependencia existente de la bóveda; misma versión para evitar incompatibilidad protobuf. |
| SimpleWebAuthn server | 14.0.3, MIT | Solo herramienta de desarrollo para verificación independiente; no se incluye en el APK. Lock de dependencias en `tools/passkey-verifier`. |

Referencias primarias: [Android provider](https://developer.android.com/identity/sign-in/credential-provider), [WebAuthn](https://www.w3.org/TR/webauthn-3/), [SimpleWebAuthn documentación](https://simplewebauthn.dev/docs/packages/server), [licencia SimpleWebAuthn](https://github.com/MasterKale/SimpleWebAuthn/blob/master/LICENSE.md).

Lista de navegadores privilegiados: datos de paquete y certificados públicos de [Google](https://www.gstatic.com/gpm-passkeys-privileged-apps/apps.json), incorporados el 2026-10-05 en `dnie-passkeys-android/src/main/assets/privileged-passkey-callers.json`. SHA-256 `0891EE2507894999EAD3EB4F42E17936A2E8ED8F8FD95592535C9811EF319481`. La lista queda fijada en el APK: cambios de firmantes/navegadores requieren revisión y actualización, sin descargar listas de confianza dinámicas durante una firma.

Los textos Apache-2.0 para las nuevas bibliotecas se incluyen como asset del módulo. La licencia Tink ya está en los assets de `app`. Los avisos específicos de dependencias transitivas de desarrollo están en sus paquetes npm; no se distribuyen como parte de la aplicación.
# Componentes añadidos en 1.7

AndroidX Biometric 1.1.0 usa la licencia Apache 2.0 de AndroidX. Google Play Services Code Scanner 16.1.0 es un SDK de Google sujeto a sus condiciones, con un módulo de lectura descargable; no se presenta como implementación propia de caBLE. La integración sigue la [documentación de Google Code Scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner). No se incluyó código de transportes FIDO de repositorios de terceros.
