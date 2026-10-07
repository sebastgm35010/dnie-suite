# Gestor nativo Windows

Punto de entrada: `DNIeBridge.exe`, incluido en el paquete generado por este directorio. Abrirlo normalmente y usar **Instalar / reparar servicio** una vez. La instalación solicita UAC; iniciar, detener, consultar estado y generar QR después no requieren elevación. Crea el acceso **DNIe Bridge** en el escritorio del usuario instalador.

La interfaz WinForms usa un canal local restringido con `DNIeBridgeAgent`, servicio SYSTEM con inicio automático. Al arrancar Windows el servicio abre únicamente el canal de control: no publica el DNI ni abre la conexión de red hasta pulsar **Iniciar puente**. Cerrar el gestor conserva un puente iniciado; **Detener puente** cancela y espera la limpieza del motor. No hay cierre del host por inactividad. La presencia física del DNI y la autorización Android siguen siendo necesarias.

El flujo normal no usa BAT, PowerShell, tareas programadas ni ADB. El selector opcional de Ciudadano Digital continúa como adaptador Python/Frida sin elevación; se solicita aparte y solo afecta procesos RENIEC reconocidos. No es necesario para el lector PC/SC o ReFirma. Su dependencia opcional sigue en `D:/DNIeToolchain/Instrumentation/python`; no está incluida en el paquete.

## Compilar y empaquetar

Requiere .NET SDK 8 con Windows Desktop, Python 3 y los paquetes NuGet del archivo `tools/windows-pairing/NuGet.Config`. Todo resultado se escribe en D:, sin SDK/WDK nuevos ni publicaciones autocontenidas grandes:

```text
python tools/windows-manager/package-manager.py --configuration Release --output D:/DNIeToolchain/DesktopPackages/Native-Release-nuevo
```

Debug y Release son compilaciones independientes. Release no contiene `DebugForm` ni acepta `diagnostics` en el servicio, incluso si un cliente envía ese comando directamente. QRCoder 1.7.0 usa MIT y su aviso se conserva en el paquete. Se requiere .NET Desktop Runtime 8 x64 instalado. El paquete propio aún no tiene firma Authenticode de distribución pública.

## Capas

- `windows-manager`: presentación, preferencias públicas, QR en memoria, instalación y selector opcional.
- `windows-control`: contratos acotados, canal local autenticado, coordinación de un solo host, adaptadores SCM y del motor.
- `windows-agent`: ciclo de vida del servicio, propietario inscrito y estado privado.
- `windows-bridge`: protocolo, transportes, TLS y PC/SC existentes. No depende de WinForms.

Configuración privada y certificado TLS quedan en `D:/DNIeBridgeApp/state`, con ACL SYSTEM/administradores. Binarios versionados en `D:/DNIeBridgeApp/versions` permiten lectura/ejecución al usuario inscrito, sin escritura. La identidad anterior de `D:/DNIeToolchain/PcIdentities/<SID>` se migra conservando certificado, token y huella. Las preferencias de la ventana no contienen esas credenciales.

Desinstalación administrativa explícita: ejecutar `DNIeBridge.exe --uninstall` en una terminal elevada. Quita únicamente nuestro servicio; conserva identidad y versiones. No cambia firewall, Windows Hello, SCardSvr, CertPropSvc ni el controlador del DNI.

Los scripts anteriores permanecen como herramientas históricas de desarrollo. No ejecutar sus hosts simultáneamente con el servicio: ambos usan el puerto 39443. USB directo sigue pendiente de reinicio de Windows y prueba física; LAN TLS también requiere validación real. Cambiar el gestor no resuelve por sí solo esos pendientes de transporte.
