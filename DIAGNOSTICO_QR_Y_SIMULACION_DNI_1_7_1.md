# Diagnóstico QR y simulación de DNI — 1.7.1

## Registro recibido

Se analizaron los 466 eventos de `dnie-diagnostico4.jsonl`, sin copiar el registro personal al repositorio. El inicio de proceso de 1.7.0 corresponde al ID 1546: hay 204 eventos de esa versión y 262 de historial anterior. Las conclusiones de esta ejecución usan solo los 204 nuevos.

El ciclo de vida confirma dos aperturas de `GmsBarcodeScanningDelegateActivity`, seguidas de su regreso a configuración: la primera durante aproximadamente 14 segundos y la segunda durante 1,3 segundos. El lector sí se abrió. La versión 1.7.0 no registraba el callback/código del escáner y convertía cualquier fallo del Task en «No se pudo abrir el lector». Por eso el registro no permite demostrar si el usuario canceló, si falló el procesamiento o si había un problema del módulo. No demuestra una autenticación FIDO completa desde PC.

También hay una apertura del catálogo mediante DNI: selección PKI, lectura de certificado, verificación de PIN y `catalog_unlocked`, en aproximadamente 2,1 segundos. Falta el resultado final de habilitación biométrica en esa versión. No aparecen eventos de excepción en el segmento nuevo; eso no descarta errores que esa versión todavía no instrumentaba.

Se registraron 40 frames lentos: 28 en bóveda, 5 en certificados y el resto en otras rutas/arranque. Los máximos son 724 ms en arranque, 417 ms en certificados y 316 ms en bóveda. La carga local de seis elementos de la bóveda completó en 287 ms. Sin el número total de frames ni una medición equivalente anterior, esto no basta para afirmar una regresión de rendimiento.

## Corrección del QR

- Las cancelaciones del Task y los códigos ML Kit de cancelación son estados normales, sin aviso rojo de fallo de apertura. Google define explícitamente `CODE_SCANNER_CANCELLED` cuando se cierra el lector ([referencia del SDK](https://developers.google.com/android/reference/com/google/mlkit/common/MlKitException)).
- Módulo no disponible, permiso de cámara de Google Play Services, versión antigua, otro escaneo en curso y fallo de procesamiento reciben mensajes específicos. No se muestran mensajes arbitrarios del SDK.
- Se impide iniciar otro escaneo mientras haya uno pendiente. Resultados normales y errores tienen presentación independiente.
- Los diagnósticos incluyen solicitud, código de resultado, cancelación, rechazo del sobre y comienzo/fallo de entrega a Android. No registran QR, imagen, URI FIDO, hash ni claves de sesión.
- Se añadió **Usar cámara del teléfono** como alternativa. Abre la app de cámara, que puede ofrecer lector QR/Lens según el dispositivo. No depende de un enlace privado de Lens ni afirma que cualquier cámara soporte FIDO.
- Se registran además etapas/finalización del mantenimiento y confirmación/cancelación de habilitación biométrica, para que el siguiente log muestre qué llegó a completarse.

El flujo sigue entregando el QR al autenticador de Google Play Services. Escanearlo o abrir su pantalla no garantiza que ese autenticador ofrezca nuestras credenciales. El transporte híbrido propio sigue fuera de esta versión.

## Simulación del motor real

Las tarjetas y certificados sintéticos existen solo en `app/src/androidTest`: no se incluyen en el APK entregado. Se usa el mantenimiento real, repositorio AtomicFile real, catálogo real y archivos cifrados reales, bajo una carpeta única de caché de prueba. Los certificados de prueba son autofirmados RSA-2048, sin datos personales. La simulación cubre las operaciones de clave/PIN del adaptador; no emula la radio NFC, PACE ni el firmware de RENIEC.

| Prueba instrumentada | Resultado comprobado |
|---|---|
| Migración completa y recuperación anterior | DNI anterior caducado con clave accesible → nuevo certificado vigente; IDs/claves preservados; autenticación generada; el DNI anterior no abre el catálogo activo; recuperación anterior funciona. |
| Migraciones rechazadas | Nuevo certificado caducado, PIN incorrecto, cancelación y prueba privada inválida no cambian el catálogo activo. No hay reintentos automáticos de PIN. |
| Catálogo modificado durante la espera | La revisión antigua se rechaza y no sobrescribe las nuevas credenciales. |
| Exportación, preparación y restauración | Archivos cifrados reales, estado BS actualizado y respaldo alterado rechazado antes de cambiar el catálogo. |
| Fallo al guardar | Se simula una excepción antes del commit atómico: el archivo original sigue idéntico y legible; no queda una escritura temporal activa; existe copia anterior cifrada. |
| Credenciales antiguas | Pueden cambiar su DNI de protección en este teléfono; siguen con BE=0 y no se exportan por esa operación. |

Las seis pruebas pasaron en el emulador API 37 en 28,4 segundos. También pasaron 118 pruebas JVM (57 app, 47 núcleo de passkeys, 14 adaptador) y 36 comprobaciones independientes SimpleWebAuthn. La comprobación del DEX confirmó que las clases de simulación no están en el APK principal.

## Entorno y artefacto

Se usó `D:\G\Android_SDK\emulator\emulator.exe` por CLI, sin abrir la IDE, con dos CPU, 2048 MiB de RAM virtual, sin ventana/audio, imagen de solo lectura y sin guardar snapshots. Se detuvo Gradle al terminar la compilación para liberar RAM antes de ejecutar pruebas. Las instalaciones fueron exclusivamente en `emulator-5580`; no se conectó el teléfono del usuario. La bóveda de documentos no incorpora acceso biométrico.

APK: `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`, versión `1.7.1-passkeys-diagnostics`, code 31, 30.164.296 bytes. SHA-256: `1AC278B56119C4DDDD564E5F01251AFBEB94404D39CF29B4C9DB21D112D1CC34`.

Resultados instrumentados finales: seis pruebas del mantenimiento, una del repositorio cifrado y una de apertura de pantalla: **8 aprobadas**. System UI mostró un diálogo ANR del propio entorno que bloqueó inicialmente la prueba de pantalla; tras cerrarlo, la ejecución aislada pasó en 4,9 segundos. No se cambió la imagen original del AVD. Las pruebas físicas de huella y autenticación QR desde un navegador permanecen independientes de las simulaciones.
