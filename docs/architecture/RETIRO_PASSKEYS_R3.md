# Retiro de passkeys y limpieza de datos · R3

2026-10-08. Usuario cancela la función y autoriza eliminar su implementación y datos internos. Checkpoint previo: `checkpoint/pre-remove-passkeys-apk70`, sobre `5ae338b`. APK71 / `2.0.0-rc23-core-only` conserva la identidad y firma de actualización.

## Retiro

Eliminados módulos dnie-passkeys-core/android, CredentialProviderService, actividades, pantallas, adaptadores FIDO, pruebas y verificador Node dedicado. Eliminadas declaraciones Gradle y bibliotecas androidx.credentials y androidx.biometric, además de assets de callers privilegiados y documentación exclusiva. Las versiones anteriores permanecen únicamente en Git/checkpoint; no hay módulo opcional ni función escondida en la APK nueva.

El escáner QR de Google se conserva porque lo usa BridgeQrScanner. Se mantiene mediante constraint la versión transitoria de Fragment 1.2.5 ya probada, requerida por ese escáner; no se conserva la biblioteca biométrica. La prueba de privacidad del logger APDU se renombra QuietApduLoggerTest porque protege todos los flujos NFC. Diagnósticos dejan de mostrar la categoría retirada.

Se declara app → dnie-diagnostics-core explícitamente: antes app recibía esa API de forma indirecta por passkeys-core. Evita que retirar una función rompa los registros de firmas, bóveda o puente.

## Limpieza al actualizar

PackageUpgradeReceiver recibe MY_PACKAGE_REPLACED con exported=false y goAsync. RemovedFeatureMigration ejecuta la limpieza en Dispatchers.IO, sin bloquear la UI, y también la programa desde DnieApplication como reintento ante interrupciones o restauraciones de archivos. Una marca persistente solo se escribe tras eliminación completa de archivos y confirmación de ausencia de las dos claves. Después se evita volver a cargar Keystore en cada arranque si los archivos siguen ausentes.

RetiredCredentialData borra únicamente filesDir/passkeys (catálogos, transiciones, independiente y residuos AtomicFile), noBackupFilesDir/passkeys-biometric.bin y sus .bak/.new. Borra exactamente dnie-passkeys-biometric-v1 y dnie-passkeys-independent-v1 del Keystore privado de la app. No sigue enlaces simbólicos ni elimina otros directorios, certificados del DNI, datos de bóveda, preferencias CAN, identidades de PC o archivos del usuario.

No se borran respaldos exportados a Descargas/Drive ni registros/remotas credenciales de los sitios: quedan fuera del almacenamiento interno y no existe autorización de escritura en esos servicios. No se usa pm clear ni se desinstala toda la app. allowBackup sigue false.

Los únicos identificadores restantes en código de producto son rutas y aliases necesarios para la migración de borrado; no hay implementación de autenticación WebAuthn.

Referencias de plataforma: [recepción de MY_PACKAGE_REPLACED](https://developer.android.com/about/versions/oreo/background), [goAsync y ciclo de vida del receptor](https://developer.android.com/develop/background-work/background-tasks/broadcasts).

## Validación

Pruebas JVM: app 77 (una omitida por falta de permisos para crear enlaces simbólicos en Windows), protocol 86 y diagnostics-core 3. Total 166 casos: 165 pasan, una omitida, cero fallos/errores. Pruebas nuevas comprueban eliminación por alcance, preservación de bóveda/emparejamiento, idempotencia y reintento. Se añade prueba equivalente del enlace en carpeta aislada Android, más comprobación del proveedor, aliases y archivos tras actualizar. No escribir claves ni credenciales reales como fixtures.

Architecture boundaries OK. Compilación principal/auxiliar BUILD SUCCESSFUL; manifiesto fusionado sin proveedor ni actividades retiradas. El grafo de dependencias se revisa para excluir los módulos retirados y bibliotecas credentials/biometric. Inspección binaria: DEX sin clases de los módulos retirados, androidx.credentials ni androidx.biometric; assets sin passkeys.

APK71 instalada por ADB (sebas-poco:38667). Antes de actualizar existía files/passkeys/catalog.dnievault. Inmediatamente después de instalar aún existía y no estaba la marca: no se observó ejecución del receptor de actualización en esta prueba. Al abrir MainActivity, la migración automática completó el borrado; se verificó credentials_retired_71=true y ausencia del directorio. La garantía comprobada en este dispositivo es limpieza al primer arranque tras actualizar, sin intervención del usuario para borrar datos.

Las 5 pruebas Android pasan en 8,51 s: ausencia de proveedor, archivos y ambos aliases de Keystore; enlace simbólico aislado sin afectar su destino; exportación/importación con selector y autorización explícita; navegación/selección de bóveda. Las pruebas de interfaz usan fixtures y no sustituyen una operación NFC real.

Artefacto: D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc23-core-only-debug.apk. SHA256: 828010436E81652B772D62F62A0FBA99BF6E23E8424D52651D957B0A4ECE1B16.
