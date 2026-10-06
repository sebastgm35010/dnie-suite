# Puente con comunicación privada y sin configuración global de Windows

El usuario descarta cambiar firewall y configuración global de Windows. Se retiraron el verificador/instalador de reglas que estaban en desarrollo. **No se aplicó ninguna regla ni se modificó Bix, el registro, servicios o políticas de controladores.** El código vigente no instala ni ejecuta esas modificaciones.

## Ruta implementada en el host

`serve-local` conserva el TLS con certificado fijado y token de Android, pero conecta con un canal privado propio, sin pasar por TCP 35963 ni por Bix. Este modo exige que el listener TLS esté en loopback. No usa un listener LAN para el canal de escritorio.

El canal privado es un named pipe creado por sesión: una instancia, nombre aleatorio, ACL exclusiva del usuario actual y `PIPE_REJECT_REMOTE_CLIENTS`. `FILE_FLAG_FIRST_PIPE_INSTANCE` impide asumir silenciosamente un nombre preexistente. Los flags y las APIs están documentados por [Microsoft](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-createnamedpipea). La ACL pertenece al objeto temporal de nuestra app; no cambia permisos globales ni configuración de Windows.

El endpoint local se crea solo después de autenticar TLS/token. La tarjeta física continúa perteneciendo a Android; nuestro host transporta las tramas sin inventar ATR, respuestas ni verificaciones. Desconectar el cliente local cierra TLS y la sesión NFC. Cancelar el host interrumpe la espera del proveedor local. El perfil Android sigue siendo de lectura de certificados, con cierre ante OFF/RESET.

Para el enlace inalámbrico inicial puede usarse la depuración ADB inalámbrica ya autorizada y un reverse temporal:

```powershell
adb -s <dispositivo-conectado> reverse tcp:39443 tcp:39443
```

Android conecta entonces a `127.0.0.1:39443`; ADB transporta esa conexión hasta localhost de la PC. El TLS de nuestra app permanece de extremo a extremo entre Android y el host y verifica el certificado antes de enviar la credencial, independientemente del túnel. Es una asociación temporal del transporte ADB, no una regla de firewall. La sesión ADB debe estar ya conectada y autorizada. En esta entrega no se ejecutó ese reverse: el dispositivo aparecía offline.

```powershell
dotnet tools/windows-bridge/bin/Release/net8.0/WindowsBridge.dll setup --output "C:\Users\LENOVO\AppData\Local\DNIeTools\bridge-local-01" --host 127.0.0.1 --port 39443
dotnet tools/windows-bridge/bin/Release/net8.0/WindowsBridge.dll serve-local --config "C:\Users\LENOVO\AppData\Local\DNIeTools\bridge-local-01\server-private.json"
```

Solo el `android-provision.json` se importa en Android por un canal de confianza. No transferir el PFX ni `server-private.json`. El archivo continúa siendo el mecanismo provisional de emparejamiento; QR y entrada manual todavía no están implementados para este puente. La propuesta es QR con material de vinculación de alta entropía y vigencia corta, seguido de confirmación de ambos equipos. Un PIN corto no debe sustituir directamente el token aleatorio.

## Lo que este cambio no implementa

Un named pipe no se registra como lector PC/SC y una app oficial no lo descubre automáticamente. Falta un adaptador que consuma este canal privado. Las aplicaciones que admiten PKCS#11 o un proveedor propio ofrecen una ruta de integración por aplicación; el Java del middleware es candidato a un proveedor `javax.smartcardio` cargado exclusivamente en ese proceso. **No está desarrollado ni probado ese proveedor y no se afirma que sustituya el chequeo PC/SC del lanzador .NET.**

La arquitectura estándar de Windows conecta las aplicaciones PC/SC con el administrador de recursos y controladores de lectores; véase [arquitectura de tarjetas inteligentes](https://learn.microsoft.com/en-us/windows/security/identity-protection/smart-cards/smart-card-architecture). Crear compatibilidad universal para apps que solo acepten lectores del sistema requiere un lector/proveedor apropiado, que no se obtiene por añadir TLS o un pipe. Esa ruta deberá discutirse de forma independiente, conservando la preferencia del usuario de evitar cambios globales.

TLS termina en el host propio. Dentro de Android y de los procesos Windows autorizados existen APDU/datos en memoria en claro, según requiere su procesamiento. El canal local restringe acceso por usuario y excluye clientes remotos, pero no autentica todavía una aplicación específica de ese mismo usuario. La autorización por proceso/operación sigue pendiente.

## Verificación realizada

Pruebas Windows con certificado temporal y bytes sintéticos: **25 comprobaciones aprobadas**. Incluyen el transporte TLS existente, la ACL real del pipe (una ACE del usuario actual), conexión local, rechazo de colisión de nombre, cancelación, transporte bidireccional entre pipe y TLS, cierre por desconexión y ausencia de creación del endpoint con token incorrecto.

Se compiló en Release sin dependencias NuGet externas. No se usaron DNI, Bix, lector físico ni reglas de firewall en esas pruebas. El rechazo remoto se configura en la llamada Win32; no se ensayó una conexión SMB desde otro equipo. No hubo cambios de Android ni APK nuevo en esta entrega.
