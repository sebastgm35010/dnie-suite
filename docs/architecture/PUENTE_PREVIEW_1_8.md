# Puente NFC–PC: implementación inicial 1.8.0

La app evolucionada incorpora **Certificados → PC**, una pantalla aislada de emparejamiento y sesión. Es un perfil experimental de lectura de certificados; no declara que Ciudadano Digital ni otras aplicaciones PC/SC sean ya compatibles. El host real permanece bloqueado mientras el lector virtual Bix exponga su puerto sin cifrar.

## Separación de responsabilidades

- `dnie-bridge-core`: framing VPCD, política de operaciones y mapeo ATR ISO 14443-A basado en los bytes históricos reales. El mapeo sigue [PC/SC Part 3](https://pcscworkgroup.com/Download/Specifications/pcsc3_v2.01.09.pdf); no copia el ATR de un DNI por contacto.
- `dnie-protocol`: apertura IAS → PKI → PACE → PKI protegido, mediante la fachada específica ya incorporada.
- `dnie-bridge-android`: importación acotada del aprovisionamiento, pin TLS, token y propietario del transporte durante autenticación, PACE, lectura y cierre.
- `app/data/bridge`: acceso al archivo SAF y adaptación de Tag/IsoDep. No depende de Compose.
- `BridgeViewModel`: coordina autorización, tareas IO, estado y cancelación; no implementa criptografía.
- `BridgeActivity`: posee exclusivamente Reader Mode mientras está en primer plano; desactiva el lector y cancela al pasar a segundo plano. La pantalla Compose solo presenta estado y acciones.
- `tools/windows-bridge`: host .NET y diagnóstico de aislamiento; [contrato y comandos](PUENTE_WINDOWS_PROTOCOLO.md).

El CAN se hereda del almacenamiento principal y puede editarse para esta sesión. No se envía a la PC. El archivo importado contiene pin, dirección, token y vencimiento; se procesa en memoria, con límite de 8192 bytes, sin guardar una copia ni solicitar permisos persistentes del documento. Las copias de pin/token y los buffers de transporte se borran al cerrarse. No se garantiza eliminación inmediata de Strings inmutables del runtime.

La app muestra dirección y huella completa SHA-256 del servidor para que el usuario la compare con la PC. La importación debe hacerse por un canal de confianza: el token constituye una credencial de conexión. No se implementó descubrimiento de equipos ni emparejamiento por QR en esta entrega.

## Propiedad del NFC y cierre

El puente no se abre mientras el inspector, un sondeo, firma, identidad o bóveda mantienen una operación activa. El inspector ahora cierra el transceptor al finalizar, libera el canal al cancelar y rechaza un segundo tag durante una lectura. La cancelación no produce un error tardío ni mantiene permanentemente el indicador de ocupación.

Se corrigió también el caso en que una cancelación coincide con `IsoDep.connect`: si la conexión termina después de haberse solicitado el cierre, se cierra inmediatamente. La apertura PACE y la red se ejecutan fuera del hilo de interfaz.

La sesión no sigue activa al salir de la pantalla o bloquear el teléfono. Mantener la pantalla encendida evita suspensiones accidentales durante una lectura, pero no reemplaza un servicio en primer plano. Un solo cliente y un solo canal seguro pertenecen a cada sesión.

## Límites que aún impiden una prueba completa

1. **Aislamiento Windows:** Bix instalado liga `0.0.0.0:35963`; no existe opción INI para ligar únicamente localhost. `doctor` lo identifica y `serve` lo rechaza antes de abrir TLS. El acceso directo al puerto eludiría la protección del enlace. No se modificaron driver, firewall o política de firmas de Windows.
2. **Reinicios de tarjeta:** OFF/RESET cierran la sesión y exigen una conexión NFC/PACE nueva. Los clientes PC/SC pueden reiniciar durante enumeración; todavía falta coordinar reconexión física y nueva autenticación con esos clientes.
3. **Perfil RENIEC:** su Java ejecuta PACE y una lectura de identidad propia. El perfil actual rechaza PACE/PIN/firma originados en PC, por lo que falta un perfil compatible separado; no debe fingir tokens ni respuestas satisfactorias.
4. **Permisos Windows:** TLS autentica el equipo/emparejamiento, no identifica el proceso que origina las APDU PC/SC. La lista de operaciones es de sesión; la autorización por aplicación requiere trabajo adicional.
5. **DNI real:** no se ha probado esta pantalla con NFC físico a través del host nuevo. La prueba interoperable usa una tarjeta simulada; no valida los puntos anteriores.

La siguiente etapa es resolver el acceso local al lector Windows, implementar y probar reset/reconexión, y ensayar lectura real antes de añadir firma o el perfil RENIEC.

## Validación

La compilación y resultados finales se registran en `build/bridge-ui-validation.txt`, `build/bridge-ui-final.txt` y `build/bridge-windows-interop.txt` (archivos locales excluidos de Git).

Se ejecutaron pruebas JVM de núcleo/adaptador/protocolo, pruebas de propiedad y cancelación del inspector, las regresiones de app y las 16 comprobaciones Windows. La prueba real entre Kotlin/JVM y .NET pasó con TLS, ATR, SELECT, READ, VERIFY rechazado y desconexión. Usó certificado y token temporales, un falso lector ligado a loopback y ningún DNI. El aprovisionamiento temporal se eliminó al terminar.

El fixture Windows solo existe en el proyecto de pruebas. Su modo `--interop-fixture` crea una carpeta privada nueva en NTFS; G: rechazó la restricción de ACL, de modo que se trasladó a `%LOCALAPPDATA%\DNIeTools`. No se deben guardar credenciales de emparejamiento en la carpeta sincronizada de la repo.

APK: `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`, identidad `pe.gob.reniec.dnie.evolution.debug`, versión `1.8.0-bridge-preview` (33). Conserva la firma de desarrollo del fork.
