# Puente Windows ↔ Android: prototipo TLS

Actualización: el usuario descarta cambios del firewall/configuración global. Se añadió `serve-local`, que usa un canal privado propio y omite completamente Bix. [Ruta nueva y límites](PUENTE_LOCAL_SIN_CAMBIOS_WINDOWS.md). Las referencias siguientes a `serve` corresponden a la ruta VPCD anterior, que conserva su bloqueo; no describen el backend privado.

Este prototipo conserva las APDU en claro únicamente dentro del equipo Windows y del motor Android. El enlace inalámbrico usa TLS 1.2 o 1.3, certificado fijado por SHA-256 y una credencial aleatoria de emparejamiento de 32 bytes. No registra contenidos de APDU, PIN, CAN, ATR ni datos personales. El transporte no interpreta ni inventa respuestas de la tarjeta.

Código propio: `tools/windows-bridge`. Consola .NET 8, sin paquetes NuGet externos. `NuGet.Config` elimina fuentes de paquetes para restaurar únicamente referencias incluidas con el SDK. No se ha incorporado código de vsmartcard.

## Alcance y límites

Windows expone TLS en una única dirección local explícita; el valor predeterminado es `127.0.0.1`, no todas las interfaces. Una IP LAN debe elegirse mediante `--host`. No instala controladores, no cambia el firewall ni ejecuta un flujo RENIEC. El proceso atiende una sola conexión y finaliza al cerrarse esa sesión. Una conexión no autenticada consume esa ejecución: se debe reiniciar; esta decisión reduce superficie y todavía no ofrece protección contra denegación de servicio.

Después de autenticar Android conecta como cliente a `127.0.0.1:35963`, donde está el lector Bix existente. La desconexión Android, un error de trama o una cancelación cierran ese cliente y retiran su tarjeta virtual. No hay inserción mediante ATR ficticio ni autenticación simulada.

**Este no es todavía un puente desplegado para Ciudadano Digital.** Android introduce inicialmente un motor de lectura limitado, con cierre ante OFF/RESET en lugar de simular un reinicio físico sobre una sesión PACE. La enumeración Windows puede producir esos controles antes de una operación útil. Falta resolver y probar el ciclo de reconexión real antes de afirmar compatibilidad con el flujo oficial.

El lector Bix instalado previamente permanece como dependencia externa; no se declara una pila íntegramente MIT. El puerto existente de Bix escucha en `0.0.0.0:35963`. Aunque nuestro cliente lo use por loopback, **eso no evita conexiones LAN directas que eludan TLS**. Por eso `serve` verifica los listeners antes de abrir TLS y **rechaza ejecutar el puente mientras ese puerto no escuche exclusivamente en loopback**. Rechaza también la ausencia de listener. No existe una excepción insegura ni una aceptación basada solo en una afirmación de firewall. El propietario deberá resolver el aislamiento del controlador primero; este prototipo no cambia su configuración ni reglas del sistema. La comprobación es puntual, no monitorea reconfiguraciones posteriores del controlador. Tampoco cifra memoria, tráfico local del lector ni el canal entre las aplicaciones oficiales y sus bibliotecas PC/SC.

## Generación y ejecución voluntaria

Desde la carpeta del fork:

```powershell
dotnet restore tools/windows-bridge/WindowsBridge.csproj --configfile tools/windows-bridge/NuGet.Config
dotnet run --project tools/windows-bridge --no-restore -- doctor
dotnet run --project tools/windows-bridge --no-restore -- setup --output "C:\Users\LENOVO\AppData\Local\DNIeTools\bridge-pairing-01" --host 192.168.1.5 --port 39443
dotnet run --project tools/windows-bridge --no-restore -- serve --config "C:\Users\LENOVO\AppData\Local\DNIeTools\bridge-pairing-01\server-private.json"
```

Las rutas e IP son ejemplos y deben ajustarse. `setup` exige una carpeta nueva, restringe sus ACL al usuario actual y SYSTEM antes de generar secretos, crea un certificado autofirmado RSA 3072 válido por 90 días y produce:

- `bridge.pfx`: certificado y clave privada, protegido por contraseña aleatoria.
- `server-private.json`: configuración exclusiva del host, contraseña PFX y token. No transferir a Android.
- `android-provision.json`: IP, puerto, SHA-256 del certificado DER y token Base64. Es una credencial: importarla únicamente por un canal de confianza, nunca publicarla ni registrarla.

El token debe permanecer privado. Quien posea esta credencial puede autenticarse como Android ante el host; TLS no pide certificado cliente. La fijación del certificado autentica el host para Android. El usuario aún debe autorizar el acceso a su DNI en Android. La renovación o revocación inicial consiste en crear otra carpeta de aprovisionamiento y dejar de ejecutar la configuración anterior; no existe todavía un gestor de dispositivos autorizados ni códigos cortos para emparejar.

`serve` rechaza certificados vencidos. Schannel de Windows exige importar la clave en un contenedor temporal del usuario, que .NET elimina al liberar el certificado; no se usa `PersistKeySet` ni se instala el certificado en el almacén de confianza. Una terminación abrupta del sistema puede dejar restos que deben considerarse al endurecer el host. `Ctrl+C` cancela handshake, lectura y conexión Bix. Los errores impresos son genéricos para evitar filtrar datos; no constituyen una auditoría completa.

## Contrato de protocolo versión 1

1. Android establece TLS 1.2/1.3 y verifica SHA-256 del certificado hoja DER contra `certificateSha256` importado. No aceptar certificados arbitrarios ni rebajar a TCP abierto.
2. Android envía exactamente 40 bytes: ocho ASCII `DNIBRDG1` seguidos de los 32 bytes decodificados de `pairingTokenBase64`.
3. Windows compara el token en tiempo constante. Solo responde `01` al autenticar; en fallo cierra la conexión. Handshake y preámbulo tienen límite conjunto de 15 segundos.
4. Windows conecta Bix por loopback con un plazo de cinco segundos. Desde ese punto ambos extremos intercambian tramas VPCD: longitud de dos bytes, big-endian, de 1 a 65535, seguida del contenido completo.
5. Windows → Android transporta controles o APDU. Android → Windows transporta ATR o R-APDU cuando corresponda. Los controles `00` OFF, `01` ON y `02` RESET no producen respuesta VPCD por sí mismos; `04` pide ATR. El servidor no genera ninguna de estas respuestas.
6. La longitud cero, la interrupción de una trama o el vencimiento del plazo cierran ambas direcciones. Cada trama completa, incluyendo lectura, escritura y espera inactiva, tiene 120 segundos por defecto; la configuración admite de 5 a 300. Todo buffer de contenido se borra al terminar la trama.

JSON Android:

```json
{
  "version": 1,
  "host": "192.168.1.5",
  "port": 39443,
  "certificateSha256": "SHA256_HEX_DEL_CERTIFICADO_DER",
  "pairingTokenBase64": "TOKEN_BASE64_PRIVADO",
  "expiresAt": "FECHA_UTC_DE_EXPIRACION"
}
```

## Verificación automática

```powershell
dotnet restore tools/windows-bridge/tests/WindowsBridge.Tests.csproj --configfile tools/windows-bridge/NuGet.Config
dotnet run --project tools/windows-bridge/tests --configuration Release --no-restore
```

El ejecutable de pruebas usa certificados de corta duración y puertos loopback aleatorios, bytes sintéticos y un receptor VPCD de prueba. No toca Bix ni el DNI, no crea aprovisionamiento persistente ni abre puertos inalámbricos. Cubre aislamiento del controlador (incluyendo rechazo de wildcard y LAN), pin incorrecto, token incorrecto sin conexión al controlador, autenticación fragmentada, controles sin respuestas inventadas, ATR fragmentado, tamaño máximo, trama vacía, contenido truncado, plazos y retirada de tarjeta al desconectarse Android.

Verificación realizada en este host: compilación Release y **16 comprobaciones aprobadas**. Se usó `UseAppHost=false` para evitar una incompatibilidad de creación del ejecutable nativo con la unidad sincronizada G:; se ejecuta mediante `dotnet run` o `dotnet WindowsBridge.dll`. No se abrió una sesión con el lector Bix ni con una tarjeta real.

### Fixture de interoperabilidad Kotlin/JVM

El ejecutable de pruebas admite `--interop-fixture <ruta-json>` únicamente para pruebas; no existe esa opción en el CLI de producción. Exige una carpeta nueva, restringe ACL, crea un certificado y token temporales y publica un JSON compatible con Android. Tanto el listener TLS como el receptor VPCD sintético usan puertos loopback aleatorios. La señal `INTEROP_FIXTURE_READY` indica que el cliente Kotlin puede leer ese archivo, fijar el certificado y autenticarse usando `PinnedBridgeClient`.

Desde el receptor de prueba se envían un pedido de ATR, SELECT `CE81`, READ BINARY de tres bytes y un VERIFY que debe rechazarse. El cliente usa una tarjeta simulada y devuelve ATR `3B80800101`, `9000`, `0102039000` y `6985`, respectivamente. Se verifica la retirada de la sesión al cerrar el receptor. No interviene un DNI, NFC ni Bix. La sesión tiene un plazo máximo de 60 segundos y el archivo con la credencial se elimina al finalizar, incluso ante error. La carpeta vacía puede permanecer para preservar el registro de que fue usada.

**Interoperabilidad ejecutada satisfactoriamente:** el cliente Kotlin/JVM real se autenticó contra `BridgeSession` .NET, las cuatro respuestas coincidieron y se cerró la sesión. El aprovisionamiento temporal se creó bajo `%LOCALAPPDATA%\DNIeTools` en NTFS, con ACL restringida, y se eliminó al terminar. La unidad G: sincronizada no permitió imponer ACL y se rechazó antes de escribir credenciales; no debe usarse para aprovisionamientos privados. Esta prueba demuestra compatibilidad de transporte y política sobre bytes simulados, no compatibilidad RENIEC ni seguridad de un controlador virtual instalado.

## Investigación del aislamiento Bix

Se inspeccionó la fuente pública de vsmartcard en el commit `8a411e3672e843f9bb9fd750fc8dc56a26bb3bc8`. En [VpcdReader.cpp](https://github.com/frankmorgner/vsmartcard/blob/8a411e3672e843f9bb9fd750fc8dc56a26bb3bc8/virtualsmartcard/win32/BixVReader/VpcdReader.cpp), `init` lee exclusivamente `RPC_PORT_BASE` y `TCP_PORT` para la conexión, y `startServer` llama a `vicc_init` sin hostname. En [vpcd.c](https://github.com/frankmorgner/vsmartcard/blob/8a411e3672e843f9bb9fd750fc8dc56a26bb3bc8/virtualsmartcard/src/vpcd/vpcd.c), ese caso crea un servidor IPv4 ligado a `INADDR_ANY`. Por lo tanto **añadir un supuesto `TCP_HOST`, `BIND_ADDRESS` o `HOST=127.0.0.1` al INI no resuelve el problema** en esta implementación.

La configuración existente usa `RPC_TYPE=2`, lector VPCD. Hay otros modos en el código fuente, pero no son alternativas que puedan activarse desde nuestra aplicación: requerirían modificar y reiniciar el controlador y un adaptador de protocolo diferente. El modo named pipe existente permite usuarios autenticados y no declara rechazo explícito de clientes de red en su creación; tampoco debe asumirse automáticamente seguro. No se cambió ningún modo, puerto ni controlador.

`doctor` consulta los listeners y las direcciones IPv4 locales sin conectar a Bix ni leer secretos. Imprime el estado de aislamiento y candidatos para `--host`, devuelve código 2 ante bloqueo y 0 cuando el listener es exclusivamente loopback. Permite preparar `setup` e importar el JSON en Android mientras se resuelve el backend local, pero no habilita `serve` con un controlador expuesto.

Opciones posteriores que requieren trabajo específico:

- Backend Windows propio que cree su canal local con ACL del usuario y rechace clientes remotos, con una licencia permisiva verificada. No existe todavía ese controlador en este repositorio.
- Variante externa del lector que incorpore realmente bind loopback, instalada y verificada por su propietario. Modificar fuente GPL del controlador conserva sus obligaciones GPL; no copiarla al motor propio.
- Aislamiento mediante firewall administrado podría proteger el listener wildcard, pero el prototipo actual **no contiene un verificador de reglas efectivas** y no acepta esa excepción. Antes de admitirla se necesitaría demostrar cobertura de todas las interfaces/perfiles y monitorear cambios, en vez de un interruptor que omita seguridad.

## Lector PC/SC global: investigación del entorno y ruta concreta

El endpoint privado de sesión permite entregar APDU a un consumidor local aprobado, pero **no registra por sí mismo un lector en el administrador de tarjetas Windows**. No debe presentarse una named pipe como si `SCardListReaders` fuera a enumerarla automáticamente.

[SCardIntroduceReader](https://learn.microsoft.com/en-us/windows/win32/api/winscard/nf-winscard-scardintroducereaderw) introduce un nombre para un lector existente; Microsoft lo documenta como una operación de base de datos y normalmente un cambio de nombre. Requiere el nombre de un dispositivo lector que ya existe. No acepta callbacks, sockets ni un objeto .NET para ejecutar APDU. No se llamó a esta función ni se crearon alias ficticios durante la investigación.

### Inventario verificado, 5 de octubre de 2026

- Bix está presente: `ROOT\SMARTCARDREADER\0000`, estado PnP OK, versión de paquete `1.13.48.938`, INF `oem209.inf`; la consulta `Win32_PnPSignedDriver` devuelve `IsSigned=false`. Esto describe el paquete instalado, no explica por sí solo la política que permite cargarlo.
- El INF existente declara clase SmartCardReader, `UmdfLibraryVersion=1.11.0`, servicio Bix y WUDFRd. **Es UMDF 1**, no un ejemplo UMDF 2 reutilizable sin adaptación.
- No se encontró Windows Kits 10 en su ruta estándar ni claves Installed Roots de SDK/WDK, Visual Studio Installer/vswhere, MSVC `cl.exe`, MSBuild C++ ni CMake en PATH. El inventario de programas instalados únicamente mostró Visual Studio 2010 Tools for Office Runtime, que no es un compilador C++.
- .NET SDK 8 sí está disponible y compila el host; su MSBuild no sustituye al compilador ni a los targets WDK.
- Hay un JDK con `javac` en `D:\G\ANDROID\jbr\bin`. Permite compilar un proveedor Java de terminales en este entorno, pero no un controlador Windows.

Se inspeccionaron rutas estándar, registro e inventario, y las ubicaciones Android conocidas; no se afirma haber recorrido todas las carpetas del disco para descartar una instalación portátil desconocida. No se instaló software ni se alteró el registro, PnP, INI, firewall o política de firma.

### Licencias primarias y herramientas

El [repositorio Windows-Driver-Frameworks](https://github.com/microsoft/Windows-Driver-Frameworks/blob/master/LICENSE) declara MIT. Es el código del framework y **no proporciona automáticamente un lector virtual PC/SC completo**. El [repositorio Windows-driver-samples](https://github.com/microsoft/Windows-driver-samples/blob/main/LICENSE) declara **Microsoft Public License (MS-PL)**: no se debe etiquetar como MIT. Los ejemplos de [Windows.Devices.SmartCards](https://github.com/microsoft/Windows-universal-samples/tree/main/Samples/SmartCard) consumen lectores o crean tarjetas virtuales de Windows; no transforman un endpoint APDU remoto en un lector arbitrario.

Para un lector global propio, la ruta propuesta es **UMDF 2 original**, con `GUID_DEVINTERFACE_SMARTCARD_READER`, IOCTL de presencia/ausencia, estado, atributos, protocolo, power/reset y transmisión; su backend consume el canal local privado del host y jamás abre una escucha de APDU TCP. La retirada Android debe completar las esperas de presencia/ausencia y cancelar transmisiones; el controlador no debe inventar ATR o `9000` cuando no hay tarjeta. Se deben respetar tamaños de buffer, semántica de `SCARD_IO_REQUEST`, exclusividad/transacciones y cancelación, antes de evaluar aplicaciones oficiales. Véanse la [guía de dispositivos lectores](https://learn.microsoft.com/en-us/windows-hardware/drivers/smartcard/) y la [interacción con la biblioteca smart card](https://learn.microsoft.com/en-us/windows-hardware/drivers/smartcard/interaction-with-the-smart-card-driver-library). La implementación precisa UMDF 2 requiere contrastar los headers/targets del WDK; no se afirma que los helpers WDM de kernel sean utilizables sin cambios en UMDF.

La opción de compilación menos invasiva es **Enterprise WDK (EWDK)**: Microsoft ofrece un entorno autocontenido de línea de comandos con Build Tools, SDK y WDK. Su [documentación](https://learn.microsoft.com/en-us/windows-hardware/drivers/download-the-wdk) describe montar la ISO local y ejecutar `LaunchBuildEnv.cmd`/`SetupVSEnv`; no exige instalar Visual Studio completo. La compilación futura sería `msbuild <proyecto-original>.vcxproj /p:Configuration=Release /p:Platform=x64` en ese entorno. **Aquí no está descargado ni montado**, y todavía no existe un proyecto lector listo para ese comando. Adquirir el toolchain, crear el proyecto, producir DLL/INF/CAT y validar los IOCTL son pasos pendientes, no una compilación hecha.

Instalar un lector global implica PnP y un paquete controlador que respete las [reglas de instalación y firma](https://learn.microsoft.com/en-us/windows-hardware/drivers/install/windows-driver-signing-tutorial). Las reglas para UMDF y kernel son distintas; no se deduce que un DLL UMDF permita evitar firma, registro o autorización de instalación. La [firma por attestation](https://learn.microsoft.com/en-us/windows-hardware/drivers/dashboard/driver-signing-offerings) es una vía de pruebas con requisitos propios, incluido certificado EV para envío, y no equivale a certificación HLK de producción. No se propone desactivar Secure Boot ni integridad, cambiar testsigning o instalar certificados de prueba en la PC de uso habitual.

### Trabajo implementable ahora, sin modificaciones globales

Podemos desarrollar y probar en .NET el contrato del backend local y el modelo de estados/IOCTL con consumidores simulados. Esto prepara el lector UMDF, pero aún no lo hace aparecer en `SCardListReaders`. Cuando esté disponible EWDK, se conecta ese contrato al dispositivo UMDF 2 y se prueba primero en un equipo de laboratorio con un paquete correctamente preparado.

Como alternativa acotada, es posible crear un proveedor Java mediante [TerminalFactorySpi](https://docs.oracle.com/en/java/javase/17/docs/api/java.smartcardio/javax/smartcardio/TerminalFactorySpi.html), `CardTerminals`, `CardTerminal`, `Card` y `CardChannel`. Puede presentar el lector dentro de **un proceso Java que incorpore explícitamente ese proveedor**, con el JDK ya disponible. Se inicializa antes de `TerminalFactory.getDefault`; el [tipo predeterminado](https://docs.oracle.com/en/java/javase/17/docs/api/java.smartcardio/javax/smartcardio/TerminalFactory.html) puede seleccionarse por propiedad del proceso. **No lo verán programas nativos que consulten winscard, CAPI/KSP ni Java que use expresamente el proveedor PC/SC del sistema.** No se propone inyectarlo, reemplazar DLL del sistema o modificar ClickOnce de RENIEC para aparentar compatibilidad global.

Para la prioridad expresada de un lector Windows universal, el plan debe continuar por UMDF 2; el proveedor por proceso solo sirve como banco de prueba adicional. Reutilizar Bix sin cambiar su configuración conserva el bloqueo de aislamiento ya documentado. No existe una llamada .NET o `SCardIntroduceReader` que sustituya la pieza PnP ausente sin introducir un controlador o cambiar el consumidor.
