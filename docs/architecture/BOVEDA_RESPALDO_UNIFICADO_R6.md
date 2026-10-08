# Respaldo único y recifrado al importar · R6

2026-10-08. Evolución autorizada sobre `d6be992`; checkpoint previo `checkpoint/pre-vault-unified-r6`. Sustituye la interfaz de R5, que exigía ZIP de ciphertext más paquete separado de protección.

## Operaciones

La biblioteca presenta Cifrar archivos, Importar cifrados y Exportar bóveda; cada tarjeta tiene Descifrar. Los asistentes de exportar/importar se abren desde esas acciones, sin pestañas Protección/Copias ni un ZIP sin protección ofrecido como respaldo. Vincular DNI y notas están en Opciones; vista previa permanece opcional solo para tipos compatibles.

Exportar: CAN/PIN del DNI que abre todos los archivos, contraseña confirmada y destino `mi-boveda.dniebackup`. Se autoriza en una sesión, se autentica cada documento, se comprime contenido y organización en ZIP privado y se cifra el conjunto. El usuario obtiene un único archivo autosuficiente, no documentos descifrados ni un sidecar. Se incluye papelera, carpetas, etiquetas, nombres y MIME. Exportar no modifica ni elimina los objetos originales.

Importar: respaldo + contraseña + CAN/PIN del DNI de destino, que puede ser uno renovado o distinto. Se comprueba que la tarjeta posee la clave privada de su certificado de cifrado vigente. Se vincula ese certificado público para el cifrado posterior; este vínculo puede permanecer aunque falle la importación. La importación autentica el archivo entero, valida ZIP/inventario y cifra todos los contenidos con claves nuevas bajo la pública elegida. La clave antigua no se necesita para importar.

Los UUID de contenido se conservan al recifrar. Solo se sustituyen los objetos existentes cuyo UUID está en el respaldo, tras preparar y incorporar todos los nuevos. Los objetos ajenos permanecen. Reimportar el mismo respaldo no acumula duplicados de esos documentos; restaura su contenido al estado del respaldo. No preserva marcas de verificación/respaldo del objeto anterior, que no acreditan el nuevo ciphertext.

## Motor y formato

- `dnie-vault-recovery/VaultArchiveCodec`: DNIEBKP2, versión 2, cabecera fija de 72 bytes con costes Argon2id, salt 16 y contexto aleatorio 32. Perfil fijo 64 MiB, 3 pasadas, 4 lanes; dominio distinto `dnie:archive:v2`.
- Cifrado por segmentos Tink AES-256-GCM-HKDF, HmacSha256, segmentos 1 MiB. Cabecera autenticada como AAD; no AES-GCM monolítico que acumule documentos en memoria. Rechaza costes o versión ajenos antes de derivar.
- `VaultArchiveService`: ZIP DEFLATE nivel 1; nombres internos `files/<índice>` y manifest binario. Nombres/MIME/carpeta/etiquetas están dentro del cifrado. No extrae rutas de nombres de usuario. Exige inventario cerrado sin entradas extra/duplicadas, CRC y longitud reales de cada entrada, de 1 a 1000 objetos, 1 GiB por documento y 16 GiB agregados. No admite perfiles KDF arbitrarios.
- `VaultContainer.sealWithId` mantiene UUID lógico pero genera secreto, envelope, keyset y nonces nuevos. Formato cotidiano DNIEVLT1 sin cambios. APIs previas seal/importEncrypted mantienen su firma y comportamiento habitual.
- `UnlockRecoveryBatch` conserva la sesión NFC y solicita el PIN al usuario una vez. La tarjeta consume autorización en DECIPHER: se hace VERIFY posterior por envelope, sin reintentar rechazos. Un lote con destinatarios distintos se rechaza antes del primer PIN.
- `AndroidVaultRepository`: mutex IO, SAF, vinculación pública y staging. Compose solo presenta acciones; ViewModel coordina sesión/prueba física y limpia claves/contraseña.

## Publicación y fallos

Exportación valida todos los documentos y finaliza el ciphertext privado antes de abrir destino SAF. Importación descifra a ZIP privado `.plain`, autentica EOF, recifra a almacén provisional y solo entonces publica objetos. Los temporales se limpian en finally y al iniciar; se requiere espacio privado para ZIP y objetos provisionales. No se asegura borrado físico de memoria flash ni resistencia de staging a un dispositivo comprometido.

La importación usa journal en `filesDir/vault-preview/transactions`, fuera de caché: se escribe/fsync antes de publicar nuevos UUID locales. Antes de la decisión durable se revierten nuevos objetos; después se conservan los nuevos y se termina de retirar los originales coincidentes. Al iniciar se procesa el journal antes de exponer inventario. Si escribir la decisión falla, se conserva el journal y ambas generaciones para que el reinicio resuelva su estado; no se borran nuevas y antiguas simultáneamente. Los archivos ajenos nunca figuran como reemplazados.

SAF no ofrece publicación atómica general: cancelación/error durante copia final puede dejar destino incompleto, sin modificar originales locales. Cambiar contraseña no revoca respaldos viejos. El archivo y contraseña juntos permiten recuperar contenido sin el DNI antiguo, alternativa explícitamente autorizada; el DNI de destino se exige en el flujo de importación de la app. Passkeys y biometría no cambian.

El backend conserva lectores de R3/R5 para compatibilidad y pruebas; los paquetes sidecar o ZIP históricos no son DNIEBKP2 ni se convierten implícitamente. Los archivos cotidianos ya cifrados siguen compatibles y pueden incluirse al exportar con su DNI original. Windows dispone del motor JVM probado, sin nueva interfaz de importación/exportación integrada todavía.

## Estado de validación

APK68 `2.0.0-rc20-unified-vault`. Compilación Gradle final superada. XML: app 79, vault-core 26 y vault-recovery 32 pruebas, sin fallos/errores (137 total; algunas tareas reutilizadas). Principal/auxiliar instaladas por ADB, ambas Success. Instrumentación Android: OK (4 tests), 14.624 s, incluye respaldo único con otra clave, dos compatibilidades legacy y navegación de asistentes. Ajuste final IME con teclado Password recompilado e instalado; repetición visual OK (1 test), 2.635 s. Pruebas nuevas: clave antigua→nueva, metadatos/papelera/nombres duplicados, restauración repetida sin duplicados, contraseña errónea/tamper/truncamiento, cancelación previa/durante commit, journal de rollback/commit y exportación con fuente alterada. Instrumentación aislada Android cubre repositorio con RSA sintética; no sustituye lote NFC ni SAF externo reales.

Pendientes físicos: exportar con DNI original e importar para el mismo DNI sin duplicados; pérdida NFC antes de completar autorización; SAF externo y espacio insuficiente. Cambio destructivo real de certificados no debe ejecutarse para una prueba. Cruce de archivos Windows↔Android pendiente; UI Windows y passkeys fuera de este cambio.
