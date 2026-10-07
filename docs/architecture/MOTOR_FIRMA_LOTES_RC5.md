# Primer incremento del motor compartido de firma

Fecha: 2026-10-07. Base previa: `248c347`, tag `checkpoint/pre-signing-suite-2026-10-06`.

## Respaldo antes de modificar el motor

En `D:/DNIeToolchain/Checkpoints/pre-signing-suite-2026-10-06` se guardaron `source.zip` (fuentes versionadas), `repository.bundle` (historia y referencias Git completas), APK Debug 52 y un README con SHA-256. `git bundle verify` confirmó la integridad del bundle. El respaldo no contiene caches, archivos personales ni keystores no versionados.

Para revisar/restaurar fuentes se puede extraer el ZIP en otra carpeta o clonar el bundle; el tag conserva el punto exacto previo al cambio. No sobrescribir el checkout activo ni desinstalar la app con datos para probar una regresión. El APK 52 es el artefacto previo guardado; bajar el versionCode de una instalación puede requerir un procedimiento de restauración específico de Android.

## Problema reportado y límite de la evidencia

El usuario confirma que seleccionó varios archivos juntos y solo el primero se firmó. El coordinador anterior ya seleccionaba PKI y verificaba el PIN una sola vez por lote, pero los dobles de prueba aceptaban firmas ilimitadas tras esa verificación. Por ello las pruebas no cubrían una tarjeta que consume la autorización tras una operación privada.

No se recibió un APDU/log nuevo que confirme que ese fue el fallo físico exacto. Este incremento cubre esa hipótesis y añade eventos para identificar la fase si el problema persiste. No declarar resuelta la prueba real por aprobar una simulación.

## Implementación

`dnie-signing-core` es una biblioteca Kotlin/JVM con Java 17. Aloja opciones y contratos PDF, documentos/resultados, importación neutral, nombres, exportación ZIP y `BatchSigner`. No importa Compose, Android ni PDFBox. Conserva paquetes Kotlin para que los consumidores no necesiten cambiar sus imports.

`dnie-pdf` conserva la implementación PDFBox Android y depende de los contratos del nuevo módulo. `app` conserva SAF, ViewModel, estado de pantalla y UI. `SigningWorkspace` permanece en la aplicación. La suite de escritorio podrá implementar el mismo contrato PDF y usar su adaptador de tarjeta.

`BatchCardSession` limita la autorización al lote y a una única conexión:

1. Selecciona PKI, verifica el PIN y lee el certificado una vez al abrir.
2. La primera operación privada utiliza esa autorización.
3. Antes de las siguientes operaciones vuelve a enviar VERIFY con el PIN ya introducido, dentro de la misma conexión/PACE. No hay otro diálogo, selección de applet o lectura del certificado por archivo.
4. Cualquier rechazo de PIN o fallo de tarjeta detiene las operaciones restantes. No reintenta el comando fallido ni abre una conexión nueva automáticamente.
5. Al cerrar descarta su copia mutable de las credenciales y cierra la tarjeta. Esto no constituye borrado garantizado de todas las copias String de la JVM.

La política renueva autorización de forma preventiva después de cada firma. Añade un VERIFY por firma posterior incluso si otra tarjeta conserva su autorización, a cambio de no depender de esa persistencia ni intentar PSO rechazados para descubrirla.

Se conserva el protocolo físico y la secuencia PACE validada. CMS continúa firmando SHA-256 de los atributos DER, mientras `messageDigest` contiene el hash del documento/ByteRange. Solo se corrigió un comentario impreciso acerca de DigestInfo; no se cambió el APDU V3.

Cada resultado se escribe en un temporal de su carpeta y se publica por renombrado después de terminar. Se eliminan temporales en falla/cancelación y se evita sobrescribir un resultado previo. Las lecturas para CMS/PDF comprueban cancelación del contexto del lote.

## Diagnóstico y siguiente prueba física

Eventos estructurados `signer`: `batch_start`, `batch_session_ready`, `batch_authorize`, `batch_card_signature`, `batch_end` y fallas por fase. Los campos contienen contadores y tipos/frames de excepción, sin PIN, CAN, documento, hash o datos del certificado. El registro APDU existente complementa los eventos con estado y duración.

Prueba requerida en Android rc5: añadir al menos tres archivos a Lotes antes de autorizar; introducir credenciales una vez y mantener el DNI hasta terminar. Incluir dos PDF y un archivo binario si es posible. Comprobar resultados individuales/ZIP, integridad de los originales y firmas. Exportar el diagnóstico si queda cualquier pendiente. Una pérdida NFC debe detener el lote y conservar los resultados completos; requiere nueva autorización para continuar los restantes.

## Fuera de este incremento

La suite Windows y su adaptador PDF todavía no se implementaron. El overload legado de firma por streams en el ViewModel y sus pruebas permanecen como deuda separada; la ruta productiva por Context usa el motor compartido. El lector, servicio Windows, transportes y controlador no se cambiaron.

## Validación y artefacto

Gradle offline completó pruebas de core, protocolo, motor compartido, PDF Android y aplicación; APK Debug 53 / `2.0.0-rc5-signing` compilado. Los reportes suman 232 pruebas, sin fallos, errores ni omisiones: core 49, protocolo 86, signing-core 10, PDF 20 y app 67. Algunas tareas reutilizan resultados actualizados. Las pruebas del motor nuevo y de la aplicación se ejecutaron en este incremento.

Además de la sesión con autorización consumida, el lote mixto genera y verifica criptográficamente cuatro resultados (dos PDF y dos CMS separados). Hay cobertura de rechazo de PIN entre archivos, detención sin reintento, pérdida de conexión, temporales incompletos, protección de resultados previos y cancelación de lectura. Las pruebas puras de importación se trasladaron al módulo JVM.

Logs fuera de Git: `D:/DNIeToolchain/Temp/signing-core-rc5-build.log` y `signing-core-rc5-regression.log`. APK candidato: `D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc5-signing-debug.apk`. No se instaló durante este incremento ni se manipuló una sesión física activa.
