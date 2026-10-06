# Puente remoto Tailscale — 1.8.10

Perfil de pruebas solicitado: Android se conecta directamente al host PC/SC por TCP, sin TLS adicional y sin ADB reverse. El tráfico usa el cifrado de Tailscale: [documentación oficial](https://tailscale.com/docs/concepts/tailscale-encryption). No depende de que el transporte de Tailscale sea P2P; un relay conserva su cifrado.

## Configuración y uso

- Laptop: `sebas-laptop.tail2752e9.ts.net`, dirección observada `100.104.106.125`.
- Teléfono autorizado: `sebas-poco.tail2752e9.ts.net`, dirección observada `100.68.70.85`.
- Puerto: TCP 39443, enlazado solo a la IP Tailscale de la laptop.
- Ejecutar `emparejar-tailscale.bat`, aceptar elevación para el backend PC/SC existente, importar `android-provision.json` o escanear el QR privado desde Puente NFC · PC.
- Para continuar el ensayo de traducción: PACE en Windows desactivado, Traducir PACE del middleware activado, CAN correcto y DNI apoyado durante la sesión. El registro APDU permanece disponible para exportar.
- El emparejamiento autoriza una sesión y vence a los diez minutos; una conexión autorizada puede durar hasta treinta minutos. No se alteró el requisito de mantener la app en primer plano y el DNI accesible.

## Implementación

`transport: tailscale_tcp` se importa explícitamente; los emparejamientos antiguos mantienen TLS. El motor no hace downgrade automático al fallar TLS. Android admite nombres MagicDNS `*.ts.net` y direcciones IPv4 100.64.0.0/10, y comprueba la IP resuelta antes de enviar el token. Windows enlaza a una IP local Tailscale y verifica que el cliente coincida con la IP del teléfono configurado antes de procesar la autenticación.

Se conservan token aleatorio de 32 bytes, preámbulo DNIBRDG1, autorización del DNI, marcos bidireccionales, PACE/SM, validación de MAC y SSC, reset y cierre. El timeout de lectura Android en este perfil aumenta a 120 segundos. La selección del transporte vive en el módulo Android y en el host; Compose solo presenta el perfil importado.

No se abren listeners públicos, no se cambian reglas del firewall ni políticas de Windows. Si el acceso remoto es bloqueado por una política existente, se diagnostica esa conexión antes de modificarla. No atribuir un eventual cierre a TLS: este perfil no negocia TLS.

## Validación y estado

APK 1.8.10-tailscale, versionCode 43: compilación offline correcta. Pruebas Android/JVM de los módulos afectados y comprobación de arquitectura aprobadas. Host Windows: 77 comprobaciones sintéticas aprobadas, incluidas autenticación rechazada sin acceso al backend, ATR, warm reset y cierre del dispositivo en TCP sin TLS; el perfil TLS anterior sigue probado.

Tailscale ping al Poco respondió mediante conexión directa en 47 ms durante la preparación. Host real SYSTEM escuchando en `100.104.106.125:39443`; lector PC/SC se adjunta después de la autorización NFC. Emparejamiento privado inicial `D:\DNIeToolchain\Pairings\401a3f83d62d4797af1f33ee9571883f`, registros del host en carpeta homónima de `D:\DNIeBridgePrivileged`. No incorporar estos archivos a Git.

Pendiente: ensayo NFC real desde otra red, siguiente comando tras DO85, compatibilidad final con IDplug/Ciudadano Digital y comportamiento ante cambio de red del teléfono. No se garantizó reconexión automática de una sesión PACE tras pérdida de transporte.

Entrega: Telegram confirmó el APK (30.171.450 bytes) y únicamente `android-provision.json` (329 bytes). Este primer emparejamiento vence el 6 de octubre de 2026 a las 09:24:25 de Bogotá; generar uno nuevo después. No se enviaron PFX, configuración privada del servidor ni registros. Instalación y ensayo físico en el teléfono quedan a cargo del usuario fuera de casa.
