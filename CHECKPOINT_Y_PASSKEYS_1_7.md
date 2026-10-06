# Checkpoint 1.6.5 y herramientas de passkeys 1.7.0

## Checkpoint privado

Antes de modificar las fuentes se guardaron `build/checkpoints/1.6.5-validated/source.zip`, el APK validado y `manifest.json`. El ZIP refleja el árbol de trabajo, incluidos archivos todavía no versionados; no depende de que exista un commit con todos los cambios. Se excluyen claves de firma, logs personales y configuración local. El checkpoint conserva código y APK, no copia los datos personales de la aplicación.

- APK 1.6.5 SHA-256: `578C9119B0163802AEDFB8A5456DCA68F86A7A94A34638F908316F6DC4B37E1E`.
- Fuentes SHA-256: `ac4fdb49d4f67fc0a352ff1000782c07bfe182dcec68f417952434b25ae5d337`.
- La base 1.0.8 y la copia de Antigravity permanecen intactas.

## Respaldo y certificados

Herramientas accesibles desde **Passkeys → Respaldo, migración y biometría**. El respaldo se cifra para la clave pública del certificado de cifrado del DNI; no contiene PIN ni CAN. La exportación exige autorizar una vez con DNI para seleccionar las credenciales elegibles y actualizar su estado de respaldo. La restauración prepara un archivo cifrado de tamaño limitado, verifica su autenticidad y decodifica el catálogo antes de sustituirlo. Reemplaza el catálogo; no combina cuentas automáticamente.

La vigencia del documento y la del certificado son independientes. El destinatario del catálogo se identifica por SHA-256 de la clave pública, no por la fecha impresa del DNI ni por los bytes completos del certificado. Una renovación que conserve esa clave puede abrir el catálogo. Un certificado caducado puede recuperar datos históricos si su clave privada sigue accesible; crear credenciales, habilitar un teléfono o vincular un nuevo DNI requiere certificado vigente. La operación realiza una sola verificación de PIN y demuestra posesión de la clave privada.

**Límite de las credenciales anteriores:** las versiones anteriores registraron BE=0. WebAuthn establece que la elegibilidad de respaldo es permanente y también considera respaldo la importación/exportación manual ([WebAuthn, estado de respaldo](https://www.w3.org/TR/webauthn-3/#sctn-credential-backup)). Por ello, las credenciales anteriores siguen funcionando, pero se excluyen del respaldo portable. Para incluir esas cuentas hay que registrar una passkey nueva en el sitio, con esta versión. La exportación informa cuántas fueron excluidas; si ninguna es elegible, no exporta claves.

Las nuevas credenciales se crean con BE=1, BS=0; exportar/restaurar establece BS=1. BE nunca cambia para una credencial existente. El catálogo v2 guarda estos indicadores y lee el formato v1 como BE=BS=0. La continuación de selección conserva ambos indicadores. Las firmas nuevas y antiguas mantienen ES256 y contador cero. No se ofrece sincronización automática ni un formato interoperable de exportación entre proveedores.

## Migración y recuperación local

La migración abre el catálogo con el DNI anterior y retiene una copia exclusivamente en RAM, durante un máximo de dos minutos. Después requiere CAN/PIN y prueba privada del nuevo DNI. Conserva los IDs y claves EC registrados en cada sitio, y vuelve a cifrarlos para la nueva clave de protección. Puede cambiar la protección de las credenciales antiguas dentro de este mismo teléfono sin alterar su BE.

Si el catálogo cambia durante la espera, la revisión deja de coincidir y la migración se rechaza. Restaurar/migrar conserva previamente un catálogo cifrado en almacenamiento privado. **Recuperar catálogo anterior en este teléfono** permite revertir con el DNI que lo protegía. Esta copia no se exporta como mecanismo para trasladar credenciales BE=0. La autorización temporal se elimina al cancelar, caducar o salir de la pantalla. Nunca se escribe el catálogo descifrado a un archivo temporal.

Un respaldo para el DNI anterior sigue necesitando su clave privada. No resuelve la pérdida del DNI, un PIN bloqueado ni la retirada del acceso a la clave del chip. Conviene migrar antes de perder ese acceso. No se cambian automáticamente certificados de otros DNI. El formato v2 no lo entiende el APK antiguo: no bajar de versión sobre estos datos sin preparar su recuperación.

## Opción biométrica del teléfono

Desactivada inicialmente y habilitable solo de forma explícita. DNI, PIN y certificado vigente permiten habilitar este teléfono; una confirmación con biometría fuerte cifra una copia local del catálogo mediante AES-256-GCM y una clave no exportable de Android Keystore. Cada inicio de sesión requiere una nueva autorización biométrica con CryptoObject; no basta recibir un callback de éxito sin la operación criptográfica.

La copia vive en `noBackupFilesDir`, está ligada a la revisión del catálogo y la clave se invalida al cambiar la inscripción biométrica. Crear/eliminar credenciales, exportar, restaurar o migrar el catálogo invalida la delegación. Hay que habilitarla de nuevo. Se puede desactivar aunque la biometría ya no esté disponible. Esta vía solo permite descubrir cuentas y autenticar: crear, administrar, respaldar y migrar siguen requiriendo DNI. Mantiene la validación del sitio/app y la autorización de selección de una sola utilización.

**La bóveda de archivos continúa requiriendo DNI.** No se habilita descifrado biométrico de documentos.

## QR FIDO experimental

**Escanear QR FIDO del navegador** usa Google Code Scanner, sin permiso de cámara de la app. Solo acepta un sobre `FIDO:/` numérico de tamaño limitado; el contenido no se registra ni se persiste. Lo entrega explícitamente al paquete de Google Play Services, después de comprobar su disponibilidad, y nunca a búsquedas o navegadores genéricos. El receptor decide la validez del mensaje FIDO y realiza el transporte híbrido.

Se comprobó que el emulador ofrece `com.google.android.gms/.fido.authenticator.ui.QRBounceActivity`. Eso confirma un receptor para la ruta, no una autenticación exitosa ni que Android ofrezca este proveedor. El escáner puede necesitar descargar su módulo. Esta versión **no implementa un transporte híbrido caBLE/CTAP propio** ni afirma compatibilidad universal con las passkeys de DNIe desde una PC. Si Android ofrece solo su gestor de Google, ese transporte propio permanece pendiente.

## Separación del código y entorno

- Núcleo JVM: formato v2, indicadores WebAuthn, política de respaldo, validación del sobre QR y firmas.
- Adaptadores Android: repositorio atómico cifrado, mantenimiento SAF/NFC, copia biométrica/Keystore y lanzamiento del lector QR.
- ViewModels: coordinación, cancelación y caducidad; pantallas Compose: controles, estado y confirmaciones de plataforma.
- Android Studio encontrado en `D:\G\ANDROID`; SDK nuevo en `D:\G\Android_SDK` con plataforma 37. Se mantiene el SDK 34 existente en `C:\Users\LENOVO\AppData\Local\Android\Sdk` para compilar. El emulador 37 se ejecuta sin ventana y sin guardar su imagen.

## Validación

Pruebas JVM: recuperación con certificado caducado, rechazo de nueva protección caducada antes de gastar PIN, cambio de envoltura RSA con preservación de IDs/claves y verificación independiente de la firma, rechazo de respaldo alterado, compatibilidad v1/v2, exclusión de BE=0 e invariantes BE/BS. El verificador SimpleWebAuthn comprueba casos nativos y de navegador, credenciales antiguas, nuevas y respaldadas, además de RP/origen/challenge incorrectos y firma alterada.

Resultados finales: 115 pruebas JVM de módulos afectados (47 del núcleo de passkeys, 14 del adaptador y 54 de la app), 36 comprobaciones independientes SimpleWebAuthn y 2 pruebas instrumentadas aprobadas en el emulador 37 (repositorio cifrado/alteración y apertura de mantenimiento). La primera ejecución del emulador sufrió un fallo de arranque bajo presión de memoria y un diálogo ANR de System UI. La prueba de interfaz con Espresso 3.6 no era compatible con el método interno InputManager eliminado en API 37; se sustituyó por automatización pública y se recorrió explícitamente el árbol de accesibilidad de Compose. Las ejecuciones aisladas finales pasaron.

APK compilado y firma verificada: `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`, paquete `pe.gob.reniec.dnie.evolution.debug`, versionCode 30, versionName `1.7.0-passkeys-tools`, 30.385.870 bytes. SHA-256: `13999DF40A639573AC16977DFA3D15A6C1775F65FB5922C3DD0100D57FF6E305`. Su certificado de firma coincide con el checkpoint 1.6.5. Se verificaron los 377 archivos del ZIP del checkpoint y su CRC, sin keystores ni logs personales.

Una ejecución real con DNI, huella y QR del navegador sigue requiriendo validación física; compilar o resolver el receptor QR no sustituye esa prueba. No se conectó ni se instaló nada en el teléfono del usuario: las instalaciones ADB de esta sesión fueron exclusivamente en `emulator-5580`, con imagen de solo lectura y sin guardar snapshot.
