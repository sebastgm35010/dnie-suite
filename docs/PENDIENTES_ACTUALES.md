# Pendientes actuales · Windows rc4 USB / Android rc4-usb

Actualizado 2026-10-06 tras reinicio y corrección USB. Este listado sustituye los encabezados de pendientes de versiones anteriores. Diferenciar implementación, simulación y validación con dispositivo real.

## Siguiente secuencia

| Prioridad | Trabajo | Estado / criterio de cierre |
| --- | --- | --- |
| P0 | Arranque real después del reinicio | Comprobar servicio automático y lector PnP, sin host/red hasta pulsar Iniciar. Abrir dos veces el gestor y comprobar una ventana; iniciar/parar sin UAC. No asumir que la prueba previa de SCM acredita un reinicio real. |
| P0 | Identificar y retirar el listener legado | Antes del reinicio hay `0.0.0.0:35963` bajo WUDFHost. Identificar su dispositivo/controlador; retirar únicamente el componente legado si se confirma. No matar WUDFHost genéricamente, ni tocar SCardSvr/Hello. El lector propio no usa red/VPCD. Cierre: sin listener legado y lector propio operativo. |
| P0 | Regresión Tailscale con gestor nativo | Sesión física, lectura y una firma ReFirma/Firma Perú con verificación independiente; manualmente retirar/volver a presentar el DNI y reconectar. Evidencia histórica funciona, pero no valida la GUI/servicio recién instalados. |
| P1 | USB AOA real tras reinicio | Descubrimiento físico corregido: Android ofrece abrir DNIe Evolución y Windows llega a usb_waiting_android. APK 52 y gestor Release actualizado. Falta validar autorización, TLS, lectura/firma, reconexión y parada con paquete final sin depuración/RNDIS. Ver CORRECCION_USB_AOA_RC4.md. |
| P1 | LAN TLS real | Usar IP Wi-Fi real del teléfono, ruta PC→Android y huella estable. Localizar fallo por etapa: TCP, DNIREADY1, TLS, token, PC/SC, NFC. Validar firma y reconexión sin abrir reglas globales ni desactivar cifrado. |
| P1 | Ciudadano Digital sobre candidato actual | Activar selector opcional, comprobar lector exacto y repetir reconocimiento. El hito previo es «DNIe ya tiene certificados instalados» y paso a autenticación facial. No continuar a descargar certificados/cambio de PIN como prueba automática. |

## Seguridad y distribución

- USB P0: segunda apertura atascada en `usb_switching_accessory`, parada agotada (25 s), sin tarjeta. Investigar bloqueo de llamada UsbDk y aislamiento/cancelación; la corrección de descubrimiento Android no cierra este pendiente. Registrar nueva prueba antes de declarar estable el método USB.

| Trabajo | Pendiente concreto |
| --- | --- |
| Consentimiento por operación del puente | La PC autorizada puede pedir operaciones mediante clientes PC/SC. Diseñar confirmación Android para operaciones privadas sin confundirla con selector o ACL local; mantener compatibilidad y pruebas de cancelación. No declararlo ya implementado. |
| Controlador de producción | El controlador instalado es de pruebas. Validar carga después de reiniciar y planificar firma/distribución admitida por Windows. Certificado personal DNI o confianza raíz no reemplazan los requisitos de firma del controlador. |
| Firma de editor / instalador | Paquete nativo sin Authenticode público. SHA256 verifica consistencia, no identidad del editor. Planificar instalación/desinstalación y actualización de producción. |
| Servicio y actualizaciones | Probar fallo de arranque, cierre de SCM con sesión real, recuperación ante crash, actualización interrumpida/rollback, disco D no disponible y limpieza de versiones antiguas. No afirmar cobertura por los 10 controles básicos existentes. |
| Convivencia de middleware | Minidriver DNI aislado en ensayos anteriores; preservar respaldo y estado. Investigar compatibilidad separada antes de restaurarlo, porque antes generaba PACE PC y cierres. |
| Sesiones prolongadas | Probar inactividad, pérdida NFC, desconexión de cable/red, fondo/bloqueo Android y varias presentaciones. El host persistente no significa mantener una sesión criptográfica válida sin el DNI. |

## Otras funciones y deuda restante

- QR FIDO: probar ceremonia completa navegador→teléfono→proveedor→sitio. Abrir la cámara no acredita WebAuthn ni transporte híbrido propio; caBLE/CTAP híbrido propio no está implementado. Reproducir el error GMS/ML Kit si vuelve a aparecer con la versión actual.
- Bóveda/passkeys: migración con dos DNI reales y certificados distintos, recuperación/cancelación SAF, muerte de proceso y restauraciones parciales. Las simulaciones y aperturas de catálogo no sustituyen esa prueba. Mantener separado vencimiento del DNI y vencimiento del certificado.
- Passkeys: comprobar delegación biométrica en teléfono, cancelación, cambio de inscripción y vuelta al DNI. La bóveda conserva requisito DNI y no usa biometría.
- Rendimiento Android: recorrido repetible de escritura, scroll y pestañas, con medición comparable; hay mejoras confirmadas por el usuario pero no evidencia de eliminación de todos los tirones.
- Compatibilidad Windows adicional: XML y lotes ReFirma son candidatos de ensayo; CMS/P7S y PDF tienen evidencia previa. No reenviar una firma por timeout ni hacer intentos PIN con datos simulados contra el DNI real.
- Mantenimiento: revisión de contratos/errores, estilo y legibilidad C#, matriz de pruebas, empaquetado portable de dependencias opcionales y versión de artefactos. Separación en archivos/módulos no equivale a deuda técnica cero.
- Versión 3.0: rediseño completo de navegación/presentación después de consolidar 2.0. Usar los mismos casos de uso; no mover PACE, SSC, sockets o APDU a UI.

## Ya resuelto: no reabrir sin una regresión nueva

Gestor nativo y servicio permanente; control desde cuenta normal; inicio idempotente; instancia única de ventana; limpieza al detener; identidad TLS estable; QR local en memoria; separación Debug/Release Windows. El pipe ya corrige consulta de identidad bajo SYSTEM y pérdida de respuesta antes del acuse. En rc3 se retiraron límites de inactividad/30 minutos del host autenticado y se introdujeron perfiles cifrados por transporte.

Resultados exactos y procedimiento de prueba: [estado/retomar](operations/RETOMAR_TRAS_REINICIO_RC4.md). No usar PIDs, monitores o launchers de documentos históricos como estado actual.
