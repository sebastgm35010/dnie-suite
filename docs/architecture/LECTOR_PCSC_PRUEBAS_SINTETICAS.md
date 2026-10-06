# Lector PC/SC: firma e instalación de pruebas, 5 de octubre de 2026

Estado comprobado: controlador original UMDF 2.15 x64 firmado con certificado exclusivo de desarrollo e instalado en Windows 11. No se utilizó un DNI, NFC, TLS ni el transporte de Bix en estas pruebas.

## Evidencia

- Hardware ID: `Root\DNIeBridgeReader`; nodo real creado por DevCon: `ROOT\SMARTCARDREADER\0001`.
- Estado PnP: OK. Bix permanece OK en `ROOT\SMARTCARDREADER\0000`.
- Nombre real obtenido con `SCardListReaders`: `DNIe Bridge Android NFC Private Reader 0`.
- DriverVer: `10/05/2026,2.0.0.2`; compilación x64 sin errores ni advertencias; INF aprobado por InfVerif `/w`.
- Paquete firmado: `D:\DNIeToolchain\ReaderTestPackage`. Artefactos sin firma originales: `D:\DNIeToolchain\ReaderBuild`.
- 25 comprobaciones del controlador instalado aprobadas, registradas en `build/pcsc-deployment/reader-synthetic-result.json`.
- 45 comprobaciones del puente .NET aprobadas después de la instalación.
- C: conserva aproximadamente 13,4 GiB libres. Herramientas, paquete y binarios de pruebas están en D:; Windows conserva la copia instalada en DriverStore.

## Firma y cambios de confianza autorizados

Certificado `CN=DNIe Bridge Local Development Only`, RSA 3072, SHA256, uso Code Signing, clave no exportable. Huella: `821F6725626C6944169C77F319FB83659A8C43D7`. Vencimiento: `2027-01-04T01:45:41Z`. Clave en LocalMachine/My; certificado público en LocalMachine/Root y LocalMachine/TrustedPublisher. No se utilizó el certificado personal del DNI. La confianza es local y exclusiva de pruebas; no equivale a firma de distribución.

El paquete se validó con InfVerif y con SignTool `/pa /c` para confirmar confianza y pertenencia del INF/DLL al catálogo. `/kp` se retiró del instalador porque el paquete revisado contiene una DLL UMDF y reutiliza el reflector WUDFRd de Windows, sin incorporar un controlador kernel propio. No se modificaron Secure Boot, BCDEdit, firewall ni políticas generales de integridad.

Inf2Cat tiene una firma interna de Microsoft cuya raíz no es pública. Su procedencia se validó comparando el SHA256 del ejecutable instalado con la entrada del paquete oficial WDK NuGet, después de verificar las firmas de ese paquete. No se importó la raíz interna de Microsoft.

## Fallos detectados y corregidos por la instalación real

1. La creación del temporizador periódico con ejecución pasiva era incompatible con WDF. Se utiliza un temporizador de una ejecución que se rearma mientras el dispositivo está en D0.
2. Se completaron Include/Needs de WUDFRD.inf en las secciones de instalación y hardware, además de Services.
3. El servicio SCardSvr corre como LocalService. El permiso SYSTEM inicial impedía enumerar el lector. Se autorizó lectura/escritura solamente a su SID de servicio específico; no a todos los procesos LocalService.
4. El protocolo soportado del lector es independiente de si hay tarjeta; ICC_PRESENCE usa 2 para una tarjeta insertada sin mecanismo de captura.
5. La identificación del nodo usa HardwareIds y no presupone que el InstanceId coincida con el ID del INF.

## Prueba sintética de comportamiento

El ejecutable de pruebas corre como SYSTEM por la ACL inicial del backend. Se copia a una carpeta protegida y se inicia mediante una tarea temporal con plazo de ejecución, eliminada después. No se instaló un servicio SYSTEM del puente.

La tarjeta simulada tiene ATR `3B800181` y T=1. Se comprobó: enumeración PC/SC, ausencia inicial, inserción, rechazo de un segundo propietario, rechazo de lectura de solicitudes por un handle distinto, sondeo vacío, conexión y negociación T=1, transmisión y conservación exacta de respuestas `6A82` y `DEAD9000`, warm reset, power off, retirada al cerrar el propietario y cancelación de una espera PC/SC. La retirada se espera desde el último estado conocido; una consulta UNaware inmediata puede devolver el estado en caché antes de la notificación asíncrona.

## Pendientes y retirada

La simulación confirma el camino PC/SC → UMDF → IOCTL → backend. Falta validar cancelación de APDU pendiente, timeout de 30 segundos y carreras repetidas bajo herramientas WDF; no se afirma cobertura de esos escenarios. Falta implementar y probar reset físico/reapertura PACE en Android: el perfil NFC actual sigue rechazando reset/apagado y retirando sesión. También falta autorización mínima del backend interactivo y compatibilidad real con los perfiles APDU de RENIEC.

Para retirar la instalación de pruebas, ejecutar como administrador `tools/windows-pcsc-driver/remove-test-installation.ps1`. Identifica únicamente nuestro hardware ID y la huella exacta de desarrollo; retira el paquete actualmente asociado y los tres registros del certificado, incluida su clave. No cambia Bix. Versiones sustituidas del paquete pueden seguir en DriverStore y requieren una revisión adicional antes de eliminarlas. El helper de retirada no se ejecutó durante esta entrega.
