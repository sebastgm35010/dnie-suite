# Gestor nativo Windows · rc4

Base preservada: `checkpoint/puente-2.0.0-rc3`. Android sigue en 2.0.0-rc3 (51), compatible con los mismos transportes y mensajes. Esta iteración reemplaza la coordinación Windows basada en ventanas BAT/PowerShell por un ejecutable WinForms no elevado y un servicio persistente SYSTEM.

## Autorización y control

El instalador nativo valida el manifiesto SHA256, copia los binarios a una carpeta administrativa versionada, inscribe un único SID y registra `DNIeBridgeAgent`. La inscripción no permite reasignar una identidad existente a otro usuario. Rechaza actualizar con un host activo. El manifiesto detecta archivos incoherentes; no sustituye una firma de editor de confianza.

El servicio arranca automáticamente con Windows pero mantiene el host detenido hasta una solicitud explícita. El propietario puede consultar/iniciar el servicio, sin permiso para eliminarlo, detenerlo por SCM o cambiar sus binarios. La GUI controla únicamente operaciones concretas del puente por pipe; no hay API de comandos de shell, instalación de controladores, lectura arbitraria de archivos ni control de otros servicios.

El pipe rechaza conexiones remotas, restringe la ACL a SYSTEM y el SID inscrito, reserva la primera instancia y verifica el SID de cada cliente mediante el token de su hilo bajo identificación. El cliente verifica el PID del servidor contra SCM antes de enviar datos. Los mensajes JSON tienen longitud limitada a 65536 bytes, plazo de lectura y confirmación explícita de recepción. Esta última evita que DisconnectNamedPipe descarte una respuesta que la GUI todavía no leyó.

La consulta nativa del TOKEN_USER evita cargar assemblies o abrir archivos durante la identificación de un cliente: un token de identificación no permite hacer accesos a recursos como un token de impersonación. Las pruebas reales desde SYSTEM detectaron ese fallo, que no aparecía cuando cliente y servidor estaban en el mismo proceso.

## Vida del motor

`HostController` serializa inicio/parada, mantiene un único host y expone estado inmutable obtenido de eventos del motor. Inicio repetido del mismo perfil es idempotente; cambiar el transporte con un host activo se rechaza. La cancelación espera el cierre del motor y retirada de tarjeta. Cerrar la GUI no cancela el host; al reabrir adopta el perfil activo del servicio.

El núcleo conserva PACE en Android, APDU, traductor PC/SC, TLS fijado a la identidad de PC y USB AOA. No modifica sus protocolos. La protección de identidad usa ACL nativa .NET; elimina el proceso icacls del flujo de identidad. El QR se renderiza localmente en memoria, sin página HTML, navegador o copia JSON en el escritorio. La huella/token persistentes se conservan al migrar desde rc3.

## Diagnóstico y límites

Release elimina la pantalla/comando debug mediante compilación condicional en ambos extremos Windows. Solo expone estado operacional; no registra APDU, PIN, CAN, payload o token. Un último fallo de control acotado puede guardarse en el estado privado como fase/tipo/assembly faltante, sin el mensaje de solicitud. Debug conserva un anillo de 100 eventos de fases sin payload.

El selector RENIEC es un adaptador opcional separado y no elevado. Conserva sus límites/alcance de proceso y dependencia Python/Frida; no pertenece al broker privilegiado. El gestor principal y el lector no necesitan ese proceso para funcionar.

No se tocaron firewall, perfiles de red, Hello ni servicios de tarjetas del sistema. No se reinició Windows. Pendientes heredados: USB real tras reinicio pedido por PnP; LAN real; nueva firma documental desde el DNI para validar el gestor en una sesión de usuario. Las pruebas de inicio/parada del servicio no usan el DNI, PIN ni un intento de firma.

Detalles de uso y construcción: [gestor Windows](../../tools/windows-manager/README.md).

Referencias primarias usadas: [ciclo de servicio SCM](https://learn.microsoft.com/en-us/windows/win32/services/service-entry-point), [verificación del PID del pipe](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-getnamedpipeserverprocessid).
