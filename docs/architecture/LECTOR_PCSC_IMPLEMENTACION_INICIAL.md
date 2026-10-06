# Lector PC/SC propio: implementación inicial

Estado actualizado: controlador UMDF2 original x64 firmado e instalado para pruebas por autorización explícita, PnP OK y lector enumerado por PC/SC. Pasaron 25 comprobaciones del lector instalado y 45 del puente .NET. La instalación usa confianza local de un certificado dedicado; no cambia Secure Boot ni opciones de arranque. Véase [registro de firma y pruebas](LECTOR_PCSC_PRUEBAS_SINTETICAS.md). Las secciones posteriores conservan la historia de los checkpoints iniciales y sus limitaciones.

## Separación de responsabilidades

- `tools/windows-pcsc-driver`: dispositivo PnP UMDF2, interfaz SmartCardReader para el administrador PC/SC de Windows y una interfaz local privada para el backend. Código propio bajo MIT, sin incorporar el controlador GPL de Virtual Smart Card.
- `tools/windows-bridge/WindowsPcscDevice.cs`: descubrimiento por GUID y acceso a los cuatro IOCTL privados. No registra nombres ficticios mediante SCardIntroduceReader.
- `tools/windows-bridge/PcscBackend.cs`: ATR real, solicitudes correlacionadas, transporte secuencial y retirada de tarjeta al terminar la sesión. El estado de transporte NTSTATUS se distingue del status word de la tarjeta: una respuesta `6A82` se conserva.
- Android sigue siendo el dueño del enlace NFC, PACE y mensajería segura. El perfil actual permite únicamente leer certificados; esta entrega no habilita PIN ni firma desde el PC.

Recorrido previsto: aplicación PC/SC → administrador Smart Card de Windows → controlador propio → IOCTL local → backend → pipe local privado → TLS autenticado → Android → PACE → DNI.

En el canal NFC tipo A, Android construye una ATR de contacto virtual a partir de los bytes históricos obtenidos de IsoDep. Las referencias a «ATR real» en esta entrega significan esa ATR procedente de la sesión NFC, con formato y checksum comprobados; no una ATR leída de una interfaz física de contacto del DNI ni una constante de tarjeta simulada.

## ABI local

GUID backend: `{6D735823-37A5-4DF8-98C4-0E512B45D8F1}`. Todos los enteros son little endian; tamaños y códigos coinciden con `BridgeAbi.h`.

| Operación | IOCTL | Entrada/salida |
|---|---|---|
| ATTACH | `0x22E000` | Cabecera de 16 bytes: versión 1, protocolo T=0/T=1, tamaño ATR, reservado cero; ATR real hasta 33 bytes |
| DETACH | `0x22E004` | Sin entrada ni salida |
| GET_REQUEST | `0x22E008` | Cabecera de 24 bytes: id u64, opcode u32, tamaño u32, capacidad u32, reservado cero; payload |
| COMPLETE | `0x22E00C` | Cabecera de 16 bytes: id u64, NTSTATUS u32, tamaño u32; payload |

Opcodes: 1 transmitir APDU sin PCI, 2 reiniciar, 3 apagar. Máximo payload 65535 bytes. GET sin solicitud devuelve STATUS_NO_MORE_ENTRIES, con sondeo cancelable desde .NET. No se transmite información del DNI en logs.

## Validación realizada

`dotnet build tools/windows-bridge/tests/WindowsBridge.Tests.csproj -c Release --no-restore --verbosity quiet`

`dotnet tools/windows-bridge/tests/bin/Release/net8.0/WindowsBridge.Tests.dll`

45 comprobaciones aprobadas: las 25 anteriores de TLS/ACL/framing más 20 de ABI y relay PC/SC. Incluyen ATR malformado, checksum incorrecto, id cero, campos reservados, tamaños inconsistentes, respuesta `6A82`, buffer insuficiente, EOF con solicitud pendiente y rechazo de reinicio sin sesión física nueva. Son pruebas con datos sintéticos; no ejecutan el controlador.

`pcsc-devices` se ejecutó contra Configuration Manager de este Windows y confirmó que aún no existe la interfaz del nuevo controlador. No establece conexión con el DNI ni con Bix.

## Comandos del backend para cuando exista el controlador

`dotnet tools/windows-bridge/bin/Release/net8.0/WindowsBridge.dll pcsc-devices`

Con el host `serve-local` ya autenticado y mostrando su nombre de pipe, el proceso del backend puede usar `pcsc-relay --pipe DNIeBridge-<32 hex> [--device <ruta de interfaz>]`. Esta orden no instala el dispositivo. Ambos procesos deben tener permisos sobre el pipe y sobre el dispositivo.

## Bloqueos antes de instalación y aceptación

1. Compilación inicial completada: MSVC 14.44, SDK 26100.7705 y WDK 26100.6584 en D:. DLL, INF validado y catálogo sin firma disponibles en `D:\DNIeToolchain\ReaderBuild\DniBridgeReader`. La compilación no demuestra funcionamiento de las colas WDF ni de PC/SC en ejecución.
2. Crear y verificar el catálogo firmado y el paquete específico de este lector. No desactivar Secure Boot ni comprobación de firmas. Instalación PnP y firma no son equivalentes a TLS.
3. El INF inicial limita el acceso del dispositivo a SYSTEM. Es una base cerrada de desarrollo, incompatible todavía con el backend interactivo normal. Definir la instalación para autorizar solamente al usuario elegido o un broker mínimo; no ejecutar automáticamente todo el host como SYSTEM.
4. Implementar reinicio físico/NFC y reapertura PACE coordinada. El backend actual responde NOT_SUPPORTED y retira la tarjeta ante reset/apagado; no finge que la sesión anterior se reinició. Windows suele solicitar resets al conectarse, por lo que esto bloquea el flujo real.
5. Verificar enumeración `SCardListReaders`, presencia/ausencia, conexión, reset, protocolo, cancelación y desconexión bajo Driver Verifier y aplicaciones reales. El código fuente UMDF no ha recibido esa validación.
6. Resolver las variantes APDU del middleware oficial y seleccionar perfiles explícitos. Que se enumere un lector no prueba compatibilidad con Ciudadano Digital ni capacidad de firma.

El transporte puede seguir usando TLS sobre el túnel ADB inalámbrico existente sin abrir puertos en el firewall. No se ha ejecutado ningún despliegue nuevo en el celular en esta entrega.

## Herramientas y almacenamiento: 5 de octubre de 2026

`lector-pcsc.bat` ofrece diagnóstico, instalación de herramientas, compilación y despliegue de un paquete firmado. El modo diagnóstico se probó desde una terminal normal: carga MSVC 14.44.35207 y MSBuild de Build Tools 2022, y encuentra SignTool del SDK en D:. InfVerif y DevCon faltan porque el WDK permanece pendiente. No se ejecutó compilación tras la solicitud de detener el desarrollo.

Ubicaciones: Build Tools `D:\DNIeToolchain\VS2022`; SDK `D:\DNIeToolchain\WindowsKits\10`; descargas `D:\DNIeToolchain\Temp`; futuros artefactos nativos `D:\DNIeToolchain\ReaderBuild`. La raíz SDK registrada en Windows confirmó D: y se verificó la presencia de `windows.h`. El instalador SDK terminó con código cero.

El SDK/WDK completo inicialmente añadido en C: se retiró mediante los instaladores Microsoft. El complemento SDK AddOn nuevo de Build Tools retenía una referencia de ruta antigua, por lo que se retiró también; el valor obsoleto KitsRoot10 se respaldó en `build/pcsc-deployment/sdk-root-before-relocation.json` antes de limpiarlo y registrar la nueva instalación. El complemento WDK de Visual Studio se agregará después de que el SDK esté en D:, al reanudar explícitamente la instalación. Los archivos personales y las versiones congeladas del proyecto no se movieron.

Comprobación final: C: aproximadamente 14 GiB libres; sin instaladores de SDK/WDK activos; Bix permanece PnP OK. El estado se guarda en `build/pcsc-deployment/toolchain-status.json`. Ese espacio es el observado al finalizar, sin atribuir a esta operación cambios de almacenamiento ajenos a la instalación. Windows mantiene sus cachés y registros de mantenimiento; no se trasladan ni borran manualmente.

### Reanudación y primera compilación validada

La instalación se reanudó por indicación del usuario. WDK 26100.6584, InfVerif y DevCon quedaron en D:. Se corrigió el componente de integración de Build Tools (`Component.Microsoft.Windows.DriverKit.BuildTools`), la propiedad `WDKBuildFolder`, el orden de cabeceras GUID y la definición de la interfaz SmartCardReader. El INF ahora usa DIRID 13, referencia WUDFRD.inf y declara Windows 10 build 17134 como mínimo.

El verificador administrado del WDK arrojaba NullReferenceException; el proyecto usa directamente el InfVerif oficial `/w`, manteniendo los errores como fallo de compilación. El INF final conserva el SHA256 del fuente revisado y el catálogo se genera después de esa copia. El export UMDF `FxDriverEntryUm` se comprobó con dumpbin.

Se corrigió `DriverSignMode`, que no desactivaba la firma del WDK, a `SignMode=Off` y `EnableTestSign=false`. El intento automático anterior falló y creó un certificado de desarrollo en CurrentUser/My; se retiraron solamente ese certificado y su clave, identificados por huella y fecha. Se confirmó ausencia de esa huella en los almacenes comprobados y estado NotSigned de DLL y CAT finales. No se importó confianza ni se habilitó test-signing.

Resultado: `lector-pcsc.bat compilar` terminó con código 0, 45 comprobaciones del backend aprobadas y compilación nativa sin errores ni advertencias. Artefactos: `D:\DNIeToolchain\ReaderBuild\DniBridgeReader`. C: conserva aproximadamente 13,4 GiB libres. Bix sigue PnP OK y el nuevo dispositivo no está instalado. Quedan firma confiable del paquete, permisos mínimos para el backend, reinicio NFC/PACE y pruebas PC/SC en ejecución. La compilación no resuelve esos pendientes.
