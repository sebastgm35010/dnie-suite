# Diagnóstico persistente · 1.6.2

El registro temporal responde al rechazo `PK_CREATE_OPTIONS` observado al registrar una passkey en Facebook. Esta versión instrumenta el rechazo; no afirma haber corregido Facebook ni cambia los requisitos de autorización, algoritmos o protección de credenciales.

## Uso

Abrir **Passkeys → Diagnóstico persistente**, o el botón con icono de terminal en **Consola APDU**. La captura comienza al iniciar el proceso de la app y continúa aunque se cierre la pantalla de diagnóstico. Repetir el registro en Facebook, volver a esta pantalla y elegir **Exportar todo**. El selector Android permite guardar un archivo `dnie-diagnostico.jsonl`.

El filtro `passkeys` muestra la forma de la solicitud, la regla rechazada y el código de validación. Por ejemplo, `ES256_NOT_OFFERED`, `ATTACHMENT_UNSUPPORTED`, `LARGE_BLOB_REQUIRED`, `CRED_PROTECT_REQUIRED`, `FIELD_displayName_LENGTH` o `BASE64URL_FORMAT`. Son ejemplos de reglas posibles, no un diagnóstico confirmado de Facebook. Los errores que todavía usan validaciones genéricas incluyen clases y ubicaciones de código para localizar la condición.

## Organización

- `dnie-diagnostics-core`: contrato JVM de eventos, selección de campos y errores sin mensajes privados.
- `app/data/diagnostics/DiagnosticStore`: SQLite privada, escrituras asíncronas, paginación y exportación por streaming.
- `app/diagnostics`: inicialización, ciclo de vida, observación de estados y captura de excepciones no controladas.
- `app/ui/diagnostics`: Activity privada, ViewModel y presentación Compose. No ejecuta criptografía ni controla sesiones NFC.
- Los módulos de protocolo y passkeys emiten eventos mediante el contrato JVM y no dependen de la UI ni de SQLite.

## Cobertura y privacidad

Se registran navegación, ciclo de vida, estados de certificados, firma PDF/lotes, identidad ICAO y bóveda; consultas y etapas del proveedor de passkeys, validación del solicitante y operaciones NFC; cabeceras de cuatro bytes APDU, estados de respuesta y tiempos. Las solicitudes WebAuthn se resumen con algoritmos, opciones conocidas, nombres de extensiones conocidas y tamaños de campos. El RP y paquete solicitante son metadatos visibles de actividad.

No se almacena el JSON completo de WebAuthn, desafíos, identificadores o nombres de cuentas, PIN, CAN, claves, cuerpos APDU, mensajes arbitrarios de excepciones, documentos, nombres de archivos, MRZ ni imágenes. No se captura logcat indiscriminadamente. La consola APDU existente mantiene su propia función y no se vuelca en este historial.

El diagnóstico de bóveda conserva tiempos de apertura PKI, lectura CE83, verificación de PIN, recuperación de clave y restauración/local IO, sin sus contenidos. El filtro `performance` muestra fotogramas de la ventana principal que tardaron al menos 48 ms. Los errores controlados de lectura/exportación de certificados, identidad y firma también conservan clases y ubicaciones de código.

## Persistencia y rendimiento

La base `temporary-diagnostics.db` reside en el almacenamiento privado de la app. No tiene caducidad ni rotación: **Borrar**, con confirmación, elimina explícitamente el historial y libera espacio. La captura continúa después del borrado. Desinstalar la app o borrar sus datos desde Android también elimina la base.

Un escritor dedicado procesa una cola de hasta 8192 eventos. La pantalla consulta páginas de 300 eventos fuera del hilo principal; exportar transmite el historial hasta un punto fijo, sin cargarlo entero en memoria. SQLite usa WAL. Si se satura la cola, se informa y se registra la cantidad de eventos descartados cuando el escritor vuelve a avanzar. Los errores de almacenamiento se muestran sin interrumpir los flujos funcionales.

Los eventos confirmados en SQLite sobreviven al cierre del proceso. Un cierre abrupto puede perder los últimos eventos pendientes en memoria; el manejador de crash intenta vaciar la cola durante 1,5 segundos y conserva el manejador Android original. El registro no promete capturar fallos nativos, ANR ni eventos ocurridos antes de inicializar la aplicación. El historial crece hasta que se borra expresamente.

## Comprobaciones

Se añaden pruebas JVM de exclusión de campos privados, errores sin mensajes sensibles, límites de los eventos, forma de solicitudes WebAuthn, regla de rechazo y APDU exclusivamente con metadatos aun con la consola desactivada. Una prueba instrumentada usa una base separada para persistencia al reabrir, paginación, exportación y borrado de eventos pendientes; compilarla no significa ejecutarla en el dispositivo.

La suite de nueve módulos conserva 277 pruebas sin fallos ni errores: diagnósticos 3, núcleo 49, protocolo 63, bóveda 24, ICAO 27, PDF 20, passkeys JVM 26, adaptador passkeys Android 11 y app 54.

También pasaron 12 comprobaciones WebAuthn independientes con SimpleWebAuthn. APK compilado y firma verificada; actualización instalada por ADB, versión 26 / `1.6.2-persistent-diagnostics`, arranque `Status: ok` y creación de la base privada con WAL comprobados. La prueba instrumentada específica de SQLite quedó compilada, sin ejecutarse. La reproducción de Facebook y la exportación desde la pantalla quedan para la siguiente prueba del usuario.

SHA-256 del APK: `C290C56CA3B7EB24C616107A196C3AF00F4E0F4689939149E8155FF4E5DE7EB7`.
