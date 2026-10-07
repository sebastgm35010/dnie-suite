# Corrección de descubrimiento USB AOA

Fecha: 2026-10-06. Android `2.0.0-rc4-usb` (52), Windows gestor/servicio Release actualizado.

## Causa y cambios

Android declaraba soporte de accesorios, pero no registraba la acción USB_ACCESSORY_ATTACHED con su filtro de fabricante/modelo. Windows anunciaba una URL de documentación como URI del accesorio; ante la falta de aplicación compatible, Android ofrecía esa página.

Se añadió una actividad de entrada exportada y limitada al descubrimiento: valida identidad y accesorio actualmente conectado y abre la pantalla privada del puente. No autoriza una PC ni inicia NFC automáticamente. El fabricante/modelo coincide con Windows; el filtro no depende de la versión. Referencia: [documentación Android USB accessory](https://developer.android.com/develop/connectivity/usb/accessory).

El permiso USB conserva PendingIntent inmutable y comprueba el permiso real de UsbManager; comunica denegación/desconexión en lugar de depender de extras modificables. La identidad está separada del adaptador de transporte.

Windows deja vacía la URI documental, espera hasta 20 segundos la reenumeración del accesorio con cancelación y muestra las etapas de USB. La espera no repite START_ACCESSORY. El gestor descarta resultados de consultas anteriores cuando hay una acción en curso, evitando sobrescribir el estado de la operación. Una IOException causada por cancelar E/S al detener deja el host detenido sin atribuir un fallo espurio; los errores no asociados a cancelación se mantienen.

TLS, identidad persistente de PC, protocolo PC/SC y motor NFC/PACE no se modificaron. No se cambiaron firewall, servicios de tarjetas ni minidriver.

## Validación

- Motor Windows: 127 comprobaciones; control Release: 32, todas satisfactorias.
- Android: 68 pruebas app Debug, 57 app Release; adaptador Android del puente 28 pruebas, dos omisiones opcionales. Sin fallos ni errores.
- APK Debug y Release compilados; paquete Windows Release compilado sin advertencias ni errores. APK Debug instalado conservando datos; servicio Release actualizado mediante instalador nativo desde terminal administrativa.
- Comprobaciones de arquitectura y contrato del filtro USB satisfactorias. PackageManager del teléfono encuentra la actividad USB de la app instalada.
- Ensayo físico sin DNI: Windows detectó el teléfono, cambió a accesorio y llegó a `usb_waiting_android`, sin tarjeta publicada. El usuario confirmó que Android ofrece abrir DNIe Evolución. La parada devolvió estado Stopped y el servicio permaneció activo. Esto confirma el descubrimiento real; no acredita todavía TLS, lectura o firma USB. El ajuste posterior del error espurio de cancelación cuenta con una prueba sintética; falta repetir esa parada física con el paquete final.

## Siguiente prueba

Limitación detectada en una segunda apertura física: el host quedó en `usb_switching_accessory`; la parada agotó su espera de 25 segundos y dejó estado Stopping, sin tarjeta publicada. La llamada nativa UsbDk aún puede bloquear durante transición/redirección. El ajuste de IOException no resuelve ese bloqueo. Se solicitó parada SCM y, agotada la espera, se terminó exclusivamente el proceso del servicio propio, verificando PID y ruta bajo D:/DNIeBridgeApp/versions. Se instaló el paquete final correctamente; el broker volvió a Running con host Stopped, sin tarjeta ni error. No se tocaron WUDFHost ni servicios PC/SC. Investigar y aislar la llamada nativa, y repetir apertura/parada, antes de considerar USB estable.

Abrir gestor Windows, elegir USB y el teléfono; iniciar, aceptar abrir la app Android y usar su perfil USB/QR de primera vinculación. Autorizar NFC con CAN y presentar DNI. Comprobar lectura, firma de documento de prueba y verificación independiente, retirada/reconexión y cancelación. No reenviar firmas automáticamente ante errores.

Distribución Windows: `D:/DNIeToolchain/DesktopPackages/Native-Release-rc4-usb-final`. Evidencia y APK fuera de Git: `D:/DNIeToolchain/Checkpoints/usb-aoa-rc4`.
