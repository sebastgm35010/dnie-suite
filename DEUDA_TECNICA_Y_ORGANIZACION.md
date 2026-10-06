# Organización y deuda técnica antes de passkeys

Fecha: 2026-10-05. Versión: `1.4.5-refactor`, código 21.

## Alcance

Esta entrega reorganiza el fork `modest-bose-evolution`, sobre la versión funcional `1.4.4-ui-perf`. La base clausurada `modest-bose` y la carpeta de Antigravity no se modifican. El APK anterior queda respaldado en `app/build/outputs/apk/checkpoints/dnie-evolution-1.4.4-ui-perf.apk`, fuera de Git.

No se incorporan passkeys en esta entrega. La separación prepara responsabilidades para nuevas funciones sin acoplarlas a las pantallas existentes.

## Cambios realizados

### Bóveda

El acceso a almacenamiento deja de estar mezclado con el estado de pantalla en `VaultViewModel`:

| Archivo | Responsabilidad |
| --- | --- |
| `app/.../data/vault/AndroidVaultRepository.kt` | Inicialización, inventario, preferencias, certificado público, importación, exportación, eliminación y publicación del archivo recuperado mediante SAF. |
| `app/.../data/vault/RestoredDocumentName.kt` | Renombrado mediante el proveedor de documentos Android. Antes estaba bajo `domain/vault` pese a depender de Android. |
| `app/.../ui/vault/VaultViewModel.kt` | Estado, validación de entrada, inicio/cancelación, coordinación NFC, verificación de destinatario antes del PIN y liberación del secreto recuperado. |
| `dnie-vault-core` | Formato del contenedor, cifrado por segmentos, metadatos autenticados, archivos privados y comprobación completa antes de publicar plaintext. |

El repositorio conserva únicamente el contexto de aplicación. Sus operaciones de disco y SAF son suspendidas, pasan a `Dispatchers.IO` y se serializan con un mutex. El certificado público se guarda con `AtomicFile`. El repositorio no verifica PIN ni abre sesiones de tarjeta.

La actualización del estado de pantalla vuelve al hilo principal tras cada operación suspendida. Se conserva la carga diferida de la prueba criptográfica para no reintroducir el coste de inicialización en el arranque.

Se mantienen rutas privadas, formato de archivo, nombres y extensiones, límites, autenticación previa a exportar plaintext, limpieza de temporales y cancelación. El secreto se borra en `finally` y el lector se cierra antes de publicar el archivo descifrado.

### Pantallas y consola

| Función | Separación |
| --- | --- |
| Bóveda | `VaultScreen` compone la pantalla y coordina selectores; `VaultComponents` dibuja tarjetas/resultados; `VaultTheme` reúne colores compartidos. |
| Consola | `ConsoleViewModel` coordina acciones y logs; `ConsoleUiState` define estado/filtros; `ConsoleFeedback` adapta sonido/vibración Android; `ConsoleScreen` compone la pantalla; `ApduLogItem` dibuja cada registro. |
| Certificados | `CertificatesScreen` conserva la pantalla; `EmptyCertificatesView` contiene el estado vacío. |

Las extracciones visuales conservan los cuerpos Compose y callbacks. Se mantienen las optimizaciones de la versión 1.4.4: colección ligada al ciclo de vida, inicialización diferida y objetos de tema compartidos. Separar el adaptador de sonido/vibración no constituye una validación de esos efectos en el teléfono.

### Documentación

`ARQUITECTURA_FORK.md` describe ahora la bóveda de archivos real, los límites entre datos/estado/presentación y el comando completo de regresiones. Su resultado de 120 pruebas corresponde a la primera modularización histórica, no a la entrega actual.

## Reglas para continuar

- Los motores de criptografía, formato y protocolo no dependen de Compose ni de `app`.
- SAF, preferencias, imágenes Android y contexto permanecen en adaptadores Android.
- Un componente Compose muestra estado y emite acciones; no cifra ni transmite APDU.
- Un ViewModel coordina una operación y su cancelación; no incorpora formatos binarios ni implementación de archivos.
- Las nuevas operaciones reutilizan los motores por sus APIs; no reutilizan un ViewModel de otra pantalla como servicio.
- CAN/PIN, contenido descifrado y claves no forman parte de logs, documentos o commits. Los checkpoints APK y claves de desarrollo quedan fuera de Git.
- Cada extracción de protocolo necesita regresiones que documenten la secuencia de comandos y los estados seguros. No dividir una sesión segura entre propietarios independientes del SSC.

## Deuda que permanece y orden recomendado

| Prioridad | Trabajo | Criterio para hacerlo |
| --- | --- | --- |
| 1 | Ampliar pruebas del adaptador Android de bóveda con proveedores SAF: destino que no permite renombrar, error de escritura y cancelación. | Mantener separado el éxito del descifrado del soporte de renombrado del proveedor. Las pruebas actuales del motor no sustituyen esta integración. |
| 2 | Extraer del transceptor legado los flujos restantes de lectura de certificados y firma cuando existan fixtures completos. | Preservar IAS → PKI → PACE → PKI protegido, MAC, SSC y GET RESPONSE físico. La longitud del archivo por sí sola no justifica una extracción arriesgada. |
| 3 | Reducir la coordinación de operaciones PDF/lotes dentro de `SignerViewModel` mediante casos de uso existentes. | Conservar separación de lotes y PDF, progreso, cancelación y cierre de tarjeta. |
| 4 | Definir un límite inyectable de repositorio si nuevos consumidores o pruebas de estado lo necesitan. | Evitar introducir un framework de inyección o interfaces vacías sin un consumidor real. |
| 5 | Incorporar passkeys como función independiente, con estado, almacenamiento y protocolo propios. | Primero fijar registro/autenticación, compatibilidad y recuperación. La capacidad RSA del DNI no equivale por sí sola a un autenticador FIDO2. |

La reorganización reduce la mezcla de responsabilidades; no declara saldada toda la deuda. La validación física de NFC y proveedores Android sigue siendo necesaria tras cambios en sus adaptadores.

## Validación

La regresión completa y la exportación del APK finalizaron correctamente. Los resultados XML registran 214 pruebas, sin fallos, errores ni omisiones:

| Módulo | Pruebas |
| --- | --- |
| `dnie-core` | 49 |
| `dnie-protocol` | 61 |
| `dnie-vault-core` | 14 |
| `dnie-icao` | 27 |
| `dnie-pdf` | 20 |
| `app` | 43 |

Los módulos sin cambios conservaron sus resultados válidos mediante la comprobación incremental de Gradle; las pruebas de `app` se ejecutaron tras la extracción. También se compilaron los fuentes de pruebas instrumentadas Android. No se ejecutaron pruebas instrumentadas ni se repitió una operación NFC física en esta entrega.

Una búsqueda de imports confirma que los fuentes principales de `dnie-core`, `dnie-protocol`, `dnie-icao` y `dnie-vault-core` no importan `android.*`.

El APK de salida es `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`, con el mismo identificador y clave de desarrollo del fork. Tras la solicitud del usuario, se instaló mediante `adb install -r` con resultado `Success`, conservando los datos existentes. La validación física con el DNI de esta versión sigue pendiente.

La firma APK v2 se verificó con `apksigner`; `aapt` confirma paquete `pe.gob.reniec.dnie.evolution.debug`, versión `1.4.5-refactor`, código 21. SHA-256: `8AEB1DD2611D64DFBDD9714659298354A0D1D7BF9848850382F4619F24979BAE`. La revisión de whitespace con `git diff --check` terminó sin errores.

Después de instalarla, el usuario confirmó que todas las funciones probadas funcionan de muy buena forma. Es una validación de uso reportada por el usuario; no se atribuye a una nueva captura instrumental ni a una lista formal de operaciones. Su instrucción de mantener separados motor e interfaz quedó registrada en `AGENTS.md`.
