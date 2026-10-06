# Bóveda personal — 1.4.0-vault-preview

## Alcance y evidencia

Implementado en modest-bose-evolution. La versión de firma congelada y la carpeta de Antigravity no se modificaron. APK independiente: pe.gob.reniec.dnie.evolution.debug. No se instaló ni se controló el teléfono para este despliegue.

El log de la prueba anterior confirmó la recuperación privada de un secreto de 32 bytes con el DNIe v3 por NFC y la comparación del contenido sintético. Esta entrega utiliza ese mismo comando para archivos reales. El flujo completo con un archivo y un DNI físico todavía requiere prueba del usuario: cifrar una copia pequeña, restaurarla y comparar su contenido/hash con el original.

La ausencia de vibración o sonido no demuestra la causa de la lentitud observada. Se conservan las mejoras anteriores de conexión fuera del hilo principal, conversión hexadecimal y procesamiento de logs. No se dispone de una medición física posterior que confirme cuánto mejoró la fluidez.

## Uso inicial

1. Entrar en Bóveda y aceptar el aviso de pérdida de acceso. Conservar los originales.
2. Vincular el DNI con CAN + PIN + NFC. La prueba debe finalizar correctamente; entonces se conserva únicamente el certificado público CE83.
3. Elegir un archivo y cifrar. Después de vincular, esta operación no requiere acercar el DNI. Límite inicial: 1 GiB por archivo.
4. Exportar cifrado permite respaldar el contenedor `.dnievault`; importar permite recuperar ese respaldo en la app. Importar comprueba estructura y límites, no autentica el contenido hasta descifrar.
5. Para descifrar una copia, elegir primero el destino del documento, ingresar CAN y PIN, iniciar y acercar el DNI. Antes del PIN, se compara la pública CE83 del DNI con el destinatario de la cabecera.

El destino propone `documento-restaurado`, con tipo genérico, porque el nombre original y el MIME están cifrados. Puede elegirse un nombre con extensión en el selector. Esta versión no cambia automáticamente el nombre del destino después de descifrar. No ejecuta ni abre automáticamente el archivo recuperado.

## Motor y formato v1

- dnie-vault-core mantiene formato, cifrado y almacenamiento independientes de Android/UI.
- Un secreto aleatorio de 32 bytes se envuelve mediante RSA-2048 PKCS#1 v1.5, compatible con la operación privada comprobada del DNIe v3. No se usa el certificado de firma ni se presupone soporte OAEP.
- Ese secreto cifra con AES-256-GCM un keyset nuevo de Tink. El archivo y sus metadatos se cifran con Streaming AEAD AES256_GCM_HKDF_1MB. Se generan claves nuevas por archivo.
- Cabecera: magic DNIEVLT1 (8), versión y suite uint32 big endian (1 y 1), UUID (16), SHA-256 SPKI del destinatario (32), envoltura RSA (256), nonce GCM (12), longitud uint32 acotada (17..8192) y keyset cifrado. Sigue el flujo Tink.
- AAD del flujo: magic + versión + suite + UUID. AAD del keyset: ese AAD + destinatario + envoltura RSA + nonce. La cabecera se considera no confiable hasta autenticar el keyset y el flujo completo.
- El prefijo dentro del flujo contiene nombre UTF-8 y MIME, ambos con longitud acotada. Después siguen los bytes originales hasta EOF autenticado. No hay índice de nombres en texto claro.
- Memoria acotada: copia con búfer de 64 KiB y segmentos criptográficos de 1 MiB. No implica memoria constante de 64 KiB para toda la biblioteca.
- UUID aleatorio como nombre local. Escritura a `.pending-*`, flush/sync y renombrado en el mismo directorio. Fallos y cancelaciones limpian el archivo pendiente. No se sobrescriben originales.

## Descifrado y privacidad

Se valida el destinatario antes de verificar el PIN. Cada solicitud requiere un nuevo PIN y una nueva operación NFC; no se implementa una API remota de descifrado. El secreto recuperado se borra del arreglo en finally y no se persiste ni se registra en logs. Las credenciales no entran en preferencias, saved state ni toString del estado.

El canal NFC se cierra después de recuperar la clave, antes de procesar el archivo completo. El contenido se descifra a un temporal privado en cacheDir/vault-private, fuera de las rutas FileProvider. Solo después de verificar todos los segmentos y EOF se copia al destino SAF elegido por el usuario. El temporal se elimina al terminar, incluyendo errores y cancelación; al reiniciar la app se limpian temporales dejados por muerte del proceso.

El destino SAF puede quedar parcialmente escrito si falla el proveedor o se cancela durante la copia final ya autenticada; Android no garantiza una publicación atómica en proveedores arbitrarios. El temporal privado requiere espacio para el tamaño descifrado. Borrar archivos no garantiza borrado forense en almacenamiento flash; tampoco se garantiza que el runtime JVM elimine todas sus copias internas de claves. La copia descifrada elegida deja de estar protegida por la bóveda.

Solo se conserva el DER público CE83 y el consentimiento del aviso; no se guarda una clave privada de recuperación. Renovar/perder el DNI puede hacer irrecuperables los archivos cifrados para su clave anterior. Reinstalar o borrar datos de la app elimina los contenedores internos: deben exportarse respaldos cifrados. allowBackup sigue deshabilitado.

La vinculación verifica posesión de la clave privada mediante prueba sintética; no afirma una validación completa de cadena, revocación o identidad del certificado. Se comprueba vigencia para cifrar archivos nuevos; recuperar archivos históricos compara la misma pública sin bloquear solo por expiración.

## Dependencia y pruebas

Tink 1.23.0: implementación Java para pruebas JVM y tink-android para el APK, sin duplicar ambas bibliotecas en Android. Licencia Apache-2.0 incluida en app/src/main/assets/third-party/Tink-LICENSE.txt. Fuente: https://github.com/tink-crypto/tink-java/tree/v1.23.0 . Este contenedor propio no pretende compatibilidad KDBX ni Windows en esta entrega.

Pruebas: ida/vuelta de archivo vacío y varios segmentos, metadatos ocultos, secreto incorrecto, cabecera alterada, versiones inválidas, longitudes hostiles, nombres con rutas, entradas que exceden límite o se estancan, rechazo de segmentos alterados/truncados antes de publicar, copias cifradas exactas, ausencia de commits tras fallo/cancelación, IDs seguros y limpieza de temporales tras interrupción. Se mantienen las pruebas de firma, ICAO, canal seguro y prueba sintética del ViewModel.

Passkeys continúan pendientes. Esta versión no anuncia un proveedor FIDO2 ni una bóveda de credenciales funcional.

## Resultado de compilación

1.4.0-vault-preview, versionCode 16. Se ejecutaron 212 pruebas JVM sin fallos: core 49, protocolo 61, bóveda 12, ICAO 27, PDF 20 y app 43. Las pruebas instrumentadas compilaron; no se ejecutaron en un dispositivo. El APK se exporta a app/build/outputs/apk/evolution/dnie-evolution-debug.apk. Se conserva 1.3.1-vault-lab en app/build/outputs/apk/checkpoints para comparación.

APK verificado con firma v2 y paquete pe.gob.reniec.dnie.evolution.debug. SHA-256: ED9FEA3338CDBC2865B0E302AF8800BD97DB47133798F0D39BDA95766ACFC4CB.

Actualización 1.4.1: el diagnóstico ADB identificó una incompatibilidad binaria entre Tink Java y Tink Android. El módulo ahora compila y prueba contra tink-android, como el APK. Se conservó el formato. La recuperación física de un contenedor se capturó correctamente después de actualizar. Véase DIAGNOSTICO_ADB_BOVEDA.md para el error, la corrección y los tiempos observados.

Actualización 1.4.2: tras autenticar y publicar, se intenta recuperar el nombre original (incluida su extensión) mediante DocumentsContract cuando el proveedor permite renombrado. Si no lo permite, el contenido sigue guardado y se muestra el nombre original en la UI. Ese nombre temporal no se incorpora al índice ni a los registros. El MIME del documento SAF puede seguir siendo genérico según el proveedor.

Actualización 1.4.3 solicitada por el usuario: el nombre queda visible en la lista y en el nombre propuesto para exportar .dnievault. Se añade una etiqueta de nombre en claro en almacenamiento privado; el contenido del archivo continúa cifrado y el contenedor conserva su nombre autenticado cifrado. Las afirmaciones de ausencia de nombres en claro corresponden a 1.4.0–1.4.2, no a 1.4.3. Los contenedores antiguos adquieren etiqueta después de un descifrado correcto.
