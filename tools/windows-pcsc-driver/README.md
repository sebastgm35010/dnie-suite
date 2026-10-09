# DNIe Android NFC: base de lector Windows PC/SC

Fuente **original MIT**, separada de Bix y de los ejemplos MS-PL de Microsoft. No contiene código GPL. El controlador x64 **compila con cero errores y advertencias** y el INF pasa InfVerif `/w`. Por autorización explícita se firmó para pruebas y se instaló en esta PC: PnP OK y enumeración PC/SC comprobada. Pasaron 25 comprobaciones del lector instalado con tarjeta simulada y 45 comprobaciones del puente. Esto no demuestra todavía el flujo NFC/PACE ni compatibilidad con RENIEC. Build Tools, SDK y WDK están en D:.

El objetivo del dispositivo `Root\DNIeBridgeReader` es exponer la interfaz estándar SmartCardReader al administrador PC/SC y una interfaz privada para nuestro host. El host TLS mantiene el canal Android; el lector no usa red, VPCD ni firewall.

## Archivos

- `DniBridgeReader.cpp`: WDF DeviceAdd, interfaces, atributos, presencia/ausencia, protocolo, power/reset y transmisión.
- `BridgeAbi.h`: contrato binario propio compartido con el adaptador .NET.
- `DniBridgeReader.inf`: paquete root-enumerated UMDF 2.15; ACL para SYSTEM y SID específico del servicio SCardSvr.
- `DniBridgeReader.vcxproj`: proyecto x64 para targets WindowsUserModeDriver10.0.
- `build.ps1`: únicamente compila; no instala, crea certificados, firma, registra ni habilita test-signing.

## Compilar

La raiz del fork incluye `lector-pcsc.bat`. `diagnostico` registra herramientas disponibles, `herramientas` instala Build Tools 2022 + SDK/WDK 26100 desde WinGet oficial (requiere administrador), y `compilar` ejecuta backend, pruebas y controlador. Build Tools se instala en `D:\DNIeToolchain\VS2022`, SDK/WDK en `D:\DNIeToolchain\WindowsKits\10`, descargas en `D:\DNIeToolchain\Temp` y artefactos nativos en `D:\DNIeToolchain\ReaderBuild`. El BAT hereda el entorno x64 automaticamente. No cambia la politica de ejecucion de PowerShell.

El SDK se instala antes del complemento WDK de Visual Studio, para que este no fije la raiz compartida en C:. `relocate-kits-to-d.ps1` se utilizó únicamente para retirar los kits y el SDK AddOn nuevos de esta sesión mediante sus instaladores, respaldar y limpiar el valor de ruta SDK que quedaba obsoleto, y reinstalar el SDK en D:. No mueve carpetas de Windows ni archivos personales. Los instaladores conservan sus registros y cachés de mantenimiento del sistema; no se borran manualmente. Este script termina con WDK y compilación pendientes.

Al reanudar se completó WDK 26100.6584 y se corrigió el componente de integración: Build Tools necesita `Component.Microsoft.Windows.DriverKit.BuildTools`, no el componente del IDE completo. El proyecto fija `WDKBuildFolder` a la versión SDK seleccionada. La tarea administrada de verificación del WDK falló con NullReferenceException; el proyecto invoca directamente el InfVerif oficial, conserva `/w` y falla ante cualquier error. El INF usa DIRID 13, WUDFRD.inf y Windows 10 build 17134 como mínimo. Se conserva exactamente el INF revisado para generar el catálogo y permitir su verificación posterior.

La propiedad WDK correcta es `SignMode=Off`, reforzada con `EnableTestSign=false`. La primera compilación alcanzó un intento automático de TestSign por una propiedad incorrecta; falló y creó un certificado temporal únicamente en CurrentUser/My. Ese certificado y su clave se retiraron por su huella exacta. Los artefactos finales están sin firmar; no se agregó confianza ni se habilitó test-signing. Los paquetes NuGet oficiales descargados para diagnóstico están en `D:\DNIeToolchain\NuGet`, con firmas verificadas; el proyecto final usa el WDK instalado y no depende de ellos.

`lector-pcsc.bat instalar "C:\ruta\paquete-firmado"` requiere administrador, el INF exacto de esta rama, DLL y catalogo confiable. Valida INF y pertenencia al catalogo mediante herramientas WDK firmadas; despues usa DevCon para crear el dispositivo root o PnPUtil para actualizar el existente. No duplica nodos, no reinicia Windows automaticamente y no importa certificados ni habilita test-signing. Este modo permanece bloqueado hasta que exista el paquete firmado y validado; el DLL compilado por si solo no basta. Los registros quedan en `build/pcsc-deployment`.

Para el paquete UMDF revisado se valida Authenticode con `/pa`, no política kernel `/kp`. La detección del dispositivo usa el hardware ID: DevCon lo creó como `ROOT\SMARTCARDREADER\0001`, que difiere de `Root\DNIeBridgeReader` y no debe provocar instalaciones duplicadas. `test-deploy.ps1` crea o reutiliza el certificado dedicado de 90 días, clave no exportable, firma DLL, regenera catálogo y firma CAT en ese orden. Importa solo ese certificado en los almacenes locales Root y TrustedPublisher; el usuario autorizó expresamente esta confianza para pruebas. No modifica Secure Boot ni BCDEdit. Paquete: `D:\DNIeToolchain\ReaderTestPackage`.

`run-synthetic-tests.ps1` crea una tarea SYSTEM temporal solo para el ejecutable de pruebas aislado, protegido con ACL, y la elimina al terminar. La app/host no se ejecuta automáticamente como SYSTEM. Resultados sintéticos: `build/pcsc-deployment/reader-synthetic-result.json`. `remove-test-installation.ps1` permite retirar el nodo propio, su paquete actual y el certificado exacto; no se ha ejecutado. Paquetes históricos sustituidos pueden permanecer en DriverStore.

Microsoft ofrece [EWDK](https://learn.microsoft.com/en-us/windows-hardware/drivers/download-the-wdk), un entorno autocontenido que incluye Build Tools, SDK y WDK. Una ISO EWDK local permite construir sin instalar Visual Studio completo. Desde su `LaunchBuildEnv.cmd` y `SetupVSEnv`, ejecutar PowerShell con el entorno heredado:

```powershell
& 'G:\Mi unidad\BOT TRADER\DNI_CODEX\modest-bose-evolution\tools\windows-pcsc-driver\build.ps1' -Configuration Release
```

Para una pareja SDK/WDK concreta, usar `-SdkVersion <version-disponible>`. .NET MSBuild sin C++/WDK no basta. La compatibilidad exacta de los nombres de propiedades/targets, APIs UMDF y secciones INF debe validarse en el WDK disponible; `build.ps1` falla si no detecta herramientas. No se debe interpretar la existencia del proyecto como un artefacto binario funcional.

El proyecto declara explícitamente `UMDF_VERSION_MAJOR=2`, `DriverType=UMDF`, `ConfigurationType=DynamicLibrary` y el toolset UMDF de Windows 10. Se contrastaron esos nombres con el [proyecto UMDF 2 oficial](https://github.com/microsoft/Windows-driver-samples/blob/main/usb/umdf2_fx2/driver/osrusbfx2um.vcxproj); no se importó su implementación MS-PL. El binario se enlazó con UMDF 2.15 y el INF declara la misma versión mínima.

## ABI privado v1

Descubrir con Configuration Manager o SetupDi la interfaz GUID **`{6D735823-37A5-4DF8-98C4-0E512B45D8F1}`**, abrir su ruta con acceso lectura/escritura y llamar `DeviceIoControl`. No confundir este GUID privado con la interfaz estándar SmartCardReader. Todos los campos son little-endian, sin padding; tamaños definidos por `static_assert`.

| Operación | IOCTL | Entrada / salida |
|---|---|---|
| ATTACH | `0x22E000` | Entrada 16 bytes: `version:u32=1`, `protocols:u32` (1=T0 o 2=T1), `atrLength:u32`, `reserved:u32=0`; después ATR real de 2 a 33 bytes. Sin salida. |
| DETACH | `0x22E004` | Entrada vacía. Sin salida. Retira la tarjeta y cancela operación pendiente. |
| GET_REQUEST | `0x22E008` | Entrada vacía. Salida 24 bytes: `id:u64`, `opcode:u32`, `inputLength:u32`, `outputCapacity:u32`, `reserved:u32=0`; después payload. Si no existe pedido, `STATUS_NO_MORE_ENTRIES` (`0x8000001A`, usual conversión Win32 259). |
| COMPLETE_RESPONSE | `0x22E00C` | Entrada 16 bytes: `id:u64`, `status:u32` (NTSTATUS), `length:u32`; después respuesta. Sin salida. |

Los cuatro IOCTL usan `FILE_DEVICE_UNKNOWN`, funciones `0x800..0x803`, `METHOD_BUFFERED`, `FILE_READ_DATA | FILE_WRITE_DATA`. Solo un handle puede ser dueño de la tarjeta; otros no pueden completar ni obtener sus operaciones. `GET_REQUEST` entrega cada pedido una vez. El ID se correlaciona para impedir responder a una operación anterior; un consumidor debe descartar respuestas después de cancelar o desconectar.

Opcodes: `1=Transmit`, payload APDU **sin** SCARD_IO_REQUEST; `2=Reset`, payload DWORD original `SCARD_COLD_RESET` o `SCARD_WARM_RESET`; `3=PowerOff`, payload DWORD `SCARD_POWER_DOWN`. El driver quita PCI de la transmisión entrante y la agrega a la salida estándar. El máximo es 65535 bytes. `outputCapacity` limita la respuesta; reset acepta ATR real y power off no devuelve contenido. `STATUS_SUCCESS=0` significa transporte válido; `9000` sigue siendo una respuesta real de la tarjeta, nunca una sustitución del error del backend. Los NTSTATUS de error deben llevar longitud cero; estados informativos como `STATUS_PENDING` no se aceptan como completions.

## Seguridad y retiro de sesión

El INF declara **SYSTEM-only**: la configuración inicial permite al administrador smart card del sistema acceder, pero un host .NET ejecutado por el usuario interactivo **no podrá adjuntar la tarjeta**. No se ejecuta ni se instala un broker SYSTEM automáticamente. Antes de un despliegue se debe diseñar un broker con privilegios mínimos o una política de usuario explícita, revisando el SID y la exposición de IOCTL privados; no ampliar la ACL a todos los usuarios.

Sin backend no hay ATR ni tarjeta. La ATR entregada es del backend aprobado, no una constante ficticia; esta base comprueba longitud y TS, y todavía necesita validación completa del formato/TCK y de las capacidades de protocolo. Cerrar el handle propietario, DETACH, fallo de respuesta, timeout de 30 segundos o salida D0 retiran el estado. Las esperas de presencia/ausencia usan colas WDF manuales cancelables. Power/reset se delegan y requieren una respuesta real: no se afirma éxito con una ATR cacheada. El perfil Android actual rechaza reset/power off y cierra la sesión; **la enumeración de Windows puede exigir esos comandos y por ello el flujo real sigue bloqueado**.

La revisión de cancelación se abordó con lock pasivo explícito y transferencia de propiedad mediante `WdfRequestUnmarkCancelable` antes de acceder al buffer de una operación pendiente. Si el cancel callback ya posee el pedido, la respuesta no lo toca. Timer, cierre de archivo y D0 comparten el mismo control de estado. Estos cambios requieren pruebas WDF reales de carreras, cancelación y retirada; las pruebas .NET del ABI no demuestran seguridad del driver.

## Pendientes antes de instalar

1. Compilación y validación INF completadas; falta validar el controlador en ejecución con WDF y un consumidor PC/SC de laboratorio.
2. Probar enumeración real por SCardListReaders, negociación T0/T1, PCI, atributos que exija SCardSvr, buffers insuficientes, lecturas simultáneas, transacciones, cancelar/reset/retirada, D0 y cierre de backend. No se ha validado el registro completo que requiere SCardSvr.
3. Implementar reconexión/reset NFC con PACE y controles antes de atribuir compatibilidad a RENIEC. El protocolo del backend inicial no tiene operaciones para canal lógico ni transacciones específicas.
4. Revisar permisos e instalación de paquete PnP, firma y canales de distribución adecuados. UMDF no elimina automáticamente los requisitos de instalación/firma. No se debe desactivar Secure Boot, integridad o políticas de Windows para probarlo en la PC habitual.

No ejecutar `pnputil`, modificar registro/INI ni instalar este paquete como consecuencia de compilar. La instalación futura debe ser una acción explícita con un paquete revisado; hoy solo se entrega código revisable.


## Revisión de energía R1 · 2026-10-09

Paquete de desarrollo 2.0.0.3. La cola predeterminada tiene `EvtIoStop` y retira
la operación remota cancelable antes de abandonar D0. Si la cancelación gana
`WdfRequestUnmarkCancelable`, únicamente `CancelPending` completa la solicitud.
No se reencola una APDU/reset ni se conserva un backend autenticado después de
cancelación: el comando pudo haber llegado al chip y repetirlo es inseguro.

Las colas manuales de presencia permanecen no gestionadas por energía para que
D0Exit pueda purgarlas. El purge es síncrono y se hace fuera del lock propio;
D0Entry reinicia colas ya vacías. El timer no se vuelve a programar fuera de D0
y los IOCTLs se rechazan cuando el lector no está alimentado. La ABI privada,
las ACL y el tratamiento PACE permanecen iguales.

El INF aísla este lector en un host UMDF con
`UmdfHostProcessSharing=ProcessSharingDisabled`. Reduce la propagación de fallos
a lectores ajenos; no demuestra que esos lectores causaran los incidentes.

### Pruebas sin privilegios ni tarjeta

Desde un entorno VS x64/SDK, ejecutar `power-tests/run.ps1`. Compila e incluye
el C++ de producción con un seam WDF exclusivo de pruebas. Usa los tipos reales
Windows/WinSCard/ABI y verifica completado único, propiedad después de cancelación,
retiro antes de D0Exit, ausencia de replay, respuesta tardía, reset/poweroff,
notificaciones, timeout, cierre de handle y 200 carreras concurrentes. Resultados
R1: 6797 comprobaciones pasan, 200 carreras cancel/stop y 200 ciclos simulados.
Artefactos van a `D:/DNIeToolchain/ReaderPowerTests`.

El seam no reproduce el scheduler WDF, PnP ni una suspensión física. No instala
controladores, modifica el sistema ni usa DNI/PIN. Complementa, sin sustituir,
la batería `synthetic-tests` que habla con el controlador instalado. Para cerrar
la incidencia se requiere además comprobar suspensión/reanudación física,
enumeración PCSC y reconexión con una sesión nueva. Nunca reintentar firmas
pendientes automáticamente después de reanudar.

Referencias de diseño:
- [EvtIoStop y propiedad de solicitudes](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdfio/nc-wdfio-evt_wdf_io_queue_io_stop).
- [Colas gestionadas por energía](https://learn.microsoft.com/en-us/windows-hardware/drivers/wdf/using-power-managed-i-o-queues).
- [Purge síncrono](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdfio/nf-wdfio-wdfioqueuepurgesynchronously).
- [Directivas INF y aislamiento de host](https://learn.microsoft.com/en-us/windows-hardware/drivers/wdf/specifying-wdf-directives-in-inf-files).
