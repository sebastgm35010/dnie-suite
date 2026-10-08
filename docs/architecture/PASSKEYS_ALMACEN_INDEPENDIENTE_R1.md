# Passkeys: configuración y almacén independiente · R1

2026-10-08. Decisión del usuario: «Almacén independiente autorizado inicialmente con DNI». Diseño aprobado como dirección; motor nuevo y migración aún no implementados. APK instalada: 69; no cambiar su política silenciosamente.

## Estado comprobado

Las claves ES256 de cada sitio ya son propias de la app. EncryptedPasskeyRepository cifra el catálogo mediante VaultContainer para la pública CIF del DNI. PasskeyMaintenance.export cifra para esa misma pública; migrar requiere abrir con la privada antigua y después autorizar la nueva. Por tanto no resuelve una renovación destructiva ya realizada.

PasskeyDeviceVault contiene una copia biométrica vinculada a la revisión del catálogo. Usa Keystore, CryptoObject por operación e invalidación por cambio de biometría. No es un almacén principal independiente ni un respaldo portable.

Regresión de acceso: ad6a34b quitó la llamada a PasskeySettingsActivity del panel de herramientas de VaultScreen. La actividad y el proveedor siguen declarados, pero la navegación normal ya no ofrece entrada. No se borraron credenciales mediante ese cambio de UI. Restauración del acceso pendiente, con entrada propia fuera de Bóveda.

## Configuración propuesta

Pantalla propia Passkeys, separada de archivos: estado del proveedor y activación, cuentas y gestión, protección de este teléfono, respaldo/recuperación y opciones avanzadas. QR del navegador permanece separado y descrito como delegación al sistema Android; no afirmar transporte híbrido propio.

El estado debe distinguir almacén antiguo ligado al DNI, almacén independiente, migración pendiente, bloqueo e indisponibilidad. No presentar «respaldo con el mismo DNI» como recuperación tras cambio de clave. No marcar respaldo preparado hasta escribir, abrir y verificar el paquete completo.

## Protección nueva

1. Inicialización: autorización explícita con DNI/PIN y comprobación criptográfica de acceso; crear un secreto aleatorio de almacén. Registrar versión y vínculo de autorización inicial, sin utilizar datos públicos del titular como secreto. Esto es autorización local; no constituye una atestación de identidad ante los sitios.
2. Catálogo: cifrado autenticado con claves derivadas del secreto independiente. La sustitución de pública del DNI no cambia claves ES256 ni credentialId de las passkeys. Formato nuevo, no reinterpretar DNIEVLT1.
3. Uso diario: abrir mediante una protección local autenticada de Keystore. Biometría fuerte por operación como opción existente; elegir soporte y política para credencial del dispositivo antes de implementarlo. No habilitar acceso sin verificación del usuario ni mezclarlo con la bóveda de archivos.
4. Recuperación: envoltura independiente bajo contraseña/secreto de recuperación, con KDF resistente y formato propio versionado. Separar pérdida del DNI de pérdida del teléfono/Keystore. La contraseña es una vía adicional real de acceso; exigir elección explícita antes de crear un respaldo.
5. Renovación: el DNI nuevo puede reautorizar el vínculo administrativo después de desbloquear el almacén por una vía existente. Acreditar la misma identidad no recupera un secreto perdido. La revocación del certificado no revoca automáticamente las credenciales WebAuthn de los sitios.

La modalidad precisa de desbloqueo diario y de recuperación sigue por definir. No cambiar el comportamiento de credenciales existentes hasta disponer de migración verificable y consentimiento explícito.

## Migración y compatibilidad

Abrir el catálogo antiguo mientras su privada siga disponible. Conservar claves, credentialId, rpId, userHandle, contadores y elegibilidad BE por credencial. Preparar destino cifrado, verificarlo y confirmar transaccionalmente antes de retirar origen. Cortes deben dejar una versión íntegra recuperable; no publicar dos catálogos divergentes.

BE=0 no se convierte retroactivamente en exportable ni se incluye en respaldo portable. Para obtener una credencial elegible diferente se requiere nuevo registro en el sitio. BS representa un respaldo realmente realizado; no confundir habilitar un botón con haber respaldado. Si la clave antigua ya no existe y no hay una vía válida, no prometer migración automática.

## Capas y ejecución

- dnie-passkeys-core: política versionada, catálogo, reglas BE/BS y contratos de protección independientes de Android.
- Motor de almacén aislado: cifrado, formato, validación, transición y recuperación; sin Compose, Activity ni NFC.
- dnie-passkeys-android: Keystore/autenticación, persistencia transaccional y CredentialProvider; proveedor consume casos de uso, no ViewModels.
- Adaptador DNI: autorización inicial y lectura del catálogo legado; no requisito criptográfico para cada inicio de sesión nuevo.
- app: navegación propia, estado claro y configuración; sin lógica criptográfica en pantallas.

Orden: restaurar acceso y corregir textos; especificar desbloqueo/recuperación; checkpoint; implementar motor y probar escenarios sintéticos; adapter y migración opt-in; pruebas de proveedor y sitios; APK. No introducir fallback automático al almacén legado, al DNI o a datos en claro.

Pruebas necesarias: nueva pareja RSA sin cambios de credenciales, pérdida de Keystore, cambio biométrico, secreto incorrecto, catálogo alterado, cortes antes/después de commit, catálogo actualizado durante operación, BE=0 excluido del respaldo y BS correcto. Separar resultados sintéticos de uso físico y aceptación por sitios.

## Referencias

Decisiones contrastadas con [WebAuthn Level 3](https://www.w3.org/TR/webauthn-3/) para BE/BS y [Credential providers Android](https://developer.android.com/identity/sign-in/credential-provider) para la separación del proveedor. Auditoría local: [renovación y claves](RECUPERACION_Y_RENOVACION_DNI.md). No se ha implementado sincronización ni recuperación de passkeys en el respaldo DNIEBKP2 de archivos.
