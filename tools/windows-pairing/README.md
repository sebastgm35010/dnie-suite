# Flujo actual 2.0.0-rc2

El gestor Windows permite Tailscale, LAN TLS y USB TLS mediante Compartir Internet por USB. LAN/USB usan una IPv4 privada concreta de la PC y QR, sin depuración ni ADB. `network-session.ps1` genera el emparejamiento e inicia el backend temporal; no modifica firewall. La conexión por cable es red USB, no un accesorio USB nativo. El flujo `pair-phone.ps1` que se describe abajo se conserva como herramienta histórica de desarrollo y queda fuera del paquete de producción.

Consulta [transportes y pruebas](../../docs/architecture/TRANSPORTES_Y_DEBUG_2_0_RC2.md) y `tools/windows-desktop/package-desktop.ps1 -Configuration Release` para generar un paquete sin herramientas debug. Las limitaciones antiguas de loopback/ADB que aparecen debajo ya no describen el flujo principal del gestor.

## Flujo historico

# Emparejamiento móvil mediante QR

Ejecutar `emparejar-pc.bat` en la raíz del fork. Compila la presentación Windows separada del motor, crea una carpeta NTFS privada nueva en D:, genera un QR local, prepara ADB reverse si hay un teléfono autorizado conectado y abre la página local. Desde 1.8.2 inicia por defecto el host con backend PC/SC integrado. Windows solicita elevación para la sesión temporal SYSTEM porque la interfaz privada del controlador instalado exige esa identidad; no se amplía su ACL ni se modifica el firewall. `-AdbDevice <serial>` selecciona el teléfono si hay varios; `-NoOpen -NoServer` genera solamente el QR y `-TransportOnly` conserva el modo anterior sin backend.

En Android 1.8.2 abrir Puente NFC · PC → Escanear QR de la PC y comparar la huella. Se conserva la importación JSON como alternativa. El QR no contiene CAN, PIN, certificado personal ni claves privadas. Incluye IP/puerto, pin SHA256 del certificado TLS, token aleatorio de 32 bytes y vencimiento de diez minutos. Es una credencial de acceso temporal: la página permanece en la carpeta privada y no tiene recursos remotos. Ocultarlo en pantalla no borra el archivo ni revoca el token antes del vencimiento.

Formato: `DNIEBRIDGE1:` seguido de JSON UTF8 codificado como Base64URL sin padding. El lector Android limita tamaño, exige el prefijo y rechaza QR vencidos o con vigencia restante superior a 15 minutos. FIDO QR es un flujo diferente y no se modifica.

El servidor comprueba el vencimiento al arrancar, limita la espera de conexión y lo vuelve a comprobar después de autenticar el token, antes de abrir el endpoint local. Una sesión ya autorizada no se corta porque venza el QR. Cada ejecución del servidor atiende una sola sesión.

El host se liga únicamente a loopback; para usar Wi-Fi sin modificar firewall se mantiene el túnel ADB inalámbrico autorizado. Si no hay dispositivo ADB conectado, el launcher lo informa y el QR aún sirve para configurar la app, pero no establece una ruta de red. No abre el puerto TLS a la LAN ni activa depuración del teléfono.

El host privilegiado se copia a `D:\DNIeBridgePrivileged` con propiedad Administradores, escritura Administradores/SYSTEM y lectura del usuario. La tarea temporal SYSTEM no tiene disparador persistente y se elimina tras iniciar; el proceso limita espera por QR y sesión a plazos finitos. Registros `host.log` y `host-error.log` en la carpeta de esa sesión. No se ejecutan DLL editables por el usuario como SYSTEM.

El backend integrado solicita ATR al móvil después de autenticarlo y entonces marca presencia en el lector nativo. Los pulsos cada diez segundos mantienen el transporte sin APDU NFC. Reset devuelve el ATR solo después de cerrar el canal anterior y reconstruir PACE; power-off retira ese canal hasta un nuevo reset. Esta adaptación IsoDep no acredita un reset eléctrico de contacto ni compatibilidad completa del Java de RENIEC: todavía requiere DNI real. La sesión aprobada puede durar hasta treinta minutos, incluso si vence el QR, y se retira al perder transporte.

`reader-status.ps1` verifica que el lector figure primero, como requiere cddnie. No deshabilita otros lectores. La prueba sintética integrada pasó 13 comprobaciones reales PC/SC/TLS, incluyendo 35 segundos de espera, warm reset, power-off y retirada. Detalles en `docs/architecture/SESION_PCSC_1_8_2.md`.

Dependencia de presentación: [QRCoder 1.7.0](https://github.com/Shane32/QRCoder), licencia MIT; DLL separada del motor Windows. System.Drawing.Common se fija a 8.0.31 por la dependencia transitiva del paquete; el renderizador utilizado es PngByteQRCode. Caché NuGet en D:. No se utiliza un generador QR externo ni se envía el emparejamiento a un servicio web.
