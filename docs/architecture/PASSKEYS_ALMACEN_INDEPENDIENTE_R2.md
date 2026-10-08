# Passkeys independientes · despliegue inicial R2

2026-10-08. Checkpoint previo: `checkpoint/pre-passkeys-independent-r2`, commit `2540435`. APK70 / `2.0.0-rc22-passkeys-local`. Primer despliegue opt-in; recuperación portable no implementada.

## Comportamiento

Se restablece entrada propia Passkeys mediante icono de llave en Mi DNIe; no depende de Bóveda. Protección y administración permite activar el almacén independiente con el DNI que abre el catálogo actual y, después, una huella fuerte mediante CryptoObject. Si no existe catálogo, la autorización comprueba la privada CIF del DNI y puede crear un almacén vacío. No se activa automáticamente al actualizar el APK.

Las claves ES256 y los identificadores por sitio se mantienen al migrar. Un secreto aleatorio de 256 bits protege el catálogo con AES-256-GCM; ese secreto se envuelve con una clave distinta de Android Keystore, que exige autenticación biométrica por operación. El envoltorio y catálogo se escriben juntos mediante AtomicFile. El catálogo se verifica antes del commit. La revisión del origen se compara antes de activar; las actualizaciones posteriores comparan la revisión de destino bajo serialización de operaciones.

Con el almacén activado, crear, usar, descubrir y eliminar passkeys no requiere DNI. Cada operación tiene su propia autorización biométrica; la selección de una cuenta conserva la continuación de uso único existente. La activación cancela continuaciones antiguas. El proveedor reconoce también el almacén independiente vacío y no necesita un catálogo legado para descubrirlo.

La existencia del almacén independiente es la decisión persistente de modo. Si se altera el archivo, se pierde la clave de Keystore o cambia la biometría, se detiene el acceso. No hay fallback al catálogo DNI anterior. El origen se conserva cifrado sin convertirlo en copia sincronizada; las passkeys creadas después de activar no están en él.

## Límites visibles

Esta etapa requiere biometría fuerte y Android 14 para usar el proveedor. No ofrece aún desbloqueo por PIN del teléfono, respaldo portable independiente, contraseña de recuperación, sincronización ni reenlace administrativo a un nuevo DNI. Perder el dispositivo, borrar sus datos o invalidar la clave biométrica puede dejar inaccesible el almacén. La interfaz informa esa condición antes de activarlo. Herramientas de respaldo/migración del catálogo antiguo se ocultan cuando el almacén independiente está activo.

No cambia BE/BS de ninguna credencial existente. El cambio de protección local no se considera exportación ni convierte BE=0 en exportable. No cambia la bóveda de archivos, su respaldo DNIEBKP2, el motor de firmas PDF/CMS ni el puente.

## Archivos por capa

- `dnie-passkeys-core/.../PasskeyLocalCipher.kt`: formato de payload cifrado, dominio de AAD y límites. Tests de claves, integridad, nonce, compatibilidad y continuidad de firma con clave EC real sintética.
- `dnie-passkeys-android/.../IndependentPasskeyVault.kt`: Keystore, envoltura, AtomicFile, revisión y sesión que borra secretos al cerrar.
- `PasskeyOperations.kt`: operaciones de proveedor sobre sesión independiente; protege el motor legado frente al modo nuevo.
- `DnieCredentialProviderService.kt`: disponibilidad y presentación de entradas para ambos modos.
- `PasskeyMaintenance.kt`: autorización inicial vacía y lectura de catálogo legado.
- `app/.../PasskeyToolsViewModel.kt`: coordina DNI → biometría → activación; expira y borra el snapshot en RAM.
- `PasskeyCeremonyViewModel.kt` y pantallas: presentan modo, solicitan autenticación y ocultan CAN/PIN en modo independiente.
- `MainActivity.kt`, `NavGraph.kt`, `CertificatesScreen.kt`: acceso propio con bloqueo si otra operación NFC sigue trabajando.

## Verificación y pendientes

Compilación principal y auxiliar: BUILD SUCCESSFUL. Resultados JVM: passkeys-core 52, passkeys-android 14 y app 79; total 145, cero fallos/errores. Incluyen cinco pruebas nuevas del cifrado local. Dos pruebas instrumentadas de presentación pasan en el teléfono: OK (2 tests), 3,011 s; verifican ausencia de CAN/PIN en modo independiente y autorización nueva al eliminar, además del formulario legado conservado. Usan fixtures sin modificar credenciales del usuario ni invocar biometría/NFC.

APK principal y auxiliar instaladas por ADB con Success; versionCode 70 y versionName 2.0.0-rc22-passkeys-local comprobados. Apertura real de Mi DNIe → icono Passkeys verificada con UI Automator: se muestra Mis passkeys y Protección y administración. No se ha activado el almacén independiente del usuario ni autorizado ninguna operación de tarjeta automáticamente.

Artefacto: `D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc22-passkeys-local-debug.apk`. SHA256 `FE1257243AF563A45B8ADC5D5933448A7BF9A020D33B588CB5565BFF1B6898BE`.

Las pruebas del motor usan claves sintéticas. No afirmar prueba física de migración, Keystore autenticado o aceptación de sitios hasta ejecutarlas con el usuario. No realizar renovación destructiva real para comprobar independencia.

Próxima etapa: especificar y autorizar recuperación portable independiente, respetar elegibilidad por credencial, probar migración/activación y fallos de commit con fixtures aislados, y comprobar crear/entrar/eliminar en un sitio de pruebas. Hasta entonces conservar otro método de acceso en los sitios.

Referencias técnicas: [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [biometría con CryptoObject](https://developer.android.com/identity/sign-in/biometric-auth), [WebAuthn BE/BS](https://www.w3.org/TR/webauthn-3/). Diseño previo: [R1](PASSKEYS_ALMACEN_INDEPENDIENTE_R1.md).
