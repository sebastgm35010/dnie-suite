# Diagnóstico ADB: bóveda y fluidez — 2026-10-05

## Cierre identificado y corregido

Dispositivo conectado por ADB: POCO 24069PC21G. Paquete diagnosticado: pe.gob.reniec.dnie.evolution.debug. Versión inicial 1.4.0-vault-preview (16).

El buffer crash contenía dos cierres del paquete, a las 23:57:46 y 23:58:52 del 4 de octubre. Excepción NoSuchMethodError en VaultContainer.validateKeyset: el código esperaba KeyData.getValue() devolviendo com.google.protobuf.ByteString. Tink para Android reubica sus clases Protobuf, por lo que su firma binaria difiere de la biblioteca Java usada al compilar el módulo JVM.

Se corrigió dnie-vault-core para compilar y probar contra tink-android 1.23.0, exactamente el runtime del APK. Se conservó el formato y las claves de los contenedores; no se borraron datos de la app. Se añadió tratamiento explícito de LinkageError en el flujo NFC para informar de incompatibilidades sin ese cierre no controlado.

## Versión instalada y validación

1.4.1-vault-debug (17) instalada por adb install -r: actualización sin desinstalar ni borrar datos. Firma v2 verificada. SHA-256 APK: 0982DF8744055A8324EC21E2D6B6A07FC40B562D565829889B11051EECE78DFA.

12 pruebas JVM de bóveda y 43 de app pasaron con la dependencia corregida. Se añadió VaultAndroidRuntimeTest para probar ida/vuelta de varios segmentos y rechazo de alteración usando el runtime real del APK. Su APK compiló, pero el dispositivo rechazó su instalación con INSTALL_FAILED_USER_RESTRICTED (Install canceled by user). Por tanto la prueba instrumentada no se ejecutó.

## Registro de tiempos

VaultDebugTrace emite DNIE_PERF únicamente en builds debug. Etapas: apertura PKI/PACE, lectura CE83, análisis de certificado, PIN, recuperación privada de clave, prueba sintética, restauración completa, autenticación en temporal privado y publicación a SAF; operaciones locales también tienen tiempo global.

Se registran etapa, resultado, milisegundos y nombre de hilo. No se registran CAN/PIN, certificado, huella del titular, nombre/URI del documento, contenido ni claves. El hilo al inicio/final de una medida suspendida no implica que su trabajo interno ocurra en ese hilo: el motor de archivos usa Dispatchers.IO.

La captura ADB se filtra por UID 10499 y tags de métricas, errores del runtime y cuadros de interfaz. Registros reales se conservan fuera del repositorio, en ../diagnosticos-locales/2026-10-05-boveda, y no deben agregarse a Git. debug-live.txt continúa recibiendo eventos mientras su proceso ADB esté activo. capture-pid.txt identifica ese proceso local.

## Hallazgos iniciales de fluidez

Arranque frío medido por am start -W: 1438 ms. Choreographer reportó saltos de 59 y 35 cuadros al inicio/entrada de Bóveda. Preparación local de Bóveda: 271 ms. La muestra posterior de gfxinfo indicó 2477 cuadros, 49 janky (1,98%), 44 eventos de hilo UI lento, percentiles 90/95/99 de 27/48/89 ms. Son métricas de esta sesión debug, no una comparación controlada ni una medida específica del descifrado.

Esto acredita tirones de UI; aún no identifica el método responsable. No se atribuyen a NFC, sonido, vibración ni al cifrado sin medición por etapa. Pendiente reproducción del usuario con DNI real para comprobar recuperación de un contenedor existente y sus tiempos. El CAN y PIN se ingresan exclusivamente en el teléfono.

## Reproducción física capturada — resultado correcto

A las 00:05:58–00:06:00 del 5 de octubre se capturó la recuperación real de un contenedor mediante DNI NFC, sin fallo:

| Etapa | Tiempo |
|---|---:|
| Apertura PKI/PACE | 367 ms |
| Lectura CE83 | 579 ms |
| Análisis del certificado | 2 ms |
| Verificación de PIN | 57 ms |
| Recuperación privada de clave | 219 ms |
| Autenticación de archivo en temporal | 25 ms |
| Copia al destino SAF | 16 ms |
| Restauración completa de archivo, incluyendo limpieza | 48 ms |

Tiempo observado desde apertura PKI hasta restauración final: aproximadamente 1,33 s. La etapa de archivo terminó con outcome=ok; no hubo AndroidRuntime fatal en esta captura. La mayor parte del tiempo de esta recuperación está en NFC y lectura del certificado, no en el descifrado del archivo de esta muestra. No se registró tamaño del documento; no se extrapola este tiempo a archivos grandes ni se afirma una comparación de hash contra el original.

Una prueba sintética inmediatamente anterior también terminó correctamente: apertura 436 ms, CE83 617 ms, análisis 3 ms, PIN 67 ms y prueba privada 274 ms.

Se mantienen separados los tirones iniciales de UI de los tiempos NFC; no se cambió el protocolo ni se omitieron verificaciones para reducir latencia. La captura sigue activa para posteriores reproducciones. Una sesión debug recién iniciada, con compilación JIT y renderizado inicial, no sirve por sí sola para adjudicar los tirones a un método; haría falta muestreo de CPU/main thread para ese detalle.

## Nombre y extensión: corrección 1.4.2-vault-debug

El contenedor ya cifraba el nombre original (incluida la extensión) y el MIME. La pérdida aparente ocurría al publicar: el selector había creado previamente un documento de nombre genérico y el callback descartaba los metadatos recuperados.

La entrega 1.4.2 (18) utiliza esos metadatos autenticados después de copiar todo el contenido: si el proveedor admite FLAG_SUPPORTS_RENAME, llama a DocumentsContract.renameDocument para recuperar el nombre original. Si falla o no está soportado, conserva la copia y muestra en pantalla el nombre original seleccionable para renombrar manualmente. No cambia el MIME almacenado por proveedores que lo mantienen fijo, ni afirma que todos los destinos admitan renombrado. Un conflicto con un original existente puede impedir el cambio de nombre; no se elimina ni sobrescribe ese original para resolverlo.

El nombre recuperado solo vive en el estado UI de esa operación y se limpia al cancelar/pausar o iniciar otra; no entra en saved state, preferencias, lista de bóveda ni logcat. El evento debug de renombrado contiene únicamente supported=true/false. API utilizada: https://developer.android.com/reference/android/provider/DocumentsContract#renameDocument(android.content.ContentResolver,%20android.net.Uri,%20java.lang.String) .

## Muestreo de CPU del arranque

Se obtuvo una traza ART por muestreo de 1 ms, conservada fuera del repositorio como startup-default.trace. El primer intento con versión de salida forzada produjo un archivo vacío; la captura con formato predeterminado sí fue válida. Se detuvo el profiler al terminar.

En los primeros 2 segundos, reconstruyendo eventos del hilo main, hubo aproximadamente 294 ms exclusivos de CPU en DexFile.openDexFileNative. VaultViewModel/VaultCapabilityProbe.<init> aparecieron con unos 69 ms inclusivos; DnieApplication.onCreate con unos 68 ms y creación del proveedor BouncyCastle con unos 65 ms inclusivos. El recorrido inicial de Compose/medición y dibujo también ocupa una parte significativa. Tiempos inclusivos contienen llamadas hijas y NO se suman entre sí. El muestreo tiene sobrecoste; no constituye un benchmark comparativo.

Cambio acotado: VaultCapabilityProbe pasa a inicialización lazy y su primer uso se realiza dentro de IO/crypto, evitando su construcción anticipada en el hilo UI al crear los ViewModels. Se conserva la inicialización temprana del proveedor criptográfico para respetar el flujo de firma que funciona. No se declara eliminada toda la lentitud ni se alteraron verificaciones NFC para acortarla.

1.4.2-vault-debug: 43 pruebas de app pasaron tras los cambios de nombre e inicialización. Firma v2 verificada. SHA-256 APK: FED8B429ED505B452766C3505518C1BBCE2DED18BD3D782DAA074B8DA7C9F379. La recuperación automática de nombre requiere comprobación con el proveedor de destino del usuario; la recuperación del contenido mediante NFC ya fue observada correctamente en 1.4.1.

## Comprobación física en 1.4.2

00:16:25–00:16:27: apertura PKI 432 ms, CE83 564 ms, análisis 2 ms, PIN 65 ms, recuperación privada 208 ms. Autenticación de contenido 21 ms, publicación SAF 14 ms. El evento file.restore_name terminó con supported=true: el proveedor admitió y devolvió éxito de renombrado. Restauración completa, incluido renombrado y limpieza, 115 ms, outcome=ok. No hay nuevo cierre del runtime en esa operación. No se consultó ni registró el nombre del documento real; el usuario puede comprobarlo en su gestor de archivos.

Persisten dos avisos de 38 cuadros omitidos al arrancar y entrar a Bóveda; preparación local 292 ms. No se presentan esos valores como una mejora estadística frente a la sesión anterior. Se conservan las métricas 1.4.2 en gfx-1.4.2.txt. La captura ADB de métricas sigue activa; el muestreo ART ya se detuvo.

## Nombres visibles en la bóveda — 1.4.3-vault-debug

Cambio solicitado explícitamente por el usuario después de comprobar nombre y extensión al recuperar. El contenido y los metadatos originales dentro del contenedor siguen cifrados, pero ahora se conserva además una etiqueta de nombre en texto claro, dentro del directorio privado de la app, para mostrarlo sin NFC. Por tanto ya no aplica a esta versión la afirmación anterior de que no existe ningún índice de nombres en claro.

Se mantienen IDs aleatorios para rutas internas y para separar dos archivos con el mismo nombre. El nombre no se utiliza como ruta ni como criterio criptográfico; se acota a 1024 bytes y se rechazan rutas/caracteres de control. Las etiquetas se escriben mediante temporales; si no pueden guardarse, se conserva el contenedor y se muestra su identificación alternativa. Eliminar un contenedor elimina también su etiqueta.

Al cifrar un archivo nuevo, su nombre aparece inmediatamente en la lista. Al exportar se propone nombre-original.ext.dnievault y al recuperar se propone nombre-original.ext cuando la etiqueta existe. Al importar, el nombre externo del archivo .dnievault sirve solo de etiqueta no verificada; después de descifrar correctamente, se sustituye por el nombre autenticado del contenedor.

Los contenedores anteriores sin etiqueta conservan compatibilidad: al descifrarlos una vez, la bóveda aprende y conserva el nombre. No se recifran ni se intenta extraer su metadata sin el DNI. Los logs siguen excluyendo nombres, claves y credenciales.

Pruebas añadidas: etiquetas tras reiniciar, archivos distintos con el mismo nombre sin sobrescritura, eliminación de etiqueta asociada, aprendizaje de nombres de contenedores antiguos, sustitución de etiquetas importadas por metadata autenticada y rechazo de etiquetas con rutas/longitudes excesivas.

1.4.3-vault-debug (19) instalada por adb install -r, sin borrar datos. 57 pruebas de módulos afectados (14 bóveda + 43 app) pasaron. El APK de instrumentación compiló; no se volvió a intentar instalar el APK de pruebas rechazado por el dispositivo. Firma v2 verificada. SHA-256 APK: B1B86C8D3C3D3B2E5EBC2E798079331EFD44A779D621A81066AE0B67CB61D114.
